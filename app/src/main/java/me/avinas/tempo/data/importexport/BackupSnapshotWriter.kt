package me.avinas.tempo.data.importexport

import androidx.room.withTransaction
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.avinas.tempo.BuildConfig
import me.avinas.tempo.data.local.AppDatabase
import me.avinas.tempo.data.profile.ProfileIdentity
import okio.buffer
import okio.sink
import java.io.File

/** Stages all database-backed JSON under one Room transaction; ZIP/image copies run later. */
internal class BackupSnapshotWriter(
    private val database: AppDatabase,
    private val codec: TempoExportJsonCodec
) {
    suspend fun write(
        target: File,
        profile: ProfileIdentity,
        prepareImages: (TempoExportData) -> TempoExportData
    ): ImportExportResult.Success = database.withTransaction {
        // ID ceilings bound paging; the transaction also freezes updates, merges and deletes.
        val maxEventId = database.listeningEventDao().getMaxEventId()
        val maxArchiveId = database.scrobbleArchiveDao().getMaxArchiveId()
        val shell = prepareImages(TempoExportData(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = AppDatabase.VERSION,
            userName = profile.userName,
            userProfileImagePath = profile.profileImagePath,
            tracks = database.trackDao().getAllSync(),
            artists = database.artistDao().getAllArtistsSync(),
            albums = database.albumDao().getAllSync(),
            trackArtists = database.trackArtistDao().getAllSync(),
            enrichedMetadata = database.enrichedMetadataDao().getAllSync(),
            userPreferences = database.userPreferencesDao().getSync(),
            artistAliases = database.artistAliasDao().getAllSync(),
            trackAliases = database.trackAliasDao().getAllSync(),
            manualContentMarks = database.manualContentMarkDao().getAllSync(),
            appPreferences = database.appPreferenceDao().getAllSync(),
            lastFmImportMetadata = database.lastFmImportMetadataDao().getAll(),
            userLevel = database.gamificationDao().getUserLevel(),
            badges = database.gamificationDao().getAllBadges(),
            userKnownArtists = database.userKnownArtistDao().getAll(),
            dailyChallenges = database.gamificationDao().getAllCompletedChallenges()
        ))
        // Validate actual schema foreign keys while still inside the same snapshot. A damaged
        // library must fail export explicitly rather than produce an apparently complete backup.
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "Library has invalid foreign-key references; backup aborted" }
        }
        val trackIds = shell.tracks.mapTo(HashSet()) { it.id }
        var lastEventId = 0L
        var lastArchiveId = 0L
        var eventsWritten = 0
        target.sink().buffer().use { sink ->
            codec.write(
                writer = JsonWriter.of(sink),
                shell = shell,
                eventPages = {
                    currentCoroutineContext().ensureActive()
                    database.listeningEventDao().getEventsPage(lastEventId, maxEventId, PAGE_SIZE)
                        .also { page ->
                            check(page.all { it.track_id in trackIds }) { "Backup contains an unmapped track" }
                            if (page.isNotEmpty()) lastEventId = page.last().id
                            eventsWritten += page.size
                        }
                },
                archivePages = {
                    currentCoroutineContext().ensureActive()
                    database.scrobbleArchiveDao().getArchivePage(lastArchiveId, maxArchiveId, PAGE_SIZE)
                        .also { page -> if (page.isNotEmpty()) lastArchiveId = page.last().id }
                }
            )
        }
        ImportExportResult.Success(
            tracksCount = shell.tracks.size,
            artistsCount = shell.artists.size,
            albumsCount = shell.albums.size,
            eventsCount = eventsWritten,
            imagesCount = shell.localImageManifest.size
        )
    }

    private companion object { const val PAGE_SIZE = 2000 }
}
