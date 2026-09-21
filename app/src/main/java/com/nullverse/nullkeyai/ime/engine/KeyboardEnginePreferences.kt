package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import android.content.res.Configuration

/**
 * IME renderer and input preferences. The custom engine is the default; the
 * legacy KeyboardView remains available as a fallback while the engine is
 * developed. Theme, height, haptics, sound, and long-press live in the same
 * local SharedPreferences file — no network, no account.
 */
object KeyboardEnginePreferences {
    const val PREFS = "nullkey_ime"
    const val KEY_USE_CUSTOM_ENGINE = "use_custom_keyboard_engine"
    const val KEY_SWIPE_TYPING = "swipe_typing_enabled"
    const val KEY_THEME = "keyboard_theme"
    const val KEY_HEIGHT_SCALE = "keyboard_height_scale"
    const val KEY_HAPTICS = "haptics_enabled"
    const val KEY_SOUND = "key_sound_enabled"
    const val KEY_LONG_PRESS_MS = "long_press_ms"

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

    fun themeId(context: Context): KeyboardThemeId =
        KeyboardThemeId.parse(prefs(context).getString(KEY_THEME, null))

    fun setThemeId(context: Context, themeId: KeyboardThemeId) {
        prefs(context).edit().putString(KEY_THEME, themeId.name).apply()
    }

    fun resolvedThemeId(context: Context): KeyboardThemeId {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return themeId(context).resolve(night)
    }

    fun heightScale(context: Context): Float =
        KeyboardInputSettings.clampHeightScale(
            prefs(context).getFloat(KEY_HEIGHT_SCALE, KeyboardInputSettings.HEIGHT_SCALE_DEFAULT),
        )

    fun setHeightScale(context: Context, scale: Float) {
        prefs(context).edit()
            .putFloat(KEY_HEIGHT_SCALE, KeyboardInputSettings.clampHeightScale(scale))
            .apply()
    }

    fun hapticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTICS, true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    fun soundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND, false)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun longPressMs(context: Context): Int =
        KeyboardInputSettings.clampLongPressMs(
            prefs(context).getInt(KEY_LONG_PRESS_MS, KeyboardInputSettings.LONG_PRESS_MS_DEFAULT),
        )

    fun setLongPressMs(context: Context, ms: Int) {
        prefs(context).edit()
            .putInt(KEY_LONG_PRESS_MS, KeyboardInputSettings.clampLongPressMs(ms))
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
