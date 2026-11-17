package me.avinas.tempo.data.remote.spotify

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AccountSessionGateTest {
    private class Request { var cancelled = false }

    @Test fun `late refresh and exchange responses cannot restore disconnected credentials`() {
        for (kind in listOf("refresh", "exchange")) {
            val gate = AccountSessionGate<Request> { it.cancelled = true }
            var token: String? = "old"
            var state = "connected"
            val generation = gate.read { it }
            val call = Request()
            assertTrue(gate.register(generation, call))
            val responseReady = CountDownLatch(1)
            val releaseResponse = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val response = executor.submit<Boolean> {
                    responseReady.countDown()
                    check(releaseResponse.await(5, TimeUnit.SECONDS))
                    // Simulate a transport delivering success even after cancel().
                    gate.ifCurrent(generation) { token = kind; state = "connected"; true } ?: false
                }
                assertTrue(responseReady.await(5, TimeUnit.SECONDS))
                gate.replace { token = null; state = "disconnected" }
                assertTrue(call.cancelled)
                releaseResponse.countDown()
                assertFalse(response.get(5, TimeUnit.SECONDS))
                assertNull(token)
                assertEquals("disconnected", state)
            } finally {
                releaseResponse.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test fun `late failure cannot disconnect a newer login or clear its pending state`() {
        val gate = AccountSessionGate<Request> { it.cancelled = true }
        val old = gate.read { it }
        var state = "old"
        gate.replace { state = "new login pending" }
        assertNull(gate.ifCurrent(old) { gate.replace { state = "disconnected" } })
        assertEquals("new login pending", state)
    }

    @Test fun `client change rejects a request registered after invalidation`() {
        val gate = AccountSessionGate<Request> { it.cancelled = true }
        val old = gate.read { it }
        gate.replace { }
        val request = Request()
        assertFalse(gate.register(old, request))
        assertTrue(request.cancelled)
    }

    @Test fun `disconnect after a concurrent commit still clears the committed credentials`() {
        val gate = AccountSessionGate<Request> { it.cancelled = true }
        val generation = gate.read { it }
        val committing = CountDownLatch(1)
        val finishCommit = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        var token: String? = null
        try {
            val commit = executor.submit {
                gate.ifCurrent(generation) {
                    committing.countDown()
                    check(finishCommit.await(5, TimeUnit.SECONDS))
                    token = "new token"
                }
            }
            assertTrue(committing.await(5, TimeUnit.SECONDS))
            val disconnect = executor.submit { gate.replace { token = null } }
            finishCommit.countDown()
            commit.get(5, TimeUnit.SECONDS)
            disconnect.get(5, TimeUnit.SECONDS)
            assertNull(token)
        } finally {
            finishCommit.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `cleanup failure still cancels old calls and invalidates their responses`() {
        val gate = AccountSessionGate<Request> { it.cancelled = true }
        val generation = gate.read { it }
        val call = Request()
        gate.register(generation, call)
        assertThrows(IllegalStateException::class.java) { gate.replace { error("storage failure") } }
        assertTrue(call.cancelled)
        assertNull(gate.ifCurrent(generation) { "stale" })
    }

    @Test fun `current response commits and finished requests are not cancelled later`() {
        val gate = AccountSessionGate<Request> { it.cancelled = true }
        val generation = gate.read { it }
        val call = Request()
        gate.register(generation, call)
        assertEquals("token", gate.ifCurrent(generation) { "token" })
        gate.finish(call)
        gate.replace { }
        assertFalse(call.cancelled)
    }
}
