package com.negativitynow.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A small, dependency-free store for the schedule and comment choices the user has made. */
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

    fun isPresetEnabled(index: Int): Boolean = preferences.getBoolean("$KEY_PRESET_ENABLED$index", true)

    fun setPresetEnabled(index: Int, enabled: Boolean) {
        preferences.edit().putBoolean("$KEY_PRESET_ENABLED$index", enabled).apply()
    }

    fun customComments(): List<CustomComment> = try {
        val encoded = preferences.getString(KEY_CUSTOM_COMMENTS, "[]") ?: "[]"
        val comments = JSONArray(encoded)
        buildList {
            for (index in 0 until comments.length()) {
                val comment = comments.optJSONObject(index) ?: continue
                val id = comment.optString(CUSTOM_ID)
                val text = comment.optString(CUSTOM_TEXT).trim()
                if (id.isNotBlank() && text.isNotBlank()) {
                    add(CustomComment(id, text, comment.optBoolean(CUSTOM_ENABLED, true)))
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun addCustomComment(text: String) {
        saveCustomComments(customComments() + CustomComment(UUID.randomUUID().toString(), text.trim(), true))
    }

    fun updateCustomComment(id: String, text: String) {
        saveCustomComments(customComments().map { comment ->
            if (comment.id == id) comment.copy(text = text.trim()) else comment
        })
    }

    fun setCustomCommentEnabled(id: String, enabled: Boolean) {
        saveCustomComments(customComments().map { comment ->
            if (comment.id == id) comment.copy(enabled = enabled) else comment
        })
    }

    fun deleteCustomComment(id: String) {
        saveCustomComments(customComments().filterNot { it.id == id })
    }

    fun scheduleConfig(): ScheduleConfig = ScheduleConfig(
        startMinute = startMinute,
        endMinute = endMinute,
        intervalMinutes = intervalMinutes
    )

    private fun saveCustomComments(comments: List<CustomComment>) {
        val encoded = JSONArray().apply {
            comments.forEach { comment ->
                put(JSONObject().apply {
                    put(CUSTOM_ID, comment.id)
                    put(CUSTOM_TEXT, comment.text)
                    put(CUSTOM_ENABLED, comment.enabled)
                })
            }
        }
        preferences.edit().putString(KEY_CUSTOM_COMMENTS, encoded.toString()).apply()
    }

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
        private const val KEY_PRESET_ENABLED = "preset_enabled_"
        private const val KEY_CUSTOM_COMMENTS = "custom_comments"
        private const val CUSTOM_ID = "id"
        private const val CUSTOM_TEXT = "text"
        private const val CUSTOM_ENABLED = "enabled"
    }
}

data class CustomComment(
    val id: String,
    val text: String,
    val enabled: Boolean
)
