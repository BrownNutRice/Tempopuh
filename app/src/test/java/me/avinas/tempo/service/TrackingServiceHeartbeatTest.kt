package me.avinas.tempo.service

import me.avinas.tempo.service.TrackingServiceHeartbeat.Snapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingServiceHeartbeatTest {
    private val now = 1_000_000L

    private fun snapshot(
        listenerConnected: Boolean = true,
        created: Long = now - 120_000L,
        alive: Long = now - 1_000L,
        connected: Long = now - 120_000L,
        disconnected: Long = 0L,
        rebind: Long = 0L,
    ) = Snapshot(
        listenerConnected = listenerConnected,
        lastServiceCreatedAt = created,
        lastServiceAliveAt = alive,
        lastListenerConnectedAt = connected,
        lastListenerDisconnectedAt = disconnected,
        lastNotificationCallbackAt = 0L,
        lastMediaSessionCallbackAt = 0L,
        lastRebindRequestedAt = rebind,
    )

    @Test fun healthyListenerNeedsNoRebind() {
        assertFalse(snapshot().shouldRequestRebind(now))
    }

    @Test fun aliveServiceWithDisconnectedListenerNeedsRebind() {
        assertTrue(snapshot(listenerConnected = false, disconnected = now - 90_000L)
            .shouldRequestRebind(now))
    }

    @Test fun staleConnectionFromPreviousProcessDoesNotCount() {
        assertTrue(snapshot(created = now - 90_000L, connected = now - 200_000L)
            .shouldRequestRebind(now))
    }

    @Test fun aFreshProcessGetsConnectionGracePeriod() {
        assertFalse(snapshot(listenerConnected = false, created = now - 10_000L)
            .shouldRequestRebind(now))
    }

    @Test fun staleServiceHeartbeatNeedsRebind() {
        assertTrue(snapshot(alive = now - TrackingServiceHeartbeat.HEARTBEAT_STALE_MS - 1L)
            .shouldRequestRebind(now))
    }

    @Test fun periodicProcessHeartbeatCannotHideFailedRebind() {
        assertTrue(snapshot(listenerConnected = false,
            alive = now - 1_000L, rebind = now - 11 * 60_000L)
            .shouldForceRestartAfterRebind(now))
    }

    @Test fun newListenerConnectionClearsFailedRebind() {
        assertFalse(snapshot(connected = now - 1_000L, rebind = now - 11 * 60_000L)
            .shouldForceRestartAfterRebind(now))
    }

    @Test fun recentRebindIsNotEscalatedEarly() {
        assertFalse(snapshot(listenerConnected = false, rebind = now - 2 * 60_000L)
            .shouldForceRestartAfterRebind(now))
    }
}
