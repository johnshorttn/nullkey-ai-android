package com.nullverse.nullkeyai.ime.engine

/**
 * Built-in NullKey layouts. Portrait letters are a 4-row QWERTY. Landscape
 * letters add a number row above QWERTY (same abstraction, different spec).
 * Symbols are shared across orientations.
 */
class DefaultKeyboardLayoutProvider : KeyboardLayoutProvider {
    override fun spec(layer: KeyboardLayer, orientation: LayoutOrientation): KeyboardLayoutSpec {
        return when (layer) {
            KeyboardLayer.LETTERS -> when (orientation) {
                LayoutOrientation.PORTRAIT -> LETTERS_PORTRAIT
                LayoutOrientation.LANDSCAPE -> LETTERS_LANDSCAPE
            }
            KeyboardLayer.SYMBOLS -> SYMBOLS
        }
    }

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
    }
}

private fun letter(ch: Char, popup: String = ""): KeySpec =
    KeySpec(code = ch.code, label = ch.toString(), popupCharacters = popup)

private fun punct(ch: Char, weight: Float = 1f): KeySpec =
    KeySpec(code = ch.code, label = ch.toString(), widthWeight = weight)

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
        KeySpec(
            code = KeyCodes.SHIFT,
            label = "⇧",
            shiftedLabel = "⇪",
            widthWeight = 1.5f,
            isModifier = true,
        ),
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
