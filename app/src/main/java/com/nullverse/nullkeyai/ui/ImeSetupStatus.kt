package com.nullverse.nullkeyai.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ime.NullKeyImeService

/** Setup-screen status for whether NullKey is enabled and selected as the IME. */
object ImeSetupStatus {
    fun isEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val component = ComponentName(context, NullKeyImeService::class.java)
        return imm.enabledInputMethodList.any {
            ComponentName(it.packageName, it.serviceName) == component
        }
    }

    fun isSelected(context: Context): Boolean {
        val selected = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        val component = ComponentName(context, NullKeyImeService::class.java)
        return selected == component.flattenToString() ||
            selected == component.flattenToShortString()
    }

    @StringRes
    fun messageRes(enabled: Boolean, selected: Boolean): Int = when {
        selected -> R.string.ime_status_selected
        enabled -> R.string.ime_status_enabled
        else -> R.string.ime_status_disabled
    }
}
