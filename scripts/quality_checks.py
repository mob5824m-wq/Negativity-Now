#!/usr/bin/env python3
"""Fast repository checks for product rules that are easy to regress."""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN_ACTIVITY = ROOT / "app/src/main/java/com/negativitynow/app/MainActivity.kt"


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


def main() -> None:
    check_opening_app_never_posts_notification()
    print("PASS: opening or foregrounding the app cannot post a notification.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"QUALITY CHECK FAILED: {error}", file=sys.stderr)
        sys.exit(1)
