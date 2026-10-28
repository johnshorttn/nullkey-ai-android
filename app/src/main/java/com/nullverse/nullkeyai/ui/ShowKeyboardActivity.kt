package com.nullverse.nullkeyai.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ime.ShowKeyboardBridge

/**
 * Invisible trampoline behind the notification's "Show keyboard" and
 * "Switch keyboard" actions.
 *
 * A notification action that opens an activity is the only sanctioned way
 * (API 31+) to collapse the notification shade, and the input-method picker
 * may only be opened by the focused window. So this activity takes focus for
 * a moment, does the one thing it was asked, and finishes. Focus then goes
 * back to the app underneath, and the IME shows itself there with
 * requestShowSelf.
 */
class ShowKeyboardActivity : Activity() {

    private var pickerShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = if (intent?.action == ACTION_SWITCH) ShowKeyboardBridge.Mode.SWITCH else ShowKeyboardBridge.Mode.SHOW
        val outcome = ShowKeyboardBridge.decide(
            mode = mode,
            enabled = ImeSetupStatus.isEnabled(this),
            selected = ImeSetupStatus.isSelected(this),
            imeRunning = ShowKeyboardBridge.current() != null,
        )
        when (outcome) {
            ShowKeyboardBridge.Outcome.SHOW_SELF -> {
                ShowKeyboardBridge.current()?.showWhenEditorReady()
                finish()
            }
            ShowKeyboardBridge.Outcome.ENABLE_IN_SETTINGS -> {
                Toast.makeText(this, R.string.show_keyboard_enable_first, Toast.LENGTH_LONG).show()
                runCatching {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                finish()
            }
            ShowKeyboardBridge.Outcome.PICKER -> Unit // shown once this window has focus
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || isFinishing) return
        if (!pickerShown) {
            // showInputMethodPicker() is ignored unless the caller has window focus.
            pickerShown = true
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            return
        }
        // Focus is back after the picker closed.
        if (ImeSetupStatus.isSelected(this)) {
            Toast.makeText(this, R.string.show_keyboard_tap_field, Toast.LENGTH_SHORT).show()
            ShowKeyboardBridge.current()?.showWhenEditorReady()
        }
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    companion object {
        const val ACTION_SHOW = "com.nullverse.nullkeyai.action.SHOW_KEYBOARD"
        const val ACTION_SWITCH = "com.nullverse.nullkeyai.action.SWITCH_KEYBOARD"
    }
}
