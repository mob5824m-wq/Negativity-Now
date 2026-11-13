package com.negativitynow.app

import java.util.Calendar
import java.util.TimeZone

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
 *
 * The implementation deliberately uses java.util.Calendar rather than java.time so the
 * scheduling rules work on Android 4.0 (API 14) without a desugaring dependency.
 */
object ScheduleEngine {
    /**
     * A selected day denotes the day on which the window begins. For an overnight
     * window, an early-morning time belongs to the previous day's selected window.
     */
    fun isInWindow(
        nowMillis: Long,
        config: ScheduleConfig,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (config.selectedDays.isEmpty()) return false
        val now = calendarAt(nowMillis, timeZone)
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return when {
            config.startMinute == config.endMinute -> isoDayOfWeek(now) in config.selectedDays
            config.startMinute < config.endMinute ->
                minute >= config.startMinute && minute < config.endMinute &&
                    isoDayOfWeek(now) in config.selectedDays
            minute >= config.startMinute -> isoDayOfWeek(now) in config.selectedDays
            minute < config.endMinute -> {
                now.add(Calendar.DAY_OF_YEAR, -1)
                isoDayOfWeek(now) in config.selectedDays
            }
            else -> false
        }
    }

    fun nextTriggerAt(
        nowMillis: Long,
        config: ScheduleConfig,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        require(config.selectedDays.isNotEmpty()) { "At least one day must be selected." }
        val now = calendarAt(nowMillis, timeZone)
        val interval = config.intervalMinutes.coerceIn(
            PreferencesStore.MIN_INTERVAL_MINUTES,
            PreferencesStore.MAX_INTERVAL_MINUTES
        )

        if (isInWindow(nowMillis, config, timeZone)) {
            val candidate = now.clone() as Calendar
            candidate.add(Calendar.MINUTE, interval)
            if (isInWindow(candidate.timeInMillis, config, timeZone)) {
                return candidate.timeInMillis
            }
        }

        return nextWindowStartAfter(now, config, timeZone).timeInMillis
    }

    private fun nextWindowStartAfter(
        reference: Calendar,
        config: ScheduleConfig,
        timeZone: TimeZone
    ): Calendar {
        // One full week is enough to find a selected day. The extra day makes the
        // search resilient around daylight-saving transitions.
        for (daysFromToday in 0..7) {
            val candidate = reference.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, daysFromToday)
            candidate.set(Calendar.HOUR_OF_DAY, config.startMinute / 60)
            candidate.set(Calendar.MINUTE, config.startMinute % 60)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)
            if (isoDayOfWeek(candidate) in config.selectedDays && candidate.after(reference)) {
                return candidate
            }
        }
        error("At least one selected day must have a future window start in $timeZone.")
    }

    private fun calendarAt(timeMillis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }

    private fun isoDayOfWeek(calendar: Calendar): Int =
        ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
}
