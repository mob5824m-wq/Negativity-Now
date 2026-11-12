package com.negativitynow.app

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduleConfig(
    val startMinute: Int,
    val endMinute: Int,
    val intervalMinutes: Int
)

/**
 * Calculates the next one-shot alarm. Replacing an alarm after it fires keeps this
 * schedule inside the user's time window without requiring an always-running service.
 */
object ScheduleEngine {
    fun isInWindow(nowMillis: Long, config: ScheduleConfig, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return isMinuteInWindow(now.hour * 60 + now.minute, config.startMinute, config.endMinute)
    }

    fun nextTriggerAt(
        nowMillis: Long,
        config: ScheduleConfig,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val interval = config.intervalMinutes.coerceIn(
            PreferencesStore.MIN_INTERVAL_MINUTES,
            PreferencesStore.MAX_INTERVAL_MINUTES
        )

        // A matching pair is deliberately useful: it means "all day" in the UI.
        if (config.startMinute == config.endMinute) {
            return now.plusMinutes(interval.toLong()).toInstant().toEpochMilli()
        }

        if (isMinuteInWindow(now.hour * 60 + now.minute, config.startMinute, config.endMinute)) {
            val candidate = now.plusMinutes(interval.toLong())
            if (isMinuteInWindow(candidate.hour * 60 + candidate.minute, config.startMinute, config.endMinute)) {
                return candidate.toInstant().toEpochMilli()
            }
        }

        return nextWindowStartAfter(now, config.startMinute, zone).toInstant().toEpochMilli()
    }

    private fun isMinuteInWindow(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> minute >= start && minute < end
        else -> minute >= start || minute < end // Window crosses midnight.
    }

    private fun nextWindowStartAfter(
        reference: ZonedDateTime,
        startMinute: Int,
        zone: ZoneId
    ): ZonedDateTime {
        fun atStart(daysFromToday: Long): ZonedDateTime = reference.toLocalDate()
            .plusDays(daysFromToday)
            .atStartOfDay(zone)
            .plusMinutes(startMinute.toLong())

        val todayStart = atStart(0)
        return if (todayStart.isAfter(reference)) todayStart else atStart(1)
    }
}
