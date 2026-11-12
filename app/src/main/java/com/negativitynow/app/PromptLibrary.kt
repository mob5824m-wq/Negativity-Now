package com.negativitynow.app

import android.content.Context

/** Localized, user-selectable prompts for scheduled reminders and the in-app verdict. */
object PromptLibrary {
    private val PRESET_COMMENT_RESOURCES = intArrayOf(
        R.string.preset_comment_01,
        R.string.preset_comment_02,
        R.string.preset_comment_03,
        R.string.preset_comment_04,
        R.string.preset_comment_05,
        R.string.preset_comment_06,
        R.string.preset_comment_07,
        R.string.preset_comment_08,
        R.string.preset_comment_09,
        R.string.preset_comment_10
    )

    fun presets(context: Context, store: PreferencesStore): List<CommentOption> =
        PRESET_COMMENT_RESOURCES.mapIndexed { index, resourceId ->
            CommentOption(
                id = "preset_$index",
                text = context.getString(resourceId),
                enabled = store.isPresetEnabled(index),
                isCustom = false
            )
        }

    fun customComments(store: PreferencesStore): List<CommentOption> =
        store.customComments().map { comment ->
            CommentOption(id = comment.id, text = comment.text, enabled = comment.enabled, isCustom = true)
        }

    fun enabledMessages(context: Context, store: PreferencesStore): List<String> =
        (presets(context, store) + customComments(store)).filter { it.enabled }.map { it.text }

    fun messageFor(
        context: Context,
        store: PreferencesStore,
        timestampMillis: Long = System.currentTimeMillis()
    ): String? {
        val enabledMessages = enabledMessages(context, store)
        if (enabledMessages.isEmpty()) return null
        val index = ((timestampMillis / 60_000L) % enabledMessages.size).toInt()
        return enabledMessages[index]
    }
}

data class CommentOption(
    val id: String,
    val text: String,
    val enabled: Boolean,
    val isCustom: Boolean
)
