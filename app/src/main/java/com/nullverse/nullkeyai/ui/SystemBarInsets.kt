package com.nullverse.nullkeyai.ui

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * API 35+ draws every activity edge-to-edge; API 36 removed the opt-out.
 * Pad the given view (activity content or IME root) so controls stay clear of
 * system bars and display cutouts.
 */
object SystemBarInsets {
    fun applyToActivity(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        pad(activity.findViewById(android.R.id.content))
    }

    fun pad(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = bars(insets)
            target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun bars(insets: WindowInsetsCompat) =
        insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
}
