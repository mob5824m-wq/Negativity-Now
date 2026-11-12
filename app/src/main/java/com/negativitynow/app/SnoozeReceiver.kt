package com.negativitynow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives notification snooze actions and delivers the one deferred reminder. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 0)
                val message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() } ?: return
                NotificationScheduler.scheduleSnooze(context, message, minutes)
            }

            ACTION_DELIVER_SNOOZE -> deliverSnoozedReminder(context)
        }
    }

    private fun deliverSnoozedReminder(context: Context) {
        val store = PreferencesStore(context)
        val message = store.snoozedMessage
        store.clearSnooze()
        if (store.isArmed && message != null) {
            NotificationHelper.postReminder(context, message)
        }
        NotificationScheduler.scheduleNext(context)
    }

    companion object {
        const val ACTION_SNOOZE = "com.negativitynow.app.SNOOZE_REMINDER"
        const val ACTION_DELIVER_SNOOZE = "com.negativitynow.app.DELIVER_SNOOZED_REMINDER"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_MESSAGE = "snooze_message"
    }
}
