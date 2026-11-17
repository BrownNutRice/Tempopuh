package me.avinas.tempo.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingHealthOverviewTest {
    private val now = 1_700_000_000_000L

    private fun overview(
        permission: Boolean = true,
        connected: Boolean = true,
        heartbeatAt: Long = now - 5_000L,
        notificationAt: Long = now - 65_000L,
        mediaAt: Long = now - 30_000L,
    ) = evaluateTrackingHealth(
        permissionGranted = permission,
        listenerConnected = connected,
        lastHeartbeatAtMs = heartbeatAt,
        lastNotificationCallbackAtMs = notificationAt,
        lastMediaSessionCallbackAtMs = mediaAt,
        nowMs = now,
    )

    @Test fun `permission missing takes precedence over other service states`() {
        assertEquals(TrackingListenerState.PERMISSION_REQUIRED, overview(permission = false).listenerState)
    }

    @Test fun `disconnected listener never reports healthy from heartbeat alone`() {
        assertEquals(TrackingListenerState.WAITING, overview(connected = false).listenerState)
    }

    @Test fun `stale heartbeat is not labelled as connected`() {
        assertEquals(TrackingListenerState.STALE, overview(heartbeatAt = now - 45 * 60_000L).listenerState)
    }

    @Test fun `fresh listener and latest callback are reported without guessing track saves`() {
        val data = overview()
        assertEquals(TrackingListenerState.CONNECTED, data.listenerState)
        assertEquals(30_000L, data.lastPlayerSignalAgeMs)
    }

    @Test fun `future and absent callback timestamps are not presented as valid signals`() {
        assertNull(overview(notificationAt = 0, mediaAt = now + 1000).lastPlayerSignalAgeMs)
    }
}
