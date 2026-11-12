package com.negativitynow.app

import android.content.Context

/** A small, dependency-free store for the schedule the user has chosen. */
class PreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var isArmed: Boolean
        get() = preferences.getBoolean(KEY_ARMED, true)
        set(value) = preferences.edit().putBoolean(KEY_ARMED, value).apply()

    var startMinute: Int
        get() = preferences.getInt(KEY_START_MINUTE, DEFAULT_START_MINUTE).coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = preferences.edit().putInt(KEY_START_MINUTE, value.coerceIn(0, MINUTES_PER_DAY - 1)).apply()

    var endMinute: Int
        get() = preferences.getInt(KEY_END_MINUTE, DEFAULT_END_MINUTE).coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = preferences.edit().putInt(KEY_END_MINUTE, value.coerceIn(0, MINUTES_PER_DAY - 1)).apply()

    var intervalMinutes: Int
        get() = preferences.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES).coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        set(value) = preferences.edit().putInt(KEY_INTERVAL_MINUTES, value.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)).apply()

    var nextTriggerAtMillis: Long
        get() = preferences.getLong(KEY_NEXT_TRIGGER, 0L)
        set(value) = preferences.edit().putLong(KEY_NEXT_TRIGGER, value).apply()

    fun scheduleConfig(): ScheduleConfig = ScheduleConfig(
        startMinute = startMinute,
        endMinute = endMinute,
        intervalMinutes = intervalMinutes
    )

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_INTERVAL_MINUTES = 180
        const val DEFAULT_START_MINUTE = 9 * 60
        const val DEFAULT_END_MINUTE = 21 * 60
        const val DEFAULT_INTERVAL_MINUTES = 60

        private const val FILE_NAME = "negativity_now_preferences"
        private const val KEY_ARMED = "armed"
        private const val KEY_START_MINUTE = "start_minute"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_NEXT_TRIGGER = "next_trigger"
    }
}
