package com.negativitynow.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View

/** Small platform-only compatibility helpers for Android 4.0 through current releases. */
object AndroidCompat {
    fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    @Suppress("DEPRECATION")
    fun color(context: Context, colorRes: Int): Int = context.resources.getColor(colorRes)

    @Suppress("DEPRECATION")
    fun drawable(context: Context, drawableRes: Int): Drawable = context.resources.getDrawable(drawableRes)

    @Suppress("DEPRECATION")
    fun setBackground(view: View, drawableRes: Int) {
        view.setBackgroundDrawable(drawable(view.context, drawableRes))
    }
}
