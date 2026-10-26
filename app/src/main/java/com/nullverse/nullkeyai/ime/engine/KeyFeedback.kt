package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Local key-press feedback. Haptics default on; sound defaults off so typing
 * does not play system key clicks unless the user opts in. No network, no
 * logging of key codes.
 */
object KeyFeedback {
    fun shouldHaptic(enabled: Boolean): Boolean = enabled

    fun shouldPlaySound(enabled: Boolean, streamVolume: Int): Boolean =
        enabled && streamVolume > 0

    fun play(view: View) {
        val context = view.context
        if (shouldHaptic(KeyboardEnginePreferences.hapticsEnabled(context))) {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        }
        if (KeyboardEnginePreferences.soundEnabled(context)) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val volume = audio?.getStreamVolume(AudioManager.STREAM_SYSTEM) ?: 0
            if (shouldPlaySound(true, volume)) {
                audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
            }
        }
    }

    /** Soft tick for a cursor step. Silent when haptics are off, and never a key click. */
    fun cursorTick(view: View) {
        if (!shouldHaptic(KeyboardEnginePreferences.hapticsEnabled(view.context))) return
        view.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }
}
