package me.avinas.tempo.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeDayTest {
    @Test fun rollsOverAtMidnightThenRecomputesAfterZoneChange() = runTest {
        val start = Instant.parse("2026-11-18T23:59:59Z")
        var zone: ZoneId = ZoneOffset.UTC
        val changes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val days = mutableListOf<ChallengeDay>()
        backgroundScope.launch {
            challengeDays(changes, { start.plusMillis(testScheduler.currentTime) }, { zone })
                .collect { days += it }
        }
        runCurrent()
        assertEquals(LocalDate.parse("2026-11-18"), days.single().date)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(LocalDate.parse("2026-11-19"), days.last().date)

        zone = ZoneId.of("America/Los_Angeles")
        changes.emit(Unit)
        runCurrent()
        assertEquals(ChallengeDay(LocalDate.parse("2026-11-18"), zone), days.last())

        advanceTimeBy(8 * 60 * 60 * 1_000L)
        runCurrent()
        assertEquals(ChallengeDay(LocalDate.parse("2026-11-19"), zone), days.last())
        assertEquals(4, days.size)
    }
}
