package com.nullverse.nullkeyai.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * API 35+ draws every activity edge-to-edge; API 36 removed the opt-out.
 * Activity content pads for system bars, cutouts, and the IME so a focused
 * field stays above the keyboard. The IME root pads for bars only — applying
 * the IME inset there would shrink the keyboard by its own height.
 */
object SystemBarInsets {
    fun applyToActivity(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        applyActivityContentInsets(activity.findViewById(android.R.id.content))
    }

    fun applyActivityContentInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val padding = activityContentPadding(insets)
            target.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            if (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0) {
                target.post { bringFocusedDescendantAboveIme(target) }
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * System bars and cutouts only. Used by the IME window.
     */
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

    /**
     * Bottom inset is the keyboard when it is taller than the navigation bar,
     * so activity content ends above the IME instead of under it.
     */
    fun activityContentPadding(insets: WindowInsetsCompat): Insets {
        val bars = bars(insets)
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        return Insets.of(bars.left, bars.top, bars.right, maxOf(bars.bottom, imeBottom))
    }

    fun bringFocusedDescendantAboveIme(root: View): Boolean {
        val focused = root.findFocus() ?: return false
        val rect = Rect(0, 0, focused.width.coerceAtLeast(1), focused.height.coerceAtLeast(1))
        return focused.requestRectangleOnScreen(rect, true)
    }
}
