package me.avinas.tempo.data.reset

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AppResetRequestTest {
    @Test fun `acceptance is a request state and repeated taps cannot issue another reset`() {
        var calls = 0
        val request = AppResetRequest { calls++; true }
        assertEquals(AppResetState.Idle, request.state.value)
        request.request()
        assertEquals(AppResetState.Requested, request.state.value)
        request.request()
        assertEquals(1, calls)
    }

    @Test fun `rejected request reports failure and permits retry`() {
        var accepted = false
        var calls = 0
        val request = AppResetRequest { calls++; accepted }
        request.request()
        assertEquals(AppResetState.Failed, request.state.value)
        accepted = true
        request.request()
        assertEquals(AppResetState.Requested, request.state.value)
        assertEquals(2, calls)
    }

    @Test fun `platform exception reports failure and does not strand the request gate`() {
        var fail = true
        val request = AppResetRequest {
            if (fail) throw SecurityException("Reset not allowed")
            true
        }
        request.request()
        assertEquals(AppResetState.Failed, request.state.value)
        fail = false
        request.request()
        assertEquals(AppResetState.Requested, request.state.value)
    }

    @Test fun `in-flight request is not reentered by a concurrent confirmation`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        var calls = 0
        val request = AppResetRequest {
            calls++
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            true
        }
        try {
            val first = executor.submit { request.request() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(AppResetState.Requesting, request.state.value)
            request.request()
            assertEquals(1, calls)
            release.countDown()
            first.get(5, TimeUnit.SECONDS)
            assertEquals(AppResetState.Requested, request.state.value)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
