package com.nullverse.nullkeyai.ime.engine

import android.view.KeyEvent

/**
 * A key press that has to leave the IME as real [KeyEvent]s: a terminal/PC
 * key (Esc, Tab, arrows, F-keys...) or any key pressed while Ctrl, Alt or
 * Meta is on. [code] is already cased by Shift.
 */
data class KeyStroke(
    val code: Int,
    val modifiers: Set<StickyModifier> = emptySet(),
    val shift: Boolean = false,
)

/** One key event to send. Pure data so the plan can be unit tested. */
data class KeyEventSpec(
    val action: Int,
    val keyCode: Int,
    val metaState: Int,
)

sealed interface KeyStrokePlan {
    /** Not a special stroke; use the normal commitText path. */
    data object PassThrough : KeyStrokePlan

    /** Swallowed on purpose (e.g. Ctrl-A in a normal text field). */
    data object Blocked : KeyStrokePlan

    /** Modifiers on a character with no key code: commit it as plain text. */
    data class Text(val text: String) : KeyStrokePlan

    data class Events(val events: List<KeyEventSpec>) : KeyStrokePlan
}

/**
 * Turns a [KeyStroke] into Android key events with the right meta state.
 *
 * The sequence is: modifier key downs, key down, key up, modifier key ups.
 * Terminals such as Termux read `KeyEvent.metaState`; some editors and
 * browsers also want the modifier key events themselves.
 */
object KeyStrokePlanner {

    const val CTRL_META = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    const val ALT_META = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
    const val META_META = KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
    const val SHIFT_META = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

    /**
     * @param allowCtrlA when false, Ctrl+A (select all) is blocked. Select-all
     *   followed by the next key would wipe a normal text field; terminals need
     *   Ctrl+A for tmux/screen/readline, so the IME passes true there.
     */
    fun plan(stroke: KeyStroke, allowCtrlA: Boolean): KeyStrokePlan {
        val special = keyCodeForSpecial(stroke.code)
        if (special == null && stroke.modifiers.isEmpty()) return KeyStrokePlan.PassThrough

        val keyCode: Int
        var shift = stroke.shift
        if (special != null) {
            keyCode = special
        } else {
            val mapped = keyCodeForCharacter(stroke.code)
                ?: return if (stroke.code > 0) {
                    KeyStrokePlan.Text(String(Character.toChars(stroke.code)))
                } else {
                    KeyStrokePlan.PassThrough
                }
            keyCode = mapped.first
            shift = mapped.second
        }

        if (!allowCtrlA &&
            keyCode == KeyEvent.KEYCODE_A &&
            StickyModifier.CTRL in stroke.modifiers &&
            StickyModifier.ALT !in stroke.modifiers &&
            StickyModifier.META !in stroke.modifiers
        ) {
            return KeyStrokePlan.Blocked
        }

        return KeyStrokePlan.Events(events(keyCode, stroke.modifiers, shift))
    }

    fun metaStateFor(modifiers: Set<StickyModifier>, shift: Boolean): Int {
        var meta = 0
        if (StickyModifier.CTRL in modifiers) meta = meta or CTRL_META
        if (StickyModifier.ALT in modifiers) meta = meta or ALT_META
        if (StickyModifier.META in modifiers) meta = meta or META_META
        if (shift) meta = meta or SHIFT_META
        return meta
    }

    private fun events(keyCode: Int, modifiers: Set<StickyModifier>, shift: Boolean): List<KeyEventSpec> {
        val held = buildList {
            if (StickyModifier.CTRL in modifiers) add(KeyEvent.KEYCODE_CTRL_LEFT to CTRL_META)
            if (StickyModifier.ALT in modifiers) add(KeyEvent.KEYCODE_ALT_LEFT to ALT_META)
            if (StickyModifier.META in modifiers) add(KeyEvent.KEYCODE_META_LEFT to META_META)
            if (shift) add(KeyEvent.KEYCODE_SHIFT_LEFT to SHIFT_META)
        }
        val out = mutableListOf<KeyEventSpec>()
        var meta = 0
        for ((code, flag) in held) {
            meta = meta or flag
            out += KeyEventSpec(KeyEvent.ACTION_DOWN, code, meta)
        }
        out += KeyEventSpec(KeyEvent.ACTION_DOWN, keyCode, meta)
        out += KeyEventSpec(KeyEvent.ACTION_UP, keyCode, meta)
        for ((code, flag) in held.asReversed()) {
            meta = meta and flag.inv()
            out += KeyEventSpec(KeyEvent.ACTION_UP, code, meta)
        }
        return out
    }

    /** Android key code for NullKey's special keys, or null. */
    fun keyCodeForSpecial(code: Int): Int? {
        KeyCodes.functionKeyNumber(code)?.let { return KeyEvent.KEYCODE_F1 + (it - 1) }
        return when (code) {
            KeyCodes.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
            KeyCodes.TAB -> KeyEvent.KEYCODE_TAB
            KeyCodes.ARROW_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyCodes.ARROW_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            KeyCodes.ARROW_UP -> KeyEvent.KEYCODE_DPAD_UP
            KeyCodes.ARROW_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            KeyCodes.HOME -> KeyEvent.KEYCODE_MOVE_HOME
            KeyCodes.END -> KeyEvent.KEYCODE_MOVE_END
            KeyCodes.PAGE_UP -> KeyEvent.KEYCODE_PAGE_UP
            KeyCodes.PAGE_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN
            KeyCodes.INSERT -> KeyEvent.KEYCODE_INSERT
            KeyCodes.FORWARD_DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
            else -> null
        }
    }

    /**
     * US-layout key code for a character pressed with Ctrl/Alt/Meta, and
     * whether Shift is needed to type it. Also covers Enter, Space and
     * Backspace so Ctrl+Backspace etc. reach the app.
     */
    fun keyCodeForCharacter(code: Int): Pair<Int, Boolean>? {
        when (code) {
            KeyCodes.DELETE -> return KeyEvent.KEYCODE_DEL to false
            KeyCodes.DONE -> return KeyEvent.KEYCODE_ENTER to false
            KeyCodes.SPACE -> return KeyEvent.KEYCODE_SPACE to false
        }
        if (code <= 0 || code > 0x7f) return null
        val ch = code.toChar()
        if (ch in 'a'..'z') return (KeyEvent.KEYCODE_A + (ch - 'a')) to false
        if (ch in 'A'..'Z') return (KeyEvent.KEYCODE_A + (ch - 'A')) to true
        if (ch in '0'..'9') return (KeyEvent.KEYCODE_0 + (ch - '0')) to false
        UNSHIFTED[ch]?.let { return it to false }
        SHIFTED[ch]?.let { return it to true }
        return null
    }

    private val UNSHIFTED = mapOf(
        '`' to KeyEvent.KEYCODE_GRAVE,
        '-' to KeyEvent.KEYCODE_MINUS,
        '=' to KeyEvent.KEYCODE_EQUALS,
        '[' to KeyEvent.KEYCODE_LEFT_BRACKET,
        ']' to KeyEvent.KEYCODE_RIGHT_BRACKET,
        '\\' to KeyEvent.KEYCODE_BACKSLASH,
        ';' to KeyEvent.KEYCODE_SEMICOLON,
        '\'' to KeyEvent.KEYCODE_APOSTROPHE,
        ',' to KeyEvent.KEYCODE_COMMA,
        '.' to KeyEvent.KEYCODE_PERIOD,
        '/' to KeyEvent.KEYCODE_SLASH,
        '\t' to KeyEvent.KEYCODE_TAB,
        '\n' to KeyEvent.KEYCODE_ENTER,
    )

    private val SHIFTED = mapOf(
        '~' to KeyEvent.KEYCODE_GRAVE,
        '!' to KeyEvent.KEYCODE_1,
        '@' to KeyEvent.KEYCODE_2,
        '#' to KeyEvent.KEYCODE_3,
        '$' to KeyEvent.KEYCODE_4,
        '%' to KeyEvent.KEYCODE_5,
        '^' to KeyEvent.KEYCODE_6,
        '&' to KeyEvent.KEYCODE_7,
        '*' to KeyEvent.KEYCODE_8,
        '(' to KeyEvent.KEYCODE_9,
        ')' to KeyEvent.KEYCODE_0,
        '_' to KeyEvent.KEYCODE_MINUS,
        '+' to KeyEvent.KEYCODE_EQUALS,
        '{' to KeyEvent.KEYCODE_LEFT_BRACKET,
        '}' to KeyEvent.KEYCODE_RIGHT_BRACKET,
        '|' to KeyEvent.KEYCODE_BACKSLASH,
        ':' to KeyEvent.KEYCODE_SEMICOLON,
        '"' to KeyEvent.KEYCODE_APOSTROPHE,
        '<' to KeyEvent.KEYCODE_COMMA,
        '>' to KeyEvent.KEYCODE_PERIOD,
        '?' to KeyEvent.KEYCODE_SLASH,
    )
}

/** Key specs for terminal/PC keys, shared by every layout that shows them. */
object TerminalKeys {
    fun spec(code: Int, widthWeight: Float = 1f): KeySpec {
        KeyCodes.functionKeyNumber(code)?.let { n ->
            return KeySpec(code = code, label = "F$n", widthWeight = widthWeight, isModifier = true)
        }
        return when (code) {
            KeyCodes.ESCAPE -> KeySpec(code, "Esc", widthWeight = widthWeight, isModifier = true)
            KeyCodes.TAB -> KeySpec(code, "Tab", widthWeight = widthWeight, isModifier = true)
            KeyCodes.CTRL -> KeySpec(code, "Ctrl", widthWeight = widthWeight, isModifier = true)
            KeyCodes.ALT -> KeySpec(code, "Alt", widthWeight = widthWeight, isModifier = true)
            KeyCodes.META -> KeySpec(code, "Meta", widthWeight = widthWeight, isModifier = true)
            KeyCodes.ARROW_LEFT -> KeySpec(code, "←", widthWeight = widthWeight, isModifier = true, isRepeatable = true)
            KeyCodes.ARROW_RIGHT -> KeySpec(code, "→", widthWeight = widthWeight, isModifier = true, isRepeatable = true)
            KeyCodes.ARROW_UP -> KeySpec(code, "↑", widthWeight = widthWeight, isModifier = true, isRepeatable = true)
            KeyCodes.ARROW_DOWN -> KeySpec(code, "↓", widthWeight = widthWeight, isModifier = true, isRepeatable = true)
            KeyCodes.HOME -> KeySpec(code, "Home", widthWeight = widthWeight, isModifier = true)
            KeyCodes.END -> KeySpec(code, "End", widthWeight = widthWeight, isModifier = true)
            KeyCodes.PAGE_UP -> KeySpec(code, "PgUp", widthWeight = widthWeight, isModifier = true, isRepeatable = true)
            KeyCodes.PAGE_DOWN -> KeySpec(code, "PgDn", widthWeight = widthWeight, isModifier = true, isRepeatable = true)
            KeyCodes.INSERT -> KeySpec(code, "Ins", widthWeight = widthWeight, isModifier = true)
            KeyCodes.FORWARD_DELETE -> KeySpec(code, "Del", widthWeight = widthWeight, isModifier = true, isRepeatable = true)
            else -> throw IllegalArgumentException("Not a terminal key: $code")
        }
    }
}

/** Builds the Android [KeyEvent] for a planned [KeyEventSpec]. */
object KeyStrokeEvents {
    fun toKeyEvent(spec: KeyEventSpec, downTime: Long, eventTime: Long = downTime): KeyEvent = KeyEvent(
        downTime,
        eventTime,
        spec.action,
        spec.keyCode,
        0,
        spec.metaState,
        android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
        0,
        KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
    )
}
