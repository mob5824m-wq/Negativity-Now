# Negativity Now

A small, native Android app for scheduling firm, no-excuses notification prompts. It supports Android 4.0 (API 14) and newer, and targets Android 15 (API 35).

## What it does

- Lets you select a **start** and **end** time (including overnight windows) and the days on which that window runs.
- Sends a rotating prompt every 15–180 minutes while that window is active.
- Offers **Snooze 10 min**, **Snooze 30 min**, and **Snooze 1 hr** actions on every reminder. Snoozing temporarily holds the regular sequence, then resumes it after the deferred reminder is delivered. Android 4.1+ can show these action buttons; Android 4.0 still receives the reminder but its platform does not provide notification action buttons.
- Displays the current prompt in the app as soon as you open it—no opening notification.
- Handles system-bar and display-cutout insets for modern edge-to-edge Android screens, including Android 15’s API 35 behavior.
- Includes a compact home-screen widget with the current verdict and the next reminder or snooze time.
- Reschedules after a reboot, time change, or time-zone change, including a pending snooze.
- Includes English, Spanish, and French resources, plus a direct link to Android notification-channel settings for sound, vibration, and importance.
- Lets you enable or disable individual preset comments, add/edit/delete custom comments, shuffle enabled comments without immediate repeats, and send a test notification.

Matching start and end times means **all day**. For an overnight window, select the day the window starts; for example, a Tuesday 21:00–07:00 window also runs into Wednesday morning. Alerts use Android's idle-friendly, inexact alarms; the operating system may briefly batch an alert to protect battery.

## Run it

1. Open this folder in the latest stable Android Studio.
2. Let Gradle sync and install the `app` configuration on an Android 4.0+ device or emulator.
3. On Android 13+, allow notifications when prompted. Earlier Android versions use their system-wide app-notification controls.
4. Choose your window and interval, then tap **ARM REMINDERS** if they are paused.

The project deliberately uses platform scheduling APIs plus AndroidX’s lightweight notification compatibility layer. This keeps the schedule readable, preserves Android 4.x notification support, and ensures the app can put the next one-shot notification back on the queue after every delivery.

## Quality and releases

`python3 scripts/quality_checks.py` enforces the product rule: **the app does not send a notification specifically when the user opens or foregrounds it.** It verifies that lifecycle methods do not post a reminder and that a notification-permission response can only complete an explicit test-notification request. It also checks snooze, day-scheduling, widget wiring, and English/Spanish/French string-name parity.

The GitHub **Quality checks** workflow runs this guard on pull requests and pushes. It also builds, verifies, and uploads an installable debug APK named `negativity-now-debug-apk` as a workflow artifact.

The **Release Android APK** workflow runs the quality guard again, creates the same installable debug APK, and attaches it to a GitHub release whenever a `v*` tag is pushed (or when manually run with a release tag). It uses Android’s automatically generated debug certificate, so **no Actions secrets are needed**. This APK is appropriate for direct installation and testing; a store-distributed production release would require a separately managed signing key.

## Project layout

- `MainActivity.kt` — settings UI, day selector, in-app opening verdict, comment controls, and notification-channel settings link.
- `ScheduleEngine.kt` — pure time-window calculation, including selected weekdays, overnight, and all-day windows.
- `NotificationScheduler.kt` — the pending regular or snoozed Android alarm.
- `NotificationAlarmReceiver.kt` — posts a prompt and schedules the following one.
- `SnoozeReceiver.kt` — handles notification snooze actions and the deferred delivery.
- `NegativityWidgetProvider.kt` — maintains the home-screen verdict and next-reminder widget.
- `RescheduleReceiver.kt` — restores an active schedule or snooze after reboot or clock changes.
