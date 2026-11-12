package com.negativitynow.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Owns the one pending regular alarm and the optional user-requested snooze alarm. */
object NotificationScheduler {
    const val ACTION_DELIVER_REMINDER = "com.negativitynow.app.DELIVER_REMINDER"
    private const val ALARM_REQUEST_CODE = 8824
    private const val SNOOZE_ALARM_REQUEST_CODE = 8825

    fun scheduleNext(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val store = PreferencesStore(context)
        if (store.hasPendingSnooze(nowMillis)) {
            store.nextTriggerAtMillis = store.snoozeUntilMillis
            WidgetUpdater.update(context)
            return
        }
        if (store.snoozeUntilMillis > 0L) {
            cancelSnoozeAlarm(context)
            store.clearSnooze()
        }
        if (!store.isArmed ||
            PromptLibrary.enabledMessages(context, store).isEmpty() ||
            store.selectedDays.isEmpty()
        ) {
            cancelRegularAlarm(context)
            store.nextTriggerAtMillis = 0L
            WidgetUpdater.update(context)
            return
        }

        val triggerAt = ScheduleEngine.nextTriggerAt(nowMillis, store.scheduleConfig())
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = regularAlarmPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        setInexactAlarm(alarmManager, triggerAt, pendingIntent)
        store.nextTriggerAtMillis = triggerAt
        WidgetUpdater.update(context)
    }

    /** Defers the current reminder and pauses the normal sequence until the snooze is delivered. */
    fun scheduleSnooze(
        context: Context,
        message: String,
        minutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val duration = minutes.takeIf { it in SNOOZE_MINUTES } ?: return
        val triggerAt = nowMillis + duration * 60_000L
        val store = PreferencesStore(context)
        if (!store.isArmed) return

        cancelRegularAlarm(context)
        cancelSnoozeAlarm(context)
        store.saveSnooze(message, triggerAt)
        store.nextTriggerAtMillis = triggerAt
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        setInexactAlarm(alarmManager, triggerAt, snoozeAlarmPendingIntent(context))
        WidgetUpdater.update(context)
    }

    /** Restores a deferred reminder after reboot or a clock/time-zone change. */
    fun restoreSnoozeOrScheduleNext(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val store = PreferencesStore(context)
        if (!store.isArmed) {
            cancel(context)
            return
        }
        if (store.hasPendingSnooze(nowMillis)) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            setInexactAlarm(alarmManager, store.snoozeUntilMillis, snoozeAlarmPendingIntent(context))
            store.nextTriggerAtMillis = store.snoozeUntilMillis
            WidgetUpdater.update(context)
        } else {
            if (store.snoozeUntilMillis > 0L) store.clearSnooze()
            scheduleNext(context, nowMillis)
        }
    }

    /** Cancels both kinds of delivery. Used when the user pauses reminders. */
    fun cancel(context: Context) {
        cancelRegularAlarm(context)
        cancelSnoozeAlarm(context)
        PreferencesStore(context).apply {
            clearSnooze()
            nextTriggerAtMillis = 0L
        }
        WidgetUpdater.update(context)
    }

    private fun setInexactAlarm(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent
    ) {
        // Inexact idle-friendly alarms need no special exact-alarm permission and are
        // the right trade-off for a periodic reminder app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelRegularAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(regularAlarmPendingIntent(context))
    }

    private fun cancelSnoozeAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(snoozeAlarmPendingIntent(context))
    }

    private fun regularAlarmPendingIntent(context: Context): PendingIntent {
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

    private fun snoozeAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_DELIVER_SNOOZE
        }
        return PendingIntent.getBroadcast(
            context,
            SNOOZE_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val SNOOZE_MINUTES = setOf(10, 30, 60)
}
