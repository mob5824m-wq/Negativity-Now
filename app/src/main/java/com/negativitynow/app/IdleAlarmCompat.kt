package com.negativitynow.app

import android.app.AlarmManager
import android.app.PendingIntent

/** Loaded only on Android 6.0+, where idle-friendly alarms are available. */
object IdleAlarmCompat {
    fun setAndAllowWhileIdle(alarmManager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}
