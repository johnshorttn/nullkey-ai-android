package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import com.nullverse.nullkeyai.R

/**
 * Spoken TalkBack labels for canvas-drawn keys. Visual labels like "⇧" and
 * "?123" are not useful to a screen reader; function keys and common
 * punctuation use localized names instead.
 */
object KeyAccessibility {
    data class Strings(
        val space: String,
        val shift: String,
        val shiftOn: String,
        val capsLock: String,
        val delete: String,
        val enter: String,
        val symbols: String,
        val letters: String,
        val comma: String,
        val period: String,
        val apostrophe: String,
        val quote: String,
        val incognito: String,
    ) {
        companion object {
            fun from(context: Context): Strings = Strings(
                space = context.getString(R.string.key_a11y_space),
                shift = context.getString(R.string.key_a11y_shift),
                shiftOn = context.getString(R.string.key_a11y_shift_on),
                capsLock = context.getString(R.string.key_a11y_caps_lock),
                delete = context.getString(R.string.key_a11y_delete),
                enter = context.getString(R.string.key_a11y_enter),
                symbols = context.getString(R.string.key_a11y_symbols),
                letters = context.getString(R.string.key_a11y_letters),
                comma = context.getString(R.string.key_a11y_comma),
                period = context.getString(R.string.key_a11y_period),
                apostrophe = context.getString(R.string.key_a11y_apostrophe),
                quote = context.getString(R.string.key_a11y_quote),
                incognito = context.getString(R.string.key_a11y_incognito),
            )
        }
    }

    fun spokenLabel(
        code: Int,
        displayLabel: String,
        shift: ShiftState,
        layer: KeyboardLayer,
        strings: Strings,
    ): String = when (code) {
        KeyCodes.SPACE -> strings.space
        KeyCodes.SHIFT -> when (shift) {
            ShiftState.LOCKED -> strings.capsLock
            ShiftState.ON -> strings.shiftOn
            ShiftState.OFF -> strings.shift
        }
        KeyCodes.DELETE -> strings.delete
        KeyCodes.DONE -> strings.enter
        KeyCodes.MODE_CHANGE -> if (layer == KeyboardLayer.SYMBOLS) strings.letters else strings.symbols
        ','.code -> strings.comma
        '.'.code -> strings.period
        '\''.code -> strings.apostrophe
        '"'.code -> strings.quote
        else -> displayLabel.ifBlank { code.toChar().toString() }
    }
}
