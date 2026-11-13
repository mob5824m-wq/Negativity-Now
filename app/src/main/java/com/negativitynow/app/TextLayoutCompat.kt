package com.negativitynow.app

import android.widget.TextView

/** Loaded only on Android 4.1+, where TextView line-spacing controls are available. */
object TextLayoutCompat {
    fun setLineSpacing(textView: TextView, extra: Float) {
        textView.setLineSpacing(extra, 1f)
    }
}
