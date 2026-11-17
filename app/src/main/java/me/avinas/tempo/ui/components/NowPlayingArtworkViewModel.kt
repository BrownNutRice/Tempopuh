package me.avinas.tempo.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.avinas.tempo.data.repository.EnrichedMetadataRepository
import me.avinas.tempo.data.repository.TrackRepository
import me.avinas.tempo.service.SpotifyBroadcastPresence
import javax.inject.Inject

/** Stable identity: position/progress callbacks must not trigger a database artwork lookup. */
internal data class NowPlayingArtworkIdentity(
    val packageName: String,
    val title: String,
    val artist: String,
)

internal data class NowPlayingArtworkChoice(
    val identity: NowPlayingArtworkIdentity,
    val remoteUrl: String?,
    val localUrl: String?,
)

/** Pure source-precedence policy; no network lookup is made by the card itself. */
internal fun selectNowPlayingArtwork(
    identity: NowPlayingArtworkIdentity,
    storedArtUrl: String?,
    enrichedArtUrl: String?,
): NowPlayingArtworkChoice {
    val stored = storedArtUrl?.takeIf { it.isNotBlank() }
    val enriched = enrichedArtUrl?.takeIf { it.isNotBlank() }
    val isLocal: (String) -> Boolean = {
        it.startsWith("file://") || it.startsWith("content://")
    }
    return NowPlayingArtworkChoice(
        identity = identity,
        remoteUrl = enriched?.takeUnless(isLocal) ?: stored?.takeUnless(isLocal),
        localUrl = stored?.takeIf(isLocal),
    )
}

/**
 * Observe artwork already stored in Tempo's library, rather than fetching an
 * image or querying Room on every media-position update. Changes to enriched
 * artwork automatically update the card for the current track.
 */
@HiltViewModel
class NowPlayingArtworkViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val enrichedMetadataRepository: EnrichedMetadataRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val artwork: StateFlow<NowPlayingArtworkChoice?> =
        SpotifyBroadcastPresence.presence
            .map { current ->
                current?.let {
                    NowPlayingArtworkIdentity(it.packageName, it.title, it.artist)
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { identity ->
                if (identity == null) {
                    flowOf(null)
                } else {
                    flow {
                        // New playback can reach the UI before getOrInsertTrack()
                        // finishes its asynchronous insert. Retry only for this
                        // identity, then switch to cheap Room flows.
                        var resolvedTrackId: Long? = null
                        for (attempt in 0 until 5) {
                            val track = trackRepository.findByTitleAndArtist(identity.title, identity.artist)
                                ?: trackRepository.findByTitleAndArtistFuzzy(identity.title, identity.artist)
                            if (track != null) {
                                resolvedTrackId = track.id
                                break
                            }
                            if (attempt < 4) delay(750L)
                        }
                        val trackId = resolvedTrackId
                        if (trackId == null) {
                            emit(null)
                            return@flow
                        }
                        emitAll(
                            combine(
                                trackRepository.getById(trackId),
                                enrichedMetadataRepository.forTrack(trackId),
                            ) { currentTrack, metadata ->
                                selectNowPlayingArtwork(
                                    identity = identity,
                                    storedArtUrl = currentTrack?.albumArtUrl,
                                    enrichedArtUrl = metadata?.albumArtUrl
                                        ?: metadata?.albumArtUrlLarge
                                        ?: metadata?.albumArtUrlSmall,
                                )
                            }
                        )
                    }.catch { emit(null) }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)
}
