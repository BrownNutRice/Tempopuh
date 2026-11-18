package me.avinas.tempo.ui.profile

import me.avinas.tempo.data.local.entities.UserLevel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal data class ProfileStreakStatus(val days: Int = 0, val minutesUntilExpiry: Long? = null) {
    companion object {
        fun at(level: UserLevel, now: Instant, zone: ZoneId): ProfileStreakStatus {
            if (level.currentStreak <= 0) return ProfileStreakStatus()

            val today = now.atZone(zone).toLocalDate()
            return when (level.lastStreakDate) {
                today.toString() -> ProfileStreakStatus(days = level.currentStreak)
                today.minusDays(1).toString() -> {
                    val deadline = today.plusDays(1).atStartOfDay(zone).toInstant()
                    val seconds = Duration.between(now, deadline).seconds
                    val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
                    ProfileStreakStatus(days = level.currentStreak, minutesUntilExpiry = minutes)
                }
                else -> ProfileStreakStatus()
            }
        }
    }
}
