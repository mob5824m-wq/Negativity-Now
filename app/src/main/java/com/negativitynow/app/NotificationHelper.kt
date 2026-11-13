package com.negativitynow.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "blunt_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelCompat.createChannel(context)
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            NotificationPermissionCompat.needsPermissionForContext(context)
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
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
            AndroidCompat.pendingIntentFlags()
        )
        val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mark)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                0,
                context.getString(R.string.snooze_10),
                snoozePendingIntent(context, message, 10, notificationId)
            )
            .addAction(
                0,
                context.getString(R.string.snooze_30),
                snoozePendingIntent(context, message, 30, notificationId)
            )
            .addAction(
                0,
                context.getString(R.string.snooze_60),
                snoozePendingIntent(context, message, 60, notificationId)
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
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
            AndroidCompat.pendingIntentFlags()
        )
    }

    private const val SNOOZE_REQUEST_CODE_BASE = 4100
}
