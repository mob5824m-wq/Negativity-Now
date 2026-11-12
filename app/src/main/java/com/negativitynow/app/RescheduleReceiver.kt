package com.negativitynow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recreates the one-shot alarm after device restart or a wall-clock/time-zone change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (PreferencesStore(context).isArmed) {
            NotificationScheduler.scheduleNext(context)
        }
    }
}
