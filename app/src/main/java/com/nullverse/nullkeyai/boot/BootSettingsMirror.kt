package com.nullverse.nullkeyai.boot

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.nullverse.nullkeyai.ime.engine.KeyboardEnginePreferences as P

/**
 * A small copy of the keyboard's **non-sensitive** look-and-feel settings in
 * device-protected (DE) storage, so the keyboard can draw the right layout and
 * theme on the lock screen before the first unlock.
 *
 * Only keys in [ALLOWED_KEYS] are ever copied. No Vault data, keys, secrets,
 * learned words, clipboard data, incognito or developer flags go here; DE
 * storage is readable before the user proves who they are, so it must hold
 * nothing private. The mirror is written only while the user is unlocked,
 * from the credential-encrypted source of truth.
 */
object BootSettingsMirror {
    const val PREFS = "nullkey_ime_boot_mirror"

    /** Layout, theme, height, vibration/sound, and typing-feel toggles only. */
    val ALLOWED_KEYS: Set<String> = setOf(
        P.KEY_THEME,
        P.KEY_HEIGHT_SCALE,
        P.KEY_HAPTICS,
        P.KEY_SOUND,
        P.KEY_LONG_PRESS_MS,
        P.KEY_LAYOUT_MODE_PORTRAIT,
        P.KEY_LAYOUT_MODE_LANDSCAPE,
        P.KEY_TERMINAL_ROW,
        P.KEY_TERMINAL_MODE_AUTO,
        P.KEY_USE_CUSTOM_ENGINE,
        P.KEY_CHORDING,
        P.KEY_CTRL_A_IN_TEXT_FIELDS,
        P.KEY_SWIPE_TYPING,
    )

    fun deviceProtectedPrefs(context: Context): SharedPreferences =
        ContextCompat.createDeviceProtectedStorageContext(context.applicationContext)
            .let { it ?: context.applicationContext }
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Replace the mirror with the allowlisted subset of [source]. Call only
     * while unlocked (the caller already has CE prefs open).
     */
    fun sync(context: Context, source: SharedPreferences) {
        val editor = deviceProtectedPrefs(context).edit().clear()
        for ((key, value) in source.all) {
            if (key !in ALLOWED_KEYS) continue
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }
}
