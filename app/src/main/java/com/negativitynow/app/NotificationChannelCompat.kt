package com.negativitynow.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Loaded only on Android 8.0+, where notification channels are available. */
object NotificationChannelCompat {
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            NotificationHelper.CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(true)
        }
        AndroidCompat.notificationManager(context).createNotificationChannel(channel)
    }
}
