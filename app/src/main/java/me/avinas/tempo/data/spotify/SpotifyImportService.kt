package me.avinas.tempo.data.spotify

import android.util.Log
import me.avinas.tempo.data.local.dao.EnrichedMetadataDao
import me.avinas.tempo.data.local.dao.ListeningEventDao
import me.avinas.tempo.data.local.dao.UserPreferencesDao
import me.avinas.tempo.data.local.entities.AlbumArtSource
import me.avinas.tempo.data.local.entities.EnrichedMetadata
import me.avinas.tempo.data.local.entities.EnrichmentStatus
import me.avinas.tempo.data.local.entities.ListeningEvent
import me.avinas.tempo.data.local.entities.SpotifyEnrichmentStatus
import me.avinas.tempo.data.local.entities.Track
import me.avinas.tempo.data.local.entities.UserPreferences
import me.avinas.tempo.data.remote.spotify.PlayHistoryObject
import me.avinas.tempo.data.remote.spotify.SpotifyApi
import me.avinas.tempo.data.remote.spotify.SpotifyAuthManager
import me.avinas.tempo.data.remote.spotify.SpotifyTrack
import me.avinas.tempo.data.repository.ArtistLinkingService
import me.avinas.tempo.data.repository.ListeningRepository
import me.avinas.tempo.data.repository.TrackRepository
import me.avinas.tempo.data.repository.TrackResolver
import me.avinas.tempo.data.repository.StatsRepository
import me.avinas.tempo.data.repository.RefreshCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for importing recently played tracks from Spotify Web API.
 */
@Singleton
class SpotifyImportService @Inject constructor(
    private val spotifyApi: SpotifyApi,
    private val authManager: SpotifyAuthManager,
    private val trackRepository: TrackRepository,
    private val listeningRepository: ListeningRepository,
    private val userPreferencesDao: UserPreferencesDao,
    private val artistLinkingService: ArtistLinkingService,
    private val enrichedMetadataDao: EnrichedMetadataDao,
    private val trackResolver: TrackResolver,
    private val listeningEventDao: ListeningEventDao,
    private val statsRepository: StatsRepository,
    private val refreshCoordinator: RefreshCoordinator,
) {
    companion object {
        private const val TAG = "SpotifyImportService"
        
        // Rate limit protection
        private const val CURSOR_OVERLAP_MS = 30 * 60 * 1000L
        
        // Source identifier for imported events
        const val IMPORT_SOURCE = "com.spotify.music.import"
        
        // Default assumed duration percentage for imported plays
        // We can't know actual play duration from recently-played API
    }
    
    /**
     * Result of an import operation.
     */
    data class ImportResult(
        val tracksImported: Int,
        val eventsCreated: Int,
        val duplicatesSkipped: Int,
        val errors: List<String>,
        val newCursor: String? = null // For incremental polling
    ) {
        val isSuccess: Boolean get() = errors.isEmpty()
        val totalProcessed: Int get() = tracksImported + duplicatesSkipped
        
        companion object {
            fun notConnected() = ImportResult(
                tracksImported = 0,
                eventsCreated = 0,
                duplicatesSkipped = 0,
                errors = listOf("Spotify not connected")
            )
            
            fun error(message: String) = ImportResult(
                tracksImported = 0,
                eventsCreated = 0,
                duplicatesSkipped = 0,
                errors = listOf(message)
            )
        }
    }
    
    /**
     * Import recently played tracks. With no [afterCursor] this fetches the last [limit] plays
     * (the API caps it at 50); with one, only plays newer than that Unix timestamp.
     */
    suspend fun importRecentlyPlayed(
        limit: Int = 50,
        afterCursor: Long? = null,
        onProgress: ((imported: Int, total: Int) -> Unit)? = null
    ): ImportResult {
        // A Spotify account must be connected; Spotify itself controls API allowlisting.
        if (!isAvailable()) {
            Log.w(TAG, "Cannot import: Spotify account not connected")
            return ImportResult.notConnected()
        }
        
        // Get access token
        val accessToken = authManager.getValidAccessToken()
        if (accessToken == null) {
            Log.e(TAG, "Failed to get valid access token")
            return ImportResult.error("Failed to authenticate with Spotify")
        }
        
        val authHeader = "Bearer $accessToken"
        
        return try {
            Log.i(TAG, "Starting Spotify import (limit=$limit, afterCursor=$afterCursor)")
            
            // Fetch recently played from Spotify
            val response = spotifyApi.getRecentlyPlayed(
                authorization = authHeader,
                limit = limit.coerceIn(1, 50),
                after = afterCursor?.let { (it - CURSOR_OVERLAP_MS).coerceAtLeast(0L) }
            )
            
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Spotify API error: ${response.code()} - $errorBody")
                
                return when (response.code()) {
                    401 -> ImportResult.error("Spotify session expired. Please reconnect.")
                    429 -> ImportResult.error("Rate limited by Spotify. Try again later.")
                    else -> ImportResult.error("Spotify API error: ${response.code()}")
                }
            }
            
            val recentlyPlayed = response.body()
            if (recentlyPlayed == null) {
                Log.w(TAG, "Empty response from Spotify")
                return ImportResult(0, 0, 0, emptyList())
            }
            
            val items = recentlyPlayed.items
            Log.i(TAG, "Fetched ${items.size} recently played tracks from Spotify")
            
            if (items.isEmpty()) {
                return ImportResult(0, 0, 0, emptyList(), recentlyPlayed.cursors?.after)
            }
            
            // Process each play history item
            var tracksImported = 0
            var eventsCreated = 0
            var duplicatesSkipped = 0
            val errors = mutableListOf<String>()
            
            items.forEachIndexed { index, playHistory ->
                try {
                    val result = processPlayHistoryItem(playHistory)
                    when (result) {
                        is ProcessResult.NewTrack -> {
                            tracksImported++
                            eventsCreated++
                        }
                        is ProcessResult.ExistingTrack -> {
                            eventsCreated++
                        }
                        is ProcessResult.Duplicate -> {
                            duplicatesSkipped++
                        }
                    }
                    onProgress?.invoke(index + 1, items.size)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing Spotify play", e)
                    errors.add("Failed to import a Spotify play")
                }
            }
            
            // Save cursor for future polling
            val newCursor = recentlyPlayed.cursors?.after
            if (newCursor != null && errors.isEmpty()) {
                saveCursor(newCursor)
            }
            
            // Imported plays are persisted directly via the DAO (not through the
            // local MusicTrackingManager). Drop the long-lived aggregate caches
            // once per sync, after all inserts, so Home/History/Stats show the
            // new plays immediately instead of serving stale five-minute totals.
            if (eventsCreated > 0) {
                statsRepository.invalidateCache()
                refreshCoordinator.notifyNewTrackRecorded()
            }

            Log.i(TAG, "Import complete: $tracksImported new tracks, $eventsCreated events, $duplicatesSkipped duplicates")
            
            ImportResult(
                tracksImported = tracksImported,
                eventsCreated = eventsCreated,
                duplicatesSkipped = duplicatesSkipped,
                errors = errors,
                newCursor = newCursor
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult.error("Import failed: ${e.message}")
        }
    }
    
    /**
     * Result of processing a single play history item.
     */
    private sealed class ProcessResult {
        object NewTrack : ProcessResult()
        object ExistingTrack : ProcessResult()
        object Duplicate : ProcessResult()
    }
    
    private suspend fun processPlayHistoryItem(playHistory: PlayHistoryObject): ProcessResult {
        val spotifyTrack = playHistory.track
        val playedAtMillis = playHistory.playedAtMillis
        if (playedAtMillis <= 0L) {
            Log.w(TAG, "Skipping a Spotify play with an invalid timestamp")
            return ProcessResult.Duplicate
        }

        // Resolve against local MediaSession/Last.fm/Spotify tracks as well as API
        // tracks. A Spotify ID is stronger identity than a fuzzy title match.
        val resolution = trackResolver.resolve(
            TrackResolver.Query(
                title = spotifyTrack.name,
                artist = spotifyTrack.allArtistNames,
                album = spotifyTrack.album.name,
                albumArtUrl = spotifyTrack.album.mediumImageUrl ?: spotifyTrack.album.largeImageUrl,
                duration = spotifyTrack.durationMs.takeIf { it > 0L },
                spotifyId = spotifyTrack.id,
            )
        )
        if (resolution.track.primaryArtistId == null) {
            try {
                artistLinkingService.linkArtistsForTrack(resolution.track)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to link imported Spotify artists", e)
            }
        }
        if (resolution.isNewTrack) createEnrichedMetadata(resolution.trackId, spotifyTrack)

        // /recently-played supplies an exact played_at timestamp and catalog duration
        // but NO observed listening duration or completion percentage. Do not
        // invent an 80% play or add fictional listening minutes.
        val event = ListeningEvent(
            track_id = resolution.trackId,
            timestamp = playedAtMillis,
            playDuration = 0L,
            completionPercentage = 0,
            source = IMPORT_SOURCE,
            wasSkipped = false,
            isReplay = false,
            estimatedDurationMs = spotifyTrack.durationMs.takeIf { it > 0L },
            pauseCount = 0,
            sessionId = null,
            endTimestamp = null
        )
        // Authority-ranked dedup avoids the old full-history scan and prevents
        // API sync from double-counting a play recorded live.
        val insertResult = listeningEventDao.insertAllBatchedWithDedup(listOf(event))
        if (insertResult.inserted == 0) return ProcessResult.Duplicate
        return if (resolution.isNewTrack) ProcessResult.NewTrack else ProcessResult.ExistingTrack
    }

    /**
     * Store Spotify ids, album art with its source, and artist ids. `spotifyEnrichmentStatus` is
     * ENRICHED so this data is never fetched again, while `enrichmentStatus` stays PENDING for the
     * genres and tags other providers supply.
     */
    private suspend fun createEnrichedMetadata(trackId: Long, spotifyTrack: SpotifyTrack) {
        try {
            val primaryArtistId = spotifyTrack.artists.firstOrNull()?.id
            val allArtistIds = spotifyTrack.artists.map { it.id }.joinToString(",")
            val primaryArtistName = spotifyTrack.artists.firstOrNull()?.name
            
            val metadata = EnrichedMetadata(
                trackId = trackId,
                // Spotify IDs
                spotifyId = spotifyTrack.id,
                spotifyArtistId = primaryArtistId,
                spotifyArtistIds = allArtistIds,
                spotifyVerifiedArtist = primaryArtistName,
                spotifyTrackUrl = spotifyTrack.externalUrls.spotify,
                spotifyPreviewUrl = spotifyTrack.previewUrl,
                // Album info
                albumTitle = spotifyTrack.album.name,
                releaseDate = spotifyTrack.album.releaseDate,
                releaseYear = try { spotifyTrack.album.releaseDate.take(4).toIntOrNull() } catch (e: Exception) { null },
                releaseType = spotifyTrack.album.albumType,
                // Album art with source tracking
                albumArtUrl = spotifyTrack.album.mediumImageUrl,
                albumArtUrlSmall = spotifyTrack.album.smallImageUrl,
                albumArtUrlLarge = spotifyTrack.album.largeImageUrl,
                albumArtSource = if (spotifyTrack.album.mediumImageUrl != null) AlbumArtSource.SPOTIFY else AlbumArtSource.NONE,
                // Track info
                trackDurationMs = spotifyTrack.durationMs,
                isrc = spotifyTrack.externalIds?.isrc,
                previewUrl = spotifyTrack.previewUrl,
                // Artist info (from simplified artist object - images fetched separately)
                artistName = primaryArtistName,
                // Enrichment status - mark as having Spotify data but needing additional enrichment
                enrichmentStatus = EnrichmentStatus.PENDING, // Still needs genre/tags from Last.fm etc.
                spotifyEnrichmentStatus = SpotifyEnrichmentStatus.ENRICHED,
                spotifyLastAttempt = System.currentTimeMillis(),
                // Timestamp
                cacheTimestamp = System.currentTimeMillis()
            )
            
            enrichedMetadataDao.upsertFromAutomaticEnrichment(metadata)
            Log.d(TAG, "Created enriched metadata for track $trackId (spotify: ${spotifyTrack.id})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create enriched metadata for track $trackId", e)
            // Non-fatal - track is still imported, metadata can be enriched later
        }
    }
    
    /**
     * Save cursor for future incremental polling.
     */
    private suspend fun saveCursor(cursor: String) {
        try {
            val prefs = userPreferencesDao.getSync() ?: UserPreferences()
            userPreferencesDao.upsert(
                prefs.copy(
                    spotifyImportCursor = cursor,
                    lastSpotifyImportTimestamp = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Saved import cursor: $cursor")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save import cursor", e)
        }
    }
    
    /**
     * Get saved cursor for incremental polling.
     * Returns the cursor as a Long timestamp for the Spotify API's 'after' parameter.
     */
    suspend fun getSavedCursor(): Long? {
        return try {
            val prefs = userPreferencesDao.getSync()
            // spotifyImportCursor is the actual cursor from Spotify API
            // It's stored as a String but represents a Unix timestamp
            prefs?.spotifyImportCursor?.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get saved cursor", e)
            null
        }
    }
    
    /**
     * Check if import is available (Spotify connected and has valid token).
     */
    fun isAvailable(): Boolean {
        // DISABLED: Spotify API requires allowlisted users (max 5) in development mode.
        // Use SpotifyJsonImportService for JSON data export imports instead.
        return authManager.isConnected()
    }
}
