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
        val escape: String = "Escape",
        val tab: String = "Tab",
        val ctrl: String = "Control",
        val alt: String = "Alt",
        val meta: String = "Meta",
        val modifierOn: String = "%1${'$'}s on",
        val modifierLocked: String = "%1${'$'}s locked",
        val arrowLeft: String = "Left arrow",
        val arrowRight: String = "Right arrow",
        val arrowUp: String = "Up arrow",
        val arrowDown: String = "Down arrow",
        val home: String = "Home",
        val end: String = "End",
        val pageUp: String = "Page up",
        val pageDown: String = "Page down",
        val insert: String = "Insert",
        val forwardDelete: String = "Forward delete",
        val functionKey: String = "F%1${'$'}d",
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
                escape = context.getString(R.string.key_a11y_escape),
                tab = context.getString(R.string.key_a11y_tab),
                ctrl = context.getString(R.string.key_a11y_ctrl),
                alt = context.getString(R.string.key_a11y_alt),
                meta = context.getString(R.string.key_a11y_meta),
                modifierOn = context.getString(R.string.key_a11y_modifier_on),
                modifierLocked = context.getString(R.string.key_a11y_modifier_locked),
                arrowLeft = context.getString(R.string.key_a11y_arrow_left),
                arrowRight = context.getString(R.string.key_a11y_arrow_right),
                arrowUp = context.getString(R.string.key_a11y_arrow_up),
                arrowDown = context.getString(R.string.key_a11y_arrow_down),
                home = context.getString(R.string.key_a11y_home),
                end = context.getString(R.string.key_a11y_end),
                pageUp = context.getString(R.string.key_a11y_page_up),
                pageDown = context.getString(R.string.key_a11y_page_down),
                insert = context.getString(R.string.key_a11y_insert),
                forwardDelete = context.getString(R.string.key_a11y_forward_delete),
                functionKey = context.getString(R.string.key_a11y_function_key),
            )
        }
    }

    fun spokenLabel(
        code: Int,
        displayLabel: String,
        shift: ShiftState,
        layer: KeyboardLayer,
        strings: Strings,
        stickyState: (StickyModifier) -> ShiftState = { ShiftState.OFF },
    ): String = when (code) {
        KeyCodes.ESCAPE -> strings.escape
        KeyCodes.TAB -> strings.tab
        KeyCodes.CTRL, KeyCodes.ALT, KeyCodes.META -> {
            val modifier = KeyCodes.stickyModifierFor(code)!!
            val name = when (modifier) {
                StickyModifier.CTRL -> strings.ctrl
                StickyModifier.ALT -> strings.alt
                StickyModifier.META -> strings.meta
            }
            when (stickyState(modifier)) {
                ShiftState.OFF -> name
                ShiftState.ON -> strings.modifierOn.format(name)
                ShiftState.LOCKED -> strings.modifierLocked.format(name)
            }
        }
        KeyCodes.ARROW_LEFT -> strings.arrowLeft
        KeyCodes.ARROW_RIGHT -> strings.arrowRight
        KeyCodes.ARROW_UP -> strings.arrowUp
        KeyCodes.ARROW_DOWN -> strings.arrowDown
        KeyCodes.HOME -> strings.home
        KeyCodes.END -> strings.end
        KeyCodes.PAGE_UP -> strings.pageUp
        KeyCodes.PAGE_DOWN -> strings.pageDown
        KeyCodes.INSERT -> strings.insert
        KeyCodes.FORWARD_DELETE -> strings.forwardDelete
        in KeyCodes.F12..KeyCodes.F1 -> strings.functionKey.format(KeyCodes.functionKeyNumber(code)!!)
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
