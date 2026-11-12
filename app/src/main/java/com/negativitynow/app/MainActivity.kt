package com.negativitynow.app

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: PreferencesStore
    private lateinit var statusPill: TextView
    private lateinit var nextHit: TextView
    private lateinit var armButton: Button
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var intervalSpinner: Spinner
    private lateinit var previewText: TextView
    private lateinit var presetListText: TextView
    private var bindingValues = false
    private var pendingTestNotification = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = PreferencesStore(this)
        NotificationHelper.createChannel(this)
        bindViews()
        setListeners()
        render()

        if (store.isArmed && store.nextTriggerAtMillis <= System.currentTimeMillis()) {
            NotificationScheduler.scheduleNext(this)
        }
        // Opening the app shows the verdict in-app, but scheduled alerts still need
        // the Android 13+ notification permission to work.
        if (store.isArmed && needsNotificationPermission()) {
            requestNotificationsPermission()
        }
    }

    private fun bindViews() {
        statusPill = findViewById(R.id.statusPill)
        nextHit = findViewById(R.id.nextHit)
        armButton = findViewById(R.id.armButton)
        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        intervalSpinner = findViewById(R.id.intervalSpinner)
        previewText = findViewById(R.id.previewText)
        presetListText = findViewById(R.id.presetListText)

        val intervalLabels = resources.getStringArray(R.array.interval_minutes)
        intervalSpinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_selected,
            intervalLabels
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown) }
    }

    private fun setListeners() {
        armButton.setOnClickListener {
            store.isArmed = !store.isArmed
            if (store.isArmed) {
                NotificationScheduler.scheduleNext(this)
                Toast.makeText(this, "Reminders armed.", Toast.LENGTH_SHORT).show()
            } else {
                NotificationScheduler.cancel(this)
                Toast.makeText(this, "Reminders paused.", Toast.LENGTH_SHORT).show()
            }
            render()
        }

        startTimeButton.setOnClickListener { showTimePicker(isStart = true) }
        endTimeButton.setOnClickListener { showTimePicker(isStart = false) }

        intervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bindingValues) return
                val selectedMinutes = INTERVAL_OPTIONS[position]
                if (store.intervalMinutes != selectedMinutes) {
                    store.intervalMinutes = selectedMinutes
                    if (store.isArmed) NotificationScheduler.scheduleNext(this@MainActivity)
                    render()
                }
            }
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            sendTestReminder()
        }
    }

    private fun render() {
        bindingValues = true
        startTimeButton.text = formatMinute(store.startMinute)
        endTimeButton.text = formatMinute(store.endMinute)
        intervalSpinner.setSelection(INTERVAL_OPTIONS.indexOf(store.intervalMinutes).coerceAtLeast(0), false)
        previewText.text = PromptLibrary.messageFor()
        presetListText.text = PromptLibrary.formattedPresetList()

        if (store.isArmed) {
            statusPill.text = "ARMED"
            statusPill.setTextColor(Color.parseColor("#0A0C10"))
            statusPill.setBackgroundResource(R.drawable.bg_pill_active)
            armButton.text = "PAUSE REMINDERS"
            val planned = store.nextTriggerAtMillis
            nextHit.text = if (planned > System.currentTimeMillis()) {
                "${formatNextTime(planned)} · every ${formatInterval(store.intervalMinutes)}"
            } else {
                "Calculating your next reminder…"
            }
        } else {
            statusPill.text = "PAUSED"
            statusPill.setTextColor(Color.parseColor("#F5F0E8"))
            statusPill.setBackgroundResource(R.drawable.bg_pill_paused)
            armButton.text = "ARM REMINDERS"
            nextHit.text = "Nothing is scheduled."
        }
        bindingValues = false
    }

    private fun showTimePicker(isStart: Boolean) {
        val selectedMinute = if (isStart) store.startMinute else store.endMinute
        val initialHour = selectedMinute / 60
        val initialMinute = selectedMinute % 60
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val value = hourOfDay * 60 + minute
                if (isStart) store.startMinute = value else store.endMinute = value
                if (store.isArmed) NotificationScheduler.scheduleNext(this)
                render()
            },
            initialHour,
            initialMinute,
            DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun requestNotificationsPermission() {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            if (pendingTestNotification) {
                pendingTestNotification = false
                postTestReminder()
            }
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            pendingTestNotification = false
            Toast.makeText(this, "Notifications are off. You can enable them in Android settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendTestReminder() {
        if (needsNotificationPermission()) {
            pendingTestNotification = true
            requestNotificationsPermission()
            return
        }
        if (!NotificationHelper.canPost(this)) {
            Toast.makeText(this, "Notifications are disabled in Android settings.", Toast.LENGTH_LONG).show()
            return
        }
        postTestReminder()
    }

    private fun postTestReminder() {
        NotificationHelper.postReminder(this, previewText.text.toString(), "Test reminder")
        Toast.makeText(this, "Test sent.", Toast.LENGTH_SHORT).show()
    }

    private fun formatMinute(totalMinutes: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, totalMinutes / 60)
            set(Calendar.MINUTE, totalMinutes % 60)
        }
        val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
    }

    private fun formatNextTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == next.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == next.get(Calendar.DAY_OF_YEAR)
        val timePattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        val pattern = if (sameDay) "'Today at' $timePattern" else "'Tomorrow at' $timePattern"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatInterval(minutes: Int): String = if (minutes == 60) "hour" else "$minutes min"

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
        private val INTERVAL_OPTIONS = intArrayOf(15, 30, 45, 60, 90, 120, 180)
    }
}
