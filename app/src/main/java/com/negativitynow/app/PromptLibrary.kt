package com.negativitynow.app

import android.content.Context
import kotlin.random.Random

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

    fun enabledMessages(context: Context, store: PreferencesStore): List<CommentOption> =
        (presets(context, store) + customComments(store)).filter { it.enabled }

    /**
     * Selects the next reminder without changing state. The caller records the option only
     * after Android accepts the notification, so a blocked notification does not skip a line.
     */
    fun nextMessage(context: Context, store: PreferencesStore): CommentOption? {
        val enabled = enabledMessages(context, store)
        if (enabled.isEmpty()) return null

        val lastId = store.lastDeliveredCommentId
        val eligible = if (enabled.size > 1 && lastId != null) {
            enabled.filterNot { it.id == lastId }
        } else {
            enabled
        }

        return if (store.randomizeComments) {
            eligible[Random.nextInt(eligible.size)]
        } else {
            val lastIndex = enabled.indexOfFirst { it.id == lastId }
            enabled[(lastIndex + 1) % enabled.size]
        }
    }

    fun recordDelivered(store: PreferencesStore, message: CommentOption) {
        store.lastDeliveredCommentId = message.id
    }

    /** The verdict previews the same next eligible line but never counts as delivery. */
    fun messageFor(context: Context, store: PreferencesStore): String? =
        nextMessage(context, store)?.text
}

data class CommentOption(
    val id: String,
    val text: String,
    val enabled: Boolean,
    val isCustom: Boolean
)
