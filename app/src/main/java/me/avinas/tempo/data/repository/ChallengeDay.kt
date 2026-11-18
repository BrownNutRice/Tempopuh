package me.avinas.tempo.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ChallengeDay(val date: LocalDate, val zone: ZoneId) {
    companion object {
        fun now(instant: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): ChallengeDay =
            ChallengeDay(instant.atZone(zone).toLocalDate(), zone)
    }
}

/** Each clock change recalculates the next midnight in the current zone, including DST. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun challengeDays(
    clockChanges: Flow<Unit>,
    now: () -> Instant = Instant::now,
    zone: () -> ZoneId = ZoneId::systemDefault,
): Flow<ChallengeDay> = clockChanges.transformLatest {
    while (true) {
        val instant = now()
        val day = ChallengeDay.now(instant, zone())
        emit(day)
        val midnight = day.date.plusDays(1).atStartOfDay(day.zone).toInstant()
        delay(Duration.between(instant, midnight).toMillis().coerceAtLeast(1L))
    }
}.distinctUntilChanged()
