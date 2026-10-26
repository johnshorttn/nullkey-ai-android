package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import com.nullverse.nullkeyai.R

/**
 * Maps function-key engine labels onto string resources so Space / Enter /
 * ABC / ?123 can be localized without changing layout specs.
 */
object LocalizedKeyLabels {
    fun resolve(context: Context, spec: KeySpec, engineLabel: String): String {
        return when (spec.code) {
            KeyCodes.SPACE -> context.getString(R.string.key_space)
            KeyCodes.DONE -> context.getString(R.string.key_enter)
            KeyCodes.MODE_CHANGE ->
                if (engineLabel.equals("ABC", ignoreCase = true)) {
                    context.getString(R.string.key_letters)
                } else {
                    context.getString(R.string.key_symbols)
                }
            else -> engineLabel
        }
    }
}
