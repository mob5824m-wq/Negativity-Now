#!/usr/bin/env python3
"""Fast repository checks for product rules that are easy to regress."""
from __future__ import annotations

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN_ACTIVITY = ROOT / "app/src/main/java/com/negativitynow/app/MainActivity.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
NOTIFICATION_HELPER = ROOT / "app/src/main/java/com/negativitynow/app/NotificationHelper.kt"
SCHEDULE_ENGINE = ROOT / "app/src/main/java/com/negativitynow/app/ScheduleEngine.kt"
SCHEDULER = ROOT / "app/src/main/java/com/negativitynow/app/NotificationScheduler.kt"
SNOOZE_RECEIVER = ROOT / "app/src/main/java/com/negativitynow/app/SnoozeReceiver.kt"
WIDGET_PROVIDER = ROOT / "app/src/main/java/com/negativitynow/app/NegativityWidgetProvider.kt"
MODERN_WINDOW = ROOT / "app/src/main/java/com/negativitynow/app/ModernWindowCompat.kt"
BUILD_FILE = ROOT / "app/build.gradle.kts"
LEGACY_STATUS_ICON = ROOT / "app/src/main/res/drawable/ic_stat_mark.png"
VALUES = ROOT / "app/src/main/res/values/strings.xml"
LOCALIZED_VALUES = (
    ROOT / "app/src/main/res/values-es/strings.xml",
    ROOT / "app/src/main/res/values-fr/strings.xml",
)


def method_body(source: str, method_name: str) -> str:
    """Return a Kotlin method body with a small brace-aware extractor."""
    match = re.search(rf"\b(?:override\s+)?fun\s+{re.escape(method_name)}\s*\(", source)
    if not match:
        return ""
    opening = source.find("{", match.end())
    if opening < 0:
        raise AssertionError(f"Could not find body for {method_name}().")

    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"Unclosed body for {method_name}().")


def check_opening_app_never_posts_notification() -> None:
    source = MAIN_ACTIVITY.read_text(encoding="utf-8")

    # Launch/foreground lifecycle methods must only display the in-app verdict.
    lifecycle_methods = ("onCreate", "onStart", "onResume", "onNewIntent")
    for name in lifecycle_methods:
        body = method_body(source, name)
        assert "NotificationHelper.postReminder" not in body, (
            f"{name}() must not post a notification when the user opens or foregrounds the app."
        )
        assert "postTestReminder" not in body, (
            f"{name}() must not send a test notification when the user opens or foregrounds the app."
        )

    forbidden_opening_paths = (
        "deliverOpenReminder",
        "requestPermissionThenDeliverOpenReminder",
        "appOpenMessage",
    )
    for path in forbidden_opening_paths:
        assert path not in source, f"Found obsolete app-open notification path: {path}"

    # The UI may only directly post a notification through its explicit test action.
    assert source.count("NotificationHelper.postReminder") == 1, (
        "MainActivity may only contain the explicit test-notification post."
    )
    test_body = method_body(source, "postTestReminder")
    assert "NotificationHelper.postReminder" in test_body, "Test notification is missing its explicit post action."
    send_test_body = method_body(source, "sendTestReminder")
    assert "pendingTestMessage = message" in send_test_body, (
        "A notification permission response must be traceable to an explicit test request."
    )


def check_selected_features_are_wired() -> None:
    manifest = MANIFEST.read_text(encoding="utf-8")
    helper = NOTIFICATION_HELPER.read_text(encoding="utf-8")
    scheduler = SCHEDULER.read_text(encoding="utf-8")
    snooze_receiver = SNOOZE_RECEIVER.read_text(encoding="utf-8")
    schedule_engine = SCHEDULE_ENGINE.read_text(encoding="utf-8")
    widget_provider = WIDGET_PROVIDER.read_text(encoding="utf-8")
    build_file = BUILD_FILE.read_text(encoding="utf-8")
    main_activity = MAIN_ACTIVITY.read_text(encoding="utf-8")
    modern_window = MODERN_WINDOW.read_text(encoding="utf-8")

    assert manifest.count(".SnoozeReceiver") == 1, "SnoozeReceiver must be registered in the manifest."
    assert ".NegativityWidgetProvider" in manifest, "The home-screen widget must be registered."
    assert "@xml/negativity_widget_info" in manifest, "The widget metadata must be registered."
    for minutes in (10, 30, 60):
        assert f"snooze_{minutes}" in helper, f"Missing {minutes}-minute notification snooze action."
    assert "ACTION_DELIVER_SNOOZE" in snooze_receiver, "Snooze delivery action is missing."
    assert "scheduleSnooze" in scheduler and "restoreSnoozeOrScheduleNext" in scheduler, (
        "Snoozes must be scheduled and restored."
    )
    assert "selectedDays" in schedule_engine and "Calendar.DAY_OF_YEAR, -1" in schedule_engine, (
        "Day selection and overnight day handling must stay in the schedule engine."
    )
    assert "java.util.Calendar" in schedule_engine and "import java.time" not in schedule_engine, (
        "Scheduling must avoid java.time so Android 4.0 can run without desugaring."
    )
    assert "minSdk = 14" in build_file, "The app must support Android 4.0 (API 14)."
    assert "compileSdk = 35" in build_file and "targetSdk = 35" in build_file, (
        "The app must continue targeting modern Android API 35."
    )
    assert "configureEdgeToEdge" in main_activity and "ModernWindowCompat.configure" in main_activity, (
        "The main screen must opt into modern edge-to-edge handling."
    )
    assert "WindowInsetsCompat.Type.systemBars" in modern_window, (
        "Modern Android system-bar insets must be applied to the main screen."
    )
    assert "androidx.core:core" in build_file, "Android 4.x notification compatibility support is missing."
    assert "Theme.Holo.NoActionBar" in manifest, "Android 4.0 needs an Android 4-compatible app theme."
    assert LEGACY_STATUS_ICON.is_file(), "Android 4.x needs a bitmap fallback for the notification icon."
    assert "WidgetUpdater.update" in widget_provider, "The widget provider must render its current state."


def string_names(path: Path) -> set[str]:
    return {
        element.attrib["name"]
        for element in ET.parse(path).getroot()
        if element.tag == "string" and "name" in element.attrib
    }


def check_localized_resource_parity() -> None:
    default_names = string_names(VALUES)
    for path in LOCALIZED_VALUES:
        localized_names = string_names(path)
        missing = default_names - localized_names
        extra = localized_names - default_names
        assert not missing and not extra, (
            f"{path.parent.name} strings must match English; missing={sorted(missing)}, extra={sorted(extra)}"
        )


def main() -> None:
    check_opening_app_never_posts_notification()
    check_selected_features_are_wired()
    check_localized_resource_parity()
    print("PASS: opening behavior, selected features, and localized resources are valid.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"QUALITY CHECK FAILED: {error}", file=sys.stderr)
        sys.exit(1)
