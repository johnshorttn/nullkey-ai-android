package com.nullverse.nullkeyai.ime.engine

/** The three layouts John asked for. */
enum class KeyboardLayoutMode {
    /** QWERTY + bottom row. Today's portrait keyboard. */
    FOUR_ROW,

    /** Number row above QWERTY, plus an optional terminal row. Today's landscape keyboard. */
    FIVE_ROW,

    /** PC-style: Esc + F1–F12, Tab/Ctrl/Alt/Meta, arrows, Home/End/PgUp/PgDn. */
    FULL,
}

/**
 * Which layout each orientation uses. The defaults reproduce the keyboard as
 * it was before layout modes existed: 4-row portrait, 5-row landscape
 * (number row, no terminal row).
 */
data class KeyboardLayoutConfig(
    val portrait: KeyboardLayoutMode = KeyboardLayoutMode.FOUR_ROW,
    val landscape: KeyboardLayoutMode = KeyboardLayoutMode.FIVE_ROW,
    /** Esc Tab Ctrl Alt arrows - / | ~ above the 5-row layout. */
    val terminalRow: Boolean = false,
    /** Terminal mode: show the terminal row whatever the mode. */
    val forceTerminalRow: Boolean = false,
) {
    fun modeFor(orientation: LayoutOrientation): KeyboardLayoutMode = when (orientation) {
        LayoutOrientation.PORTRAIT -> portrait
        LayoutOrientation.LANDSCAPE -> landscape
    }

    companion object {
        val DEFAULT = KeyboardLayoutConfig()

        fun defaultModeFor(orientation: LayoutOrientation): KeyboardLayoutMode = DEFAULT.modeFor(orientation)
    }
}

/**
 * Built-in NullKey layouts for each [KeyboardLayoutMode]. Letter rows are the
 * same specs in every mode, so swipe typing and accent popups behave the same.
 */
class DefaultKeyboardLayoutProvider(
    private val config: () -> KeyboardLayoutConfig = { KeyboardLayoutConfig.DEFAULT },
) : KeyboardLayoutProvider {
    override fun spec(layer: KeyboardLayer, orientation: LayoutOrientation): KeyboardLayoutSpec =
        specFor(layer, orientation, config())

    companion object {
        val LETTERS_PORTRAIT: KeyboardLayoutSpec = KeyboardLayoutSpec(
            id = "letters-portrait",
            layer = KeyboardLayer.LETTERS,
            rows = listOf(qwertyRow, asdfRow, zxcvRow, bottomLettersRow),
        )

        val LETTERS_LANDSCAPE: KeyboardLayoutSpec = KeyboardLayoutSpec(
            id = "letters-landscape",
            layer = KeyboardLayer.LETTERS,
            rows = listOf(numberRow, qwertyRow, asdfRow, zxcvRow, bottomLettersRow),
        )

        val SYMBOLS: KeyboardLayoutSpec = KeyboardLayoutSpec(
            id = "symbols",
            layer = KeyboardLayer.SYMBOLS,
            rows = listOf(numberRow, symbolsRow1, symbolsRow2, bottomSymbolsRow),
        )

        /** Height weight of the terminal and F-key rows. */
        const val SHORT_ROW_WEIGHT = 0.8f

        val TERMINAL_ROW: KeyRow = KeyRow(
            heightWeight = SHORT_ROW_WEIGHT,
            keys = listOf(
                TerminalKeys.spec(KeyCodes.ESCAPE),
                TerminalKeys.spec(KeyCodes.TAB),
                TerminalKeys.spec(KeyCodes.CTRL),
                TerminalKeys.spec(KeyCodes.ALT),
                TerminalKeys.spec(KeyCodes.ARROW_LEFT),
                TerminalKeys.spec(KeyCodes.ARROW_DOWN),
                TerminalKeys.spec(KeyCodes.ARROW_UP),
                TerminalKeys.spec(KeyCodes.ARROW_RIGHT),
                punct('-'),
                punct('/'),
                punct('|'),
                punct('~'),
            ),
        )

        val LETTERS_FULL: List<KeyRow> = listOf(
            KeyRow(
                heightWeight = SHORT_ROW_WEIGHT,
                keys = listOf(TerminalKeys.spec(KeyCodes.ESCAPE)) +
                    KeyCodes.FUNCTION_KEYS.map { TerminalKeys.spec(it) } +
                    listOf(TerminalKeys.spec(KeyCodes.INSERT), TerminalKeys.spec(KeyCodes.FORWARD_DELETE)),
            ),
            KeyRow(
                keys = listOf(
                    shifted('`', '~'), shifted('1', '!'), shifted('2', '@'), shifted('3', '#'),
                    shifted('4', '$'), shifted('5', '%'), shifted('6', '^'), shifted('7', '&'),
                    shifted('8', '*'), shifted('9', '('), shifted('0', ')'), shifted('-', '_'),
                    shifted('=', '+'),
                    KeySpec(code = KeyCodes.DELETE, label = "⌫", widthWeight = 1.5f, isRepeatable = true),
                ),
            ),
            KeyRow(
                keys = listOf(TerminalKeys.spec(KeyCodes.TAB, 1.5f)) + qwertyRow.keys +
                    listOf(shifted('[', '{'), shifted(']', '}'), shifted('\\', '|')),
            ),
            KeyRow(
                keys = listOf(TerminalKeys.spec(KeyCodes.CTRL, 1.75f)) + asdfRow.keys +
                    listOf(
                        shifted(';', ':'),
                        shifted('\'', '"'),
                        KeySpec(code = KeyCodes.DONE, label = "enter", widthWeight = 1.75f),
                    ),
            ),
            KeyRow(
                keys = listOf(shiftKey(1.5f)) + zxcvRow.keys.filter { it.code.toChar().isLetter() } +
                    listOf(
                        shifted(',', '<'), shifted('.', '>'), shifted('/', '?'),
                        TerminalKeys.spec(KeyCodes.PAGE_UP),
                        TerminalKeys.spec(KeyCodes.ARROW_UP),
                        TerminalKeys.spec(KeyCodes.PAGE_DOWN),
                    ),
            ),
            KeyRow(
                keys = listOf(
                    KeySpec(code = KeyCodes.MODE_CHANGE, label = "?123", widthWeight = 1.3f, isModifier = true),
                    TerminalKeys.spec(KeyCodes.ALT),
                    TerminalKeys.spec(KeyCodes.META),
                    TerminalKeys.spec(KeyCodes.HOME),
                    KeySpec(code = KeyCodes.SPACE, label = "space", widthWeight = 4f),
                    TerminalKeys.spec(KeyCodes.END),
                    TerminalKeys.spec(KeyCodes.ARROW_LEFT),
                    TerminalKeys.spec(KeyCodes.ARROW_DOWN),
                    TerminalKeys.spec(KeyCodes.ARROW_RIGHT),
                ),
            ),
        )

        fun specFor(
            layer: KeyboardLayer,
            orientation: LayoutOrientation,
            config: KeyboardLayoutConfig,
        ): KeyboardLayoutSpec {
            val mode = config.modeFor(orientation)
            val terminal = config.forceTerminalRow ||
                (mode == KeyboardLayoutMode.FIVE_ROW && config.terminalRow)
            val orient = orientation.name.lowercase()
            if (layer == KeyboardLayer.SYMBOLS) {
                // Full shows Esc/Ctrl/arrows on letters; give symbols the
                // terminal row so they stay reachable there too.
                val withTerminal = terminal || mode == KeyboardLayoutMode.FULL
                if (!withTerminal) return SYMBOLS
                return KeyboardLayoutSpec(
                    id = "symbols-terminal",
                    layer = KeyboardLayer.SYMBOLS,
                    rows = listOf(TERMINAL_ROW) + SYMBOLS.rows,
                )
            }
            val base = when (mode) {
                KeyboardLayoutMode.FOUR_ROW ->
                    if (orientation == LayoutOrientation.PORTRAIT) LETTERS_PORTRAIT
                    else LETTERS_PORTRAIT.copy(id = "letters-4row-landscape")
                KeyboardLayoutMode.FIVE_ROW ->
                    if (orientation == LayoutOrientation.LANDSCAPE) LETTERS_LANDSCAPE
                    else LETTERS_LANDSCAPE.copy(id = "letters-5row-portrait")
                KeyboardLayoutMode.FULL -> KeyboardLayoutSpec(
                    id = "letters-full-$orient",
                    layer = KeyboardLayer.LETTERS,
                    rows = LETTERS_FULL,
                )
            }
            if (!terminal || mode == KeyboardLayoutMode.FULL) return base
            return base.copy(id = base.id + "-terminal", rows = listOf(TERMINAL_ROW) + base.rows)
        }
    }
}

private fun letter(ch: Char, popup: String = ""): KeySpec =
    KeySpec(code = ch.code, label = ch.toString(), popupCharacters = popup)

private fun punct(ch: Char, weight: Float = 1f): KeySpec =
    KeySpec(code = ch.code, label = ch.toString(), widthWeight = weight)

/** A PC key with a Shift character; long-press also offers it. */
private fun shifted(ch: Char, shift: Char): KeySpec = KeySpec(
    code = ch.code,
    label = ch.toString(),
    shiftedLabel = shift.toString(),
    shiftedCode = shift.code,
    popupCharacters = shift.toString(),
)

private fun shiftKey(weight: Float): KeySpec = KeySpec(
    code = KeyCodes.SHIFT,
    label = "⇧",
    shiftedLabel = "⇪",
    widthWeight = weight,
    isModifier = true,
)

private val numberRow = KeyRow(
    keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0').map { punct(it) },
)

private val qwertyRow = KeyRow(
    keys = listOf(
        letter('q'),
        letter('w'),
        letter('e', "èéêëēėę"),
        letter('r'),
        letter('t'),
        letter('y', "ÿ"),
        letter('u', "ùúûüū"),
        letter('i', "ìíîïī"),
        letter('o', "òóôöõøō"),
        letter('p'),
    ),
)

private val asdfRow = KeyRow(
    leadingGapWeight = 0.5f,
    trailingGapWeight = 0.5f,
    keys = listOf(
        letter('a', "àáâäæãåā"),
        letter('s', "ßśš"),
        letter('d'),
        letter('f'),
        letter('g'),
        letter('h'),
        letter('j'),
        letter('k'),
        letter('l'),
    ),
)

private val zxcvRow = KeyRow(
    keys = listOf(
        shiftKey(1.5f),
        letter('z'),
        letter('x'),
        letter('c', "çćč"),
        letter('v'),
        letter('b'),
        letter('n', "ñń"),
        letter('m'),
        KeySpec(
            code = KeyCodes.DELETE,
            label = "⌫",
            widthWeight = 1.5f,
            isRepeatable = true,
        ),
    ),
)

private val bottomLettersRow = KeyRow(
    keys = listOf(
        KeySpec(code = KeyCodes.MODE_CHANGE, label = "?123", widthWeight = 1.8f, isModifier = true),
        punct(',', 1.2f),
        KeySpec(code = KeyCodes.SPACE, label = "space", widthWeight = 4f),
        punct('.', 1.2f),
        KeySpec(code = KeyCodes.DONE, label = "enter", widthWeight = 1.8f),
    ),
)

private val symbolsRow1 = KeyRow(
    keys = listOf('@', '#', '$', '%', '&', '-', '+', '(', ')', '/').map { punct(it) },
)

private val symbolsRow2 = KeyRow(
    keys = listOf(
        punct('*'),
        punct('"'),
        punct('\''),
        punct(':'),
        punct(';'),
        punct('!'),
        punct('?'),
        KeySpec(
            code = KeyCodes.DELETE,
            label = "⌫",
            widthWeight = 3f,
            isRepeatable = true,
        ),
    ),
)

private val bottomSymbolsRow = KeyRow(
    keys = listOf(
        KeySpec(code = KeyCodes.MODE_CHANGE, label = "ABC", widthWeight = 2f, isModifier = true),
        punct(','),
        KeySpec(code = KeyCodes.SPACE, label = "space", widthWeight = 4f),
        punct('.'),
        KeySpec(code = KeyCodes.DONE, label = "enter", widthWeight = 2f),
    ),
)
