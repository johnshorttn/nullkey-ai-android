package com.nullverse.nullkeyai.clipboard

import android.content.Context

enum class ClipSwipeAction {
    PIN, PROTECT, DELETE, TAG;

    companion object {
        fun parse(value: String?, fallback: ClipSwipeAction): ClipSwipeAction =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}

object ClipSwipePreferences {
    private const val PREFS = "nullkey_vault"
    private const val KEY_LEFT = "clip_swipe_left"
    private const val KEY_RIGHT = "clip_swipe_right"

    fun left(context: Context): ClipSwipeAction =
        ClipSwipeAction.parse(prefs(context).getString(KEY_LEFT, null), ClipSwipeAction.DELETE)

    fun right(context: Context): ClipSwipeAction =
        ClipSwipeAction.parse(prefs(context).getString(KEY_RIGHT, null), ClipSwipeAction.PIN)

    fun setLeft(context: Context, action: ClipSwipeAction) =
        prefs(context).edit().putString(KEY_LEFT, action.name).apply()

    fun setRight(context: Context, action: ClipSwipeAction) =
        prefs(context).edit().putString(KEY_RIGHT, action.name).apply()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
