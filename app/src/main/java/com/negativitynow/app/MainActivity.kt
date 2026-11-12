package com.negativitynow.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
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
    private lateinit var daySelector: LinearLayout
    private lateinit var previewText: TextView
    private lateinit var testButton: Button
    private lateinit var randomizeCommentsSwitch: Switch
    private lateinit var presetList: LinearLayout
    private lateinit var customCommentsList: LinearLayout
    private lateinit var customEmptyText: TextView
    private var bindingValues = false
    private var pendingTestMessage: CommentOption? = null

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
        // The verdict is now displayed in the app, but scheduled alerts still need
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
        daySelector = findViewById(R.id.daySelector)
        previewText = findViewById(R.id.previewText)
        testButton = findViewById(R.id.testButton)
        randomizeCommentsSwitch = findViewById(R.id.randomizeCommentsSwitch)
        presetList = findViewById(R.id.presetList)
        customCommentsList = findViewById(R.id.customCommentsList)
        customEmptyText = findViewById(R.id.customEmptyText)

        val intervalLabels = INTERVAL_LABEL_RESOURCES.map(::getString)
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
                Toast.makeText(this, R.string.reminders_armed, Toast.LENGTH_SHORT).show()
            } else {
                NotificationScheduler.cancel(this)
                Toast.makeText(this, R.string.reminders_paused, Toast.LENGTH_SHORT).show()
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

        randomizeCommentsSwitch.setOnCheckedChangeListener { _, checked ->
            if (bindingValues) return@setOnCheckedChangeListener
            if (store.randomizeComments != checked) {
                store.randomizeComments = checked
                onCommentsChanged()
            }
        }

        testButton.setOnClickListener { sendTestReminder() }
        findViewById<Button>(R.id.addCommentButton).setOnClickListener { showCustomCommentDialog() }
        findViewById<Button>(R.id.alertSettingsButton).setOnClickListener { openAlertSettings() }
    }

    private fun render() {
        bindingValues = true
        startTimeButton.text = formatMinute(store.startMinute)
        endTimeButton.text = formatMinute(store.endMinute)
        intervalSpinner.setSelection(INTERVAL_OPTIONS.indexOf(store.intervalMinutes).coerceAtLeast(0), false)
        randomizeCommentsSwitch.isChecked = store.randomizeComments
        renderDaySelector()

        val currentMessage = PromptLibrary.messageFor(this, store)
        previewText.text = currentMessage ?: getString(R.string.no_enabled_comments)
        testButton.isEnabled = currentMessage != null
        testButton.alpha = if (currentMessage != null) 1f else 0.45f
        renderCommentLists()

        if (store.isArmed) {
            statusPill.setText(R.string.armed)
            statusPill.setTextColor(Color.parseColor("#0A0C10"))
            statusPill.setBackgroundResource(R.drawable.bg_pill_active)
            armButton.setText(R.string.pause_reminders)
            val planned = store.nextTriggerAtMillis
            nextHit.text = when {
                store.selectedDays.isEmpty() -> getString(R.string.no_days_selected)
                currentMessage == null -> getString(R.string.no_enabled_comments)
                store.hasPendingSnooze() -> getString(R.string.snoozed_until, formatNextTime(store.snoozeUntilMillis))
                planned > System.currentTimeMillis() -> getString(
                    R.string.next_hit_format,
                    formatNextTime(planned),
                    formatInterval(store.intervalMinutes)
                )
                else -> getString(R.string.calculating_next_reminder)
            }
        } else {
            statusPill.setText(R.string.paused)
            statusPill.setTextColor(Color.parseColor("#F5F0E8"))
            statusPill.setBackgroundResource(R.drawable.bg_pill_paused)
            armButton.setText(R.string.arm_reminders)
            nextHit.setText(R.string.nothing_scheduled)
        }
        bindingValues = false
        WidgetUpdater.update(this)
    }

    private fun renderDaySelector() {
        daySelector.removeAllViews()
        val selectedDays = store.selectedDays
        (1..7).toList().chunked(4).forEach { rowDays ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            rowDays.forEach { day ->
                row.addView(CheckBox(this).apply {
                    text = getString(DAY_SHORT_LABEL_RESOURCES[day - 1])
                    isAllCaps = false
                    setTextColor(getColor(R.color.paper))
                    textSize = 13f
                    contentDescription = getString(
                        R.string.day_enabled_description,
                        getString(DAY_FULL_LABEL_RESOURCES[day - 1])
                    )
                    isChecked = day in selectedDays
                    setOnCheckedChangeListener { _, checked ->
                        if (bindingValues) return@setOnCheckedChangeListener
                        store.setDayEnabled(day, checked)
                        if (store.isArmed) NotificationScheduler.scheduleNext(this@MainActivity)
                        render()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            daySelector.addView(row)
        }
    }

    private fun renderCommentLists() {
        presetList.removeAllViews()
        PromptLibrary.presets(this, store).forEachIndexed { index, comment ->
            presetList.addView(
                commentRow(
                    option = comment,
                    onEnabledChanged = { checked ->
                        store.setPresetEnabled(index, checked)
                        onCommentsChanged()
                    }
                ),
                rowLayoutParams()
            )
        }

        customCommentsList.removeAllViews()
        val customComments = store.customComments()
        customEmptyText.visibility = if (customComments.isEmpty()) View.VISIBLE else View.GONE
        customComments.forEach { comment ->
            val option = CommentOption(comment.id, comment.text, comment.enabled, isCustom = true)
            customCommentsList.addView(
                commentRow(
                    option = option,
                    onEnabledChanged = { checked ->
                        store.setCustomCommentEnabled(comment.id, checked)
                        onCommentsChanged()
                    },
                    onEdit = { showCustomCommentDialog(comment) },
                    onDelete = { showDeleteCommentDialog(comment) }
                ),
                rowLayoutParams()
            )
        }
    }

    private fun commentRow(
        option: CommentOption,
        onEnabledChanged: (Boolean) -> Unit,
        onEdit: (() -> Unit)? = null,
        onDelete: (() -> Unit)? = null
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = getDrawable(R.drawable.bg_time_value)
        setPadding(dp(12), dp(10), dp(12), dp(8))

        addView(TextView(this@MainActivity).apply {
            text = option.text
            setTextColor(getColor(R.color.paper))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)

            addView(Switch(this@MainActivity).apply {
                text = getString(R.string.enabled)
                setTextColor(getColor(R.color.muted))
                contentDescription = getString(R.string.comment_enabled_description, option.text)
                isChecked = option.enabled
                setOnCheckedChangeListener { _, checked -> onEnabledChanged(checked) }
            })

            onEdit?.let { editAction ->
                addView(smallActionButton(R.string.edit, editAction))
            }
            onDelete?.let { deleteAction ->
                addView(smallActionButton(R.string.delete, deleteAction))
            }
        })
    }

    private fun smallActionButton(labelRes: Int, onClick: () -> Unit): Button = Button(this).apply {
        setText(labelRes)
        setTextColor(getColor(R.color.paper))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        background = getDrawable(R.drawable.bg_button_secondary)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { onClick() }
    }

    private fun rowLayoutParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(8) }

    private fun onCommentsChanged() {
        if (store.isArmed) NotificationScheduler.scheduleNext(this)
        render()
    }

    private fun showCustomCommentDialog(existing: CustomComment? = null) {
        val input = EditText(this).apply {
            hint = getString(R.string.comment_input_hint)
            setText(existing?.text.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(MAX_COMMENT_LENGTH))
            minLines = 3
            setSelectAllOnFocus(false)
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_custom_comment else R.string.edit_custom_comment)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (existing == null) R.string.save else R.string.update) { _, _ ->
                val text = input.text.toString().trim()
                when {
                    text.isEmpty() -> Toast.makeText(this, R.string.comment_empty, Toast.LENGTH_SHORT).show()
                    text.length > MAX_COMMENT_LENGTH -> Toast.makeText(
                        this,
                        getString(R.string.comment_too_long, MAX_COMMENT_LENGTH),
                        Toast.LENGTH_SHORT
                    ).show()
                    existing == null -> store.addCustomComment(text)
                    else -> store.updateCustomComment(existing.id, text)
                }
                if (text.isNotEmpty() && text.length <= MAX_COMMENT_LENGTH) onCommentsChanged()
            }
            .show()
    }

    private fun showDeleteCommentDialog(comment: CustomComment) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_comment_title)
            .setMessage(R.string.delete_comment_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.deleteCustomComment(comment.id)
                onCommentsChanged()
                Toast.makeText(this, R.string.comment_deleted, Toast.LENGTH_SHORT).show()
            }
            .show()
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

    private fun openAlertSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID)
        }
        startActivity(intent)
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
            pendingTestMessage?.let { message ->
                pendingTestMessage = null
                postTestReminder(message)
            }
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            pendingTestMessage = null
            Toast.makeText(this, R.string.notifications_off, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendTestReminder() {
        val message = PromptLibrary.nextMessage(this, store)
        if (message == null) {
            Toast.makeText(this, R.string.no_enabled_comments, Toast.LENGTH_SHORT).show()
            return
        }
        if (needsNotificationPermission()) {
            pendingTestMessage = message
            requestNotificationsPermission()
            return
        }
        if (!NotificationHelper.canPost(this)) {
            Toast.makeText(this, R.string.notifications_disabled_settings, Toast.LENGTH_LONG).show()
            return
        }
        postTestReminder(message)
    }

    private fun postTestReminder(message: CommentOption) {
        if (NotificationHelper.postReminder(this, message.text, getString(R.string.test_reminder_title))) {
            PromptLibrary.recordDelivered(store, message)
            render()
            Toast.makeText(this, R.string.test_sent, Toast.LENGTH_SHORT).show()
        }
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
        val time = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(timestamp))
        return getString(if (sameDay) R.string.today_at else R.string.tomorrow_at, time)
    }

    private fun formatInterval(minutes: Int): String = if (minutes == 60) {
        getString(R.string.interval_hour)
    } else {
        getString(R.string.interval_minutes, minutes)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
        private const val MAX_COMMENT_LENGTH = 220
        private val INTERVAL_OPTIONS = intArrayOf(15, 30, 45, 60, 90, 120, 180)
        private val INTERVAL_LABEL_RESOURCES = intArrayOf(
            R.string.interval_label_15,
            R.string.interval_label_30,
            R.string.interval_label_45,
            R.string.interval_label_60,
            R.string.interval_label_90,
            R.string.interval_label_120,
            R.string.interval_label_180
        )
        private val DAY_SHORT_LABEL_RESOURCES = intArrayOf(
            R.string.day_short_monday,
            R.string.day_short_tuesday,
            R.string.day_short_wednesday,
            R.string.day_short_thursday,
            R.string.day_short_friday,
            R.string.day_short_saturday,
            R.string.day_short_sunday
        )
        private val DAY_FULL_LABEL_RESOURCES = intArrayOf(
            R.string.day_monday,
            R.string.day_tuesday,
            R.string.day_wednesday,
            R.string.day_thursday,
            R.string.day_friday,
            R.string.day_saturday,
            R.string.day_sunday
        )
    }
}
