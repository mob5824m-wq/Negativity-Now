package com.negativitynow.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object NotificationHelper {
    const val CHANNEL_ID = "blunt_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N || manager.areNotificationsEnabled()
    }

    fun postReminder(
        context: Context,
        message: String,
        title: String = context.getString(R.string.notification_title)
    ): Boolean {
        createChannel(context)
        if (!canPost(context)) return false

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mark)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(0, context.getString(R.string.snooze_10), snoozePendingIntent(context, message, 10, notificationId))
            .addAction(0, context.getString(R.string.snooze_30), snoozePendingIntent(context, message, 30, notificationId))
            .addAction(0, context.getString(R.string.snooze_60), snoozePendingIntent(context, message, 60, notificationId))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        return true
    }

    private fun snoozePendingIntent(
        context: Context,
        message: String,
        minutes: Int,
        notificationId: Int
    ): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE
            // Intent data makes actions from older visible notifications keep their own message.
            data = Uri.parse("negativity-now://snooze/$notificationId/$minutes")
            putExtra(SnoozeReceiver.EXTRA_MESSAGE, message)
            putExtra(SnoozeReceiver.EXTRA_SNOOZE_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context,
            SNOOZE_REQUEST_CODE_BASE + minutes,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val SNOOZE_REQUEST_CODE_BASE = 4100
}
