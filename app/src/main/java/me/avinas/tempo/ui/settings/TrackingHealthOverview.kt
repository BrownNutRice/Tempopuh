package me.avinas.tempo.ui.settings

import me.avinas.tempo.service.TrackingServiceHeartbeat

/** A heartbeat proves recent service activity, not that any listening event was saved. */
internal enum class TrackingListenerState {
    PERMISSION_REQUIRED, WAITING, STALE, CONNECTED
}

internal data class TrackingHealthOverview(
    val listenerState: TrackingListenerState,
    val lastPlayerSignalAgeMs: Long?,
)

/** Pure interpretation of persisted listener/callback timestamps; no track metadata is read. */
internal fun evaluateTrackingHealth(
    permissionGranted: Boolean,
    listenerConnected: Boolean,
    lastHeartbeatAtMs: Long,
    lastNotificationCallbackAtMs: Long,
    lastMediaSessionCallbackAtMs: Long,
    nowMs: Long,
): TrackingHealthOverview {
    val heartbeatAge = ageAt(nowMs, lastHeartbeatAtMs)
    val status = when {
        !permissionGranted -> TrackingListenerState.PERMISSION_REQUIRED
        !listenerConnected -> TrackingListenerState.WAITING
        heartbeatAge == null || heartbeatAge > TrackingServiceHeartbeat.HEARTBEAT_STALE_MS ->
            TrackingListenerState.STALE
        else -> TrackingListenerState.CONNECTED
    }
    val latestSignal = maxOf(lastNotificationCallbackAtMs, lastMediaSessionCallbackAtMs)
    return TrackingHealthOverview(
        listenerState = status,
        lastPlayerSignalAgeMs = ageAt(nowMs, latestSignal),
    )
}

private fun ageAt(nowMs: Long, eventAtMs: Long): Long? =
    if (eventAtMs <= 0L || eventAtMs > nowMs) null else nowMs - eventAtMs
