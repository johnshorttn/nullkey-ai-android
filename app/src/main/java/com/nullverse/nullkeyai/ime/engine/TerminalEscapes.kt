package com.nullverse.nullkeyai.ime.engine

/**
 * Escape-sequence fallback: turns a terminal key or modifier chord into the
 * bytes an xterm-compatible terminal expects, so it can be committed as text.
 *
 * Some apps drop or rewrite Android key events (web terminals in Chrome such
 * as xterm.js, some SSH clients). Text always arrives, so when the user turns
 * this on, NullKey sends `ESC [ A` for Up, `0x03` for Ctrl+C and so on.
 *
 * Sequences follow the xterm defaults (normal cursor-key mode):
 * - Arrows `ESC [ A..D`, Home `ESC [ H`, End `ESC [ F`; with modifiers
 *   `ESC [ 1 ; m X`.
 * - Ins/Del/PgUp/PgDn `ESC [ n ~`; with modifiers `ESC [ n ; m ~`.
 * - F1..F4 `ESC O P..S`, F5..F12 `ESC [ 15/17/18/19/20/21/23/24 ~`.
 * - m = 1 + Shift(1) + Alt(2) + Ctrl(4) + Meta(8).
 * - Ctrl+letter and the usual punctuation map to C0 control bytes; Alt (and
 *   Meta, which terminals treat the same way) prefixes ESC.
 *
 * Written from the xterm control-sequence documentation; the idea of an
 * escape-sequence fallback comes from Hacker's Keyboard (Apache 2.0), but no
 * code was copied.
 */
object TerminalEscapes {

    const val ESC = "\u001b"

    /**
     * The text to commit for [stroke], or null when there is no sensible
     * byte sequence (e.g. Meta+letter with nothing else, or a non-ASCII
     * character with Ctrl); the caller then falls back to key events.
     */
    fun encode(stroke: KeyStroke): String? {
        val ctrl = StickyModifier.CTRL in stroke.modifiers
        val alt = StickyModifier.ALT in stroke.modifiers || StickyModifier.META in stroke.modifiers
        val m = modifierParameter(stroke)

        special(stroke.code, m, stroke.shift)?.let { return it }

        val base: String = when (stroke.code) {
            KeyCodes.DONE -> "\r"
            KeyCodes.DELETE -> if (ctrl) "\u0008" else "\u007f"
            KeyCodes.SPACE -> if (ctrl) "\u0000" else " "
            else -> {
                val code = stroke.code
                if (code <= 0 || code > 0x7f) return null
                val ch = code.toChar()
                if (ctrl) controlByte(ch)?.toString() ?: return null else ch.toString()
            }
        }
        if (!ctrl && !alt) return null
        return if (alt) ESC + base else base
    }

    /** xterm modifier parameter, or 1 when there are none. */
    fun modifierParameter(stroke: KeyStroke): Int {
        var m = 1
        if (stroke.shift) m += 1
        if (StickyModifier.ALT in stroke.modifiers) m += 2
        if (StickyModifier.CTRL in stroke.modifiers) m += 4
        if (StickyModifier.META in stroke.modifiers) m += 8
        return m
    }

    private fun special(code: Int, m: Int, shift: Boolean): String? {
        KeyCodes.functionKeyNumber(code)?.let { return functionKey(it, m) }
        return when (code) {
            KeyCodes.ESCAPE -> if (m == 3) ESC + ESC else ESC
            KeyCodes.TAB -> if (shift && m == 2) "$ESC[Z" else if (m == 3) "$ESC\t" else "\t"
            KeyCodes.ARROW_UP -> cursor('A', m)
            KeyCodes.ARROW_DOWN -> cursor('B', m)
            KeyCodes.ARROW_RIGHT -> cursor('C', m)
            KeyCodes.ARROW_LEFT -> cursor('D', m)
            KeyCodes.HOME -> cursor('H', m)
            KeyCodes.END -> cursor('F', m)
            KeyCodes.INSERT -> tilde(2, m)
            KeyCodes.FORWARD_DELETE -> tilde(3, m)
            KeyCodes.PAGE_UP -> tilde(5, m)
            KeyCodes.PAGE_DOWN -> tilde(6, m)
            else -> null
        }
    }

    private fun cursor(final: Char, m: Int) = if (m == 1) "$ESC[$final" else "$ESC[1;$m$final"

    private fun tilde(n: Int, m: Int) = if (m == 1) "$ESC[$n~" else "$ESC[$n;$m~"

    private val F_TILDE = intArrayOf(15, 17, 18, 19, 20, 21, 23, 24)

    private fun functionKey(n: Int, m: Int): String = if (n <= 4) {
        val final = 'P' + (n - 1)
        if (m == 1) "${ESC}O$final" else "$ESC[1;$m$final"
    } else {
        tilde(F_TILDE[n - 5], m)
    }

    /** C0 control byte for Ctrl+[ch], or null if Ctrl has no byte for it. */
    fun controlByte(ch: Char): Char? = when (ch) {
        in 'a'..'z' -> (ch - 'a' + 1).toChar()
        in 'A'..'Z' -> (ch - 'A' + 1).toChar()
        '@', '2', ' ' -> '\u0000'
        '[', '3' -> '\u001b'
        '\\', '4' -> '\u001c'
        ']', '5' -> '\u001d'
        '^', '6', '~', '`' -> '\u001e'
        '_', '7', '-', '/' -> '\u001f'
        '?', '8' -> '\u007f'
        else -> null
    }
}
