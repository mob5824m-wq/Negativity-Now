package com.negativitynow.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

/** Loaded only on Android 13+, where the notification runtime-permission APIs exist. */
object NotificationPermissionCompat {
    fun needsPermission(activity: Activity): Boolean = needsPermissionForContext(activity)

    fun needsPermissionForContext(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun request(activity: Activity, requestCode: Int) {
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
    }
}
