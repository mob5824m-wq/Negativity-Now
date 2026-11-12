package com.negativitynow.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Owns the single pending alarm used to deliver the next scheduled reminder. */
object NotificationScheduler {
    const val ACTION_DELIVER_REMINDER = "com.negativitynow.app.DELIVER_REMINDER"
    private const val ALARM_REQUEST_CODE = 8824

    fun scheduleNext(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val store = PreferencesStore(context)
        if (!store.isArmed) {
            cancel(context)
            return
        }

        val triggerAt = ScheduleEngine.nextTriggerAt(nowMillis, store.scheduleConfig())
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = alarmPendingIntent(context)
        alarmManager.cancel(pendingIntent)

        // Inexact idle-friendly alarms need no special exact-alarm permission and are
        // the right trade-off for a periodic reminder app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        store.nextTriggerAtMillis = triggerAt
    }

    fun cancel(context: Context) {
        val store = PreferencesStore(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPendingIntent(context))
        store.nextTriggerAtMillis = 0L
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_DELIVER_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
