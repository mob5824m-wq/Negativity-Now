package com.negativitynow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Sends one reminder, then queues the next one. */
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_DELIVER_REMINDER) return

        val store = PreferencesStore(context)
        if (!store.isArmed) return

        val now = System.currentTimeMillis()
        if (ScheduleEngine.isInWindow(now, store.scheduleConfig())) {
            PromptLibrary.messageFor(context, store, now)?.let { message ->
                NotificationHelper.postReminder(context, message)
            }
        }

        // If Android delayed the alarm past the end of a window, nothing is posted;
        // a new, valid delivery is still queued for the next window.
        NotificationScheduler.scheduleNext(context, now)
    }
}
