package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.nullverse.nullkeyai.boot.BootSettingsMirror
import com.nullverse.nullkeyai.boot.DirectBootGate

/**
 * IME renderer, input, and privacy/developer preferences. The custom engine is
 * the default; the legacy KeyboardView remains available as a fallback while
 * the engine is developed. Theme, height, haptics, sound, long-press, incognito,
 * and developer options live in the same local SharedPreferences file — no
 * network, no account.
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
    const val KEY_INCOGNITO = "incognito_enabled"
    const val KEY_DEVELOPER_OPTIONS = "developer_options_enabled"
    const val KEY_CTRL_A_IN_TEXT_FIELDS = "ctrl_a_in_text_fields"
    const val KEY_LAYOUT_MODE_PORTRAIT = "layout_mode_portrait"
    const val KEY_LAYOUT_MODE_LANDSCAPE = "layout_mode_landscape"
    const val KEY_TERMINAL_ROW = "terminal_row_enabled"
    const val KEY_TERMINAL_MODE_AUTO = "terminal_mode_auto"
    const val KEY_CHORDING = "modifier_chording_enabled"

    /** Hold Shift/Ctrl/Alt/Meta with one finger while tapping keys with another. On by default. */
    fun chordingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHORDING, true)

    fun setChordingEnabled(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_CHORDING, enabled) }
    }

    /** Auto terminal mode in Termux/JuiceSSH/ConnectBot and raw fields. On by default. */
    fun terminalModeAuto(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TERMINAL_MODE_AUTO, true)

    fun setTerminalModeAuto(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_TERMINAL_MODE_AUTO, enabled) }
    }

    fun layoutMode(context: Context, orientation: LayoutOrientation): KeyboardLayoutMode {
        val key = layoutModeKey(orientation)
        val stored = prefs(context).getString(key, null)
        return KeyboardLayoutMode.entries.firstOrNull { it.name == stored }
            ?: KeyboardLayoutConfig.defaultModeFor(orientation)
    }

    fun setLayoutMode(context: Context, orientation: LayoutOrientation, mode: KeyboardLayoutMode) {
        edit(context) { it.putString(layoutModeKey(orientation), mode.name) }
    }

    fun terminalRowEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TERMINAL_ROW, false)

    fun setTerminalRowEnabled(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_TERMINAL_ROW, enabled) }
    }

    fun layoutConfig(context: Context): KeyboardLayoutConfig = KeyboardLayoutConfig(
        portrait = layoutMode(context, LayoutOrientation.PORTRAIT),
        landscape = layoutMode(context, LayoutOrientation.LANDSCAPE),
        terminalRow = terminalRowEnabled(context),
    )

    private fun layoutModeKey(orientation: LayoutOrientation): String = when (orientation) {
        LayoutOrientation.PORTRAIT -> KEY_LAYOUT_MODE_PORTRAIT
        LayoutOrientation.LANDSCAPE -> KEY_LAYOUT_MODE_LANDSCAPE
    }

    fun useCustomEngine(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_CUSTOM_ENGINE, true)

    fun setUseCustomEngine(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_USE_CUSTOM_ENGINE, enabled) }
    }

    fun swipeTypingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SWIPE_TYPING, true)

    fun setSwipeTypingEnabled(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_SWIPE_TYPING, enabled) }
    }

    fun themeId(context: Context): KeyboardThemeId =
        KeyboardThemeId.parse(prefs(context).getString(KEY_THEME, null))

    fun setThemeId(context: Context, themeId: KeyboardThemeId) {
        edit(context) { it.putString(KEY_THEME, themeId.name) }
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
        edit(context) { it
            .putFloat(KEY_HEIGHT_SCALE, KeyboardInputSettings.clampHeightScale(scale)) }
    }

    fun hapticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTICS, true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_HAPTICS, enabled) }
    }

    fun soundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND, false)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_SOUND, enabled) }
    }

    fun longPressMs(context: Context): Int =
        KeyboardInputSettings.clampLongPressMs(
            prefs(context).getInt(KEY_LONG_PRESS_MS, KeyboardInputSettings.LONG_PRESS_MS_DEFAULT),
        )

    fun setLongPressMs(context: Context, ms: Int) {
        edit(context) { it
            .putInt(KEY_LONG_PRESS_MS, KeyboardInputSettings.clampLongPressMs(ms)) }
    }

    /**
     * Ctrl+A in a normal text field selects everything, and the next key then
     * replaces it. Off by default; terminals always get Ctrl+A.
     */
    fun ctrlAInTextFields(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CTRL_A_IN_TEXT_FIELDS, false)

    fun setCtrlAInTextFields(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_CTRL_A_IN_TEXT_FIELDS, enabled) }
    }

    /** Before the first unlock nothing is learned, so report incognito as on. */
    fun incognitoEnabled(context: Context): Boolean =
        !DirectBootGate.isUserUnlocked(context) ||
            credentialPrefs(context).getBoolean(KEY_INCOGNITO, false)

    fun setIncognitoEnabled(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_INCOGNITO, enabled) }
    }

    /** Learning is on unless the user opts into incognito / no-learn. */
    fun shouldLearn(context: Context): Boolean = !incognitoEnabled(context)

    fun developerOptionsEnabled(context: Context): Boolean =
        DirectBootGate.isUserUnlocked(context) &&
            credentialPrefs(context).getBoolean(KEY_DEVELOPER_OPTIONS, false)

    fun setDeveloperOptionsEnabled(context: Context, enabled: Boolean) {
        edit(context) { it.putBoolean(KEY_DEVELOPER_OPTIONS, enabled) }
    }

    /**
     * Copy the non-sensitive subset into device-protected storage so the
     * keyboard looks right on the lock screen after a reboot. No-op while locked.
     */
    fun syncBootMirror(context: Context) {
        if (!DirectBootGate.isUserUnlocked(context)) return
        BootSettingsMirror.sync(context, credentialPrefs(context))
    }

    /**
     * Unlocked: the real settings in credential-encrypted storage. Before the
     * first unlock (direct boot): the read-only [BootSettingsMirror].
     */
    private fun prefs(context: Context): SharedPreferences =
        if (DirectBootGate.isUserUnlocked(context)) credentialPrefs(context)
        else BootSettingsMirror.deviceProtectedPrefs(context)

    private fun credentialPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Settings never change before the first unlock; CE storage is untouched then. */
    private inline fun edit(context: Context, block: (SharedPreferences.Editor) -> Unit) {
        if (!DirectBootGate.isUserUnlocked(context)) return
        val prefs = credentialPrefs(context)
        val editor = prefs.edit()
        block(editor)
        editor.apply()
        BootSettingsMirror.sync(context, prefs)
    }
}
