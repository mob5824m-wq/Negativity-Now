# Negativity Now

A small, native Android app for scheduling firm, no-excuses notification prompts.

## What it does

- Lets you select a **start** and **end** time (including overnight windows).
- Sends a rotating prompt every 15–180 minutes while that window is active.
- Displays the current prompt in the app as soon as you open it—no opening notification.
- Reschedules after a reboot, time change, or time-zone change.
- Includes English, Spanish, and French resources, plus a direct link to Android notification-channel settings for sound, vibration, and importance.
- Lets you enable or disable individual preset comments, add/edit/delete custom comments, and send a test notification.

Matching start and end times means **all day**. Alerts use Android's idle-friendly, inexact alarms; the operating system may briefly batch an alert to protect battery.

## Run it

1. Open this folder in the latest stable Android Studio.
2. Let Gradle sync and install the `app` configuration on an Android 8.0+ device or emulator.
3. On Android 13+, allow notifications when prompted.
4. Choose your window and interval, then tap **ARM REMINDERS** if they are paused.

The project deliberately uses platform Android APIs rather than a notification/work scheduling library. This keeps the schedule readable and ensures the app can put the next one-shot notification back on the queue after every delivery.

## Quality and releases

`python3 scripts/quality_checks.py` enforces the product rule: **the app does not send a notification specifically when the user opens or foregrounds it.** It verifies that lifecycle methods do not post a reminder and that a notification-permission response can only complete an explicit test-notification request.

The GitHub **Quality checks** workflow runs this guard on pull requests and pushes. It also builds, verifies, and uploads an installable debug APK named `negativity-now-debug-apk` as a workflow artifact.

The **Release Android APK** workflow runs the quality guard again, creates the same installable debug APK, and attaches it to a GitHub release whenever a `v*` tag is pushed (or when manually run with a release tag). It uses Android’s automatically generated debug certificate, so **no Actions secrets are needed**. This APK is appropriate for direct installation and testing; a store-distributed production release would require a separately managed signing key.

## Project layout

- `MainActivity.kt` — settings UI, in-app opening verdict, comment controls, and notification-channel settings link.
- `ScheduleEngine.kt` — pure time-window calculation, including overnight and all-day windows.
- `NotificationScheduler.kt` — the pending Android alarm.
- `NotificationAlarmReceiver.kt` — posts a prompt and schedules the following one.
- `RescheduleReceiver.kt` — restores an active schedule after reboot or clock changes.
