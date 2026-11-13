package com.negativitynow.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Compact home-screen view of the next eligible verdict and reminder. */
class NegativityWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.update(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.update(context)
    }
}

object WidgetUpdater {
    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, NegativityWidgetProvider::class.java)
        update(context, manager, manager.getAppWidgetIds(component))
    }

    fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        manager.updateAppWidget(widgetIds, buildViews(context))
    }

    private fun buildViews(context: Context): RemoteViews {
        val store = PreferencesStore(context)
        val now = System.currentTimeMillis()
        val message = PromptLibrary.messageFor(context, store)
        val subtitle = when {
            store.hasPendingSnooze(now) -> context.getString(
                R.string.widget_snoozed_until,
                formatNextTime(context, store.snoozeUntilMillis)
            )
            !store.isArmed -> context.getString(R.string.widget_paused)
            store.selectedDays.isEmpty() -> context.getString(R.string.widget_select_day)
            message == null -> context.getString(R.string.no_enabled_comments)
            store.nextTriggerAtMillis > now -> context.getString(
                R.string.widget_next_reminder,
                formatNextTime(context, store.nextTriggerAtMillis)
            )
            else -> context.getString(R.string.calculating_next_reminder)
        }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            801,
            openAppIntent,
            AndroidCompat.pendingIntentFlags()
        )

        return RemoteViews(context.packageName, R.layout.widget_negativity_now).apply {
            setTextViewText(R.id.widgetMessage, message ?: context.getString(R.string.no_enabled_comments))
            setTextViewText(R.id.widgetSubtitle, subtitle)
            setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)
        }
    }

    private fun formatNextTime(context: Context, timestamp: Long): String {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == next.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == next.get(Calendar.DAY_OF_YEAR)
        val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        val time = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(timestamp))
        return context.getString(if (sameDay) R.string.today_at else R.string.tomorrow_at, time)
    }
}
