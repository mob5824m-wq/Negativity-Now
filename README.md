# Negativity Now

A small, native Android app for scheduling firm, no-excuses notification prompts.

## What it does

- Lets you select a **start** and **end** time (including overnight windows).
- Sends a rotating prompt every 15–180 minutes while that window is active.
- Displays the current prompt in the app as soon as you open it—no opening notification.
- Reschedules after a reboot, time change, or time-zone change.
- Includes a visible preset list of comments, a one-tap pause control, and a test-notification button.

Matching start and end times means **all day**. Alerts use Android's idle-friendly, inexact alarms; the operating system may briefly batch an alert to protect battery.

## Run it

1. Open this folder in the latest stable Android Studio.
2. Let Gradle sync and install the `app` configuration on an Android 8.0+ device or emulator.
3. On Android 13+, allow notifications when prompted.
4. Choose your window and interval, then tap **ARM REMINDERS** if they are paused.

The project deliberately uses platform Android APIs rather than a notification/work scheduling library. This keeps the schedule readable and ensures the app can put the next one-shot notification back on the queue after every delivery.

## Project layout

- `MainActivity.kt` — settings UI, notification permission flow, and in-app opening verdict.
- `ScheduleEngine.kt` — pure time-window calculation, including overnight and all-day windows.
- `NotificationScheduler.kt` — the pending Android alarm.
- `NotificationAlarmReceiver.kt` — posts a prompt and schedules the following one.
- `RescheduleReceiver.kt` — restores an active schedule after reboot or clock changes.
