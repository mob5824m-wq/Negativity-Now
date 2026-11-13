package com.negativitynow.app

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Loaded only on Android 5.0+, where transparent system bars are available. */
object ModernWindowCompat {
    fun configure(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT

        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                left + systemBars.left,
                top + systemBars.top,
                right + systemBars.right,
                bottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
