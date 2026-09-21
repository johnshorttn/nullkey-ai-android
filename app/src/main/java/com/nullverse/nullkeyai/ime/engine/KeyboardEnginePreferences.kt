package com.nullverse.nullkeyai.ime.engine

import android.content.Context

/**
 * IME renderer preference. The custom engine is the default; the legacy
 * KeyboardView remains available as a fallback while the engine is developed.
 */
object KeyboardEnginePreferences {
    const val PREFS = "nullkey_ime"
    const val KEY_USE_CUSTOM_ENGINE = "use_custom_keyboard_engine"
    const val KEY_SWIPE_TYPING = "swipe_typing_enabled"

    fun useCustomEngine(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_CUSTOM_ENGINE, true)

    fun setUseCustomEngine(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_CUSTOM_ENGINE, enabled).apply()
    }

    fun swipeTypingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SWIPE_TYPING, true)

    fun setSwipeTypingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SWIPE_TYPING, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
