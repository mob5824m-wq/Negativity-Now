package com.negativitynow.app

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduleConfig(
    val startMinute: Int,
    val endMinute: Int,
    val intervalMinutes: Int,
    /** ISO weekday values: Monday is 1 and Sunday is 7. */
    val selectedDays: Set<Int> = PreferencesStore.ALL_DAYS
)

/**
 * Calculates the next one-shot alarm. Replacing an alarm after it fires keeps this
 * schedule inside the user's time window without requiring an always-running service.
 */
object ScheduleEngine {
    /**
     * A selected day denotes the day on which the window begins. For an overnight
     * window, an early-morning time belongs to the previous day's selected window.
     */
    fun isInWindow(nowMillis: Long, config: ScheduleConfig, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (config.selectedDays.isEmpty()) return false
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val minute = now.hour * 60 + now.minute
        return when {
            config.startMinute == config.endMinute -> now.dayOfWeek.value in config.selectedDays
            config.startMinute < config.endMinute ->
                minute >= config.startMinute && minute < config.endMinute &&
                    now.dayOfWeek.value in config.selectedDays
            minute >= config.startMinute -> now.dayOfWeek.value in config.selectedDays
            minute < config.endMinute -> now.minusDays(1).dayOfWeek.value in config.selectedDays
            else -> false
        }
    }

    fun nextTriggerAt(
        nowMillis: Long,
        config: ScheduleConfig,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        require(config.selectedDays.isNotEmpty()) { "At least one day must be selected." }
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val interval = config.intervalMinutes.coerceIn(
            PreferencesStore.MIN_INTERVAL_MINUTES,
            PreferencesStore.MAX_INTERVAL_MINUTES
        )

        if (isInWindow(nowMillis, config, zone)) {
            val candidate = now.plusMinutes(interval.toLong())
            if (isInWindow(candidate.toInstant().toEpochMilli(), config, zone)) {
                return candidate.toInstant().toEpochMilli()
            }
        }

        return nextWindowStartAfter(now, config, zone).toInstant().toEpochMilli()
    }

    private fun nextWindowStartAfter(
        reference: ZonedDateTime,
        config: ScheduleConfig,
        zone: ZoneId
    ): ZonedDateTime {
        // One full week is enough to find a selected day. The extra day makes the
        // search resilient around daylight-saving transitions.
        for (daysFromToday in 0L..7L) {
            val date = reference.toLocalDate().plusDays(daysFromToday)
            if (date.dayOfWeek.value !in config.selectedDays) continue
            val candidate = date.atStartOfDay(zone).plusMinutes(config.startMinute.toLong())
            if (candidate.isAfter(reference)) return candidate
        }
        error("At least one selected day must have a future window start.")
    }
}
