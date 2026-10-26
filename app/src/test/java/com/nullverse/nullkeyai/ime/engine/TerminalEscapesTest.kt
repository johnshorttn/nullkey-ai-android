package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalEscapesTest {
    private val esc = "\u001b"
    private val ctrl = setOf(StickyModifier.CTRL)
    private val alt = setOf(StickyModifier.ALT)

    private fun enc(code: Int, mods: Set<StickyModifier> = emptySet(), shift: Boolean = false) =
        TerminalEscapes.encode(KeyStroke(code, mods, shift))

    @Test
    fun plainTerminalKeys() {
        assertEquals(esc, enc(KeyCodes.ESCAPE))
        assertEquals("\t", enc(KeyCodes.TAB))
        assertEquals("$esc[A", enc(KeyCodes.ARROW_UP))
        assertEquals("$esc[B", enc(KeyCodes.ARROW_DOWN))
        assertEquals("$esc[C", enc(KeyCodes.ARROW_RIGHT))
        assertEquals("$esc[D", enc(KeyCodes.ARROW_LEFT))
        assertEquals("$esc[H", enc(KeyCodes.HOME))
        assertEquals("$esc[F", enc(KeyCodes.END))
        assertEquals("$esc[2~", enc(KeyCodes.INSERT))
        assertEquals("$esc[3~", enc(KeyCodes.FORWARD_DELETE))
        assertEquals("$esc[5~", enc(KeyCodes.PAGE_UP))
        assertEquals("$esc[6~", enc(KeyCodes.PAGE_DOWN))
    }

    @Test
    fun functionKeys() {
        val expected = listOf(
            "${esc}OP", "${esc}OQ", "${esc}OR", "${esc}OS",
            "$esc[15~", "$esc[17~", "$esc[18~", "$esc[19~",
            "$esc[20~", "$esc[21~", "$esc[23~", "$esc[24~",
        )
        for (n in 1..12) assertEquals("F$n", expected[n - 1], enc(KeyCodes.functionKey(n)))
        assertEquals("$esc[1;5P", enc(KeyCodes.functionKey(1), ctrl))
        assertEquals("$esc[15;2~", enc(KeyCodes.functionKey(5), shift = true))
    }

    @Test
    fun modifiedCursorKeysUseXtermParameter() {
        assertEquals("$esc[1;5C", enc(KeyCodes.ARROW_RIGHT, ctrl))
        assertEquals("$esc[1;3D", enc(KeyCodes.ARROW_LEFT, alt))
        assertEquals("$esc[1;2A", enc(KeyCodes.ARROW_UP, shift = true))
        assertEquals("$esc[1;7B", enc(KeyCodes.ARROW_DOWN, ctrl + alt))
        assertEquals("$esc[1;9H", enc(KeyCodes.HOME, setOf(StickyModifier.META)))
        assertEquals("$esc[5;5~", enc(KeyCodes.PAGE_UP, ctrl))
        assertEquals("$esc[Z", enc(KeyCodes.TAB, shift = true))
    }

    @Test
    fun ctrlLettersBecomeControlBytes() {
        assertEquals("\u0003", enc('c'.code, ctrl))
        assertEquals("\u0003", enc('C'.code, ctrl, shift = true))
        assertEquals("\u0004", enc('d'.code, ctrl))
        assertEquals("\u001a", enc('z'.code, ctrl))
        assertEquals("\u0001", enc('a'.code, ctrl))
        assertEquals("\u001b", enc('['.code, ctrl))
        assertEquals("\u001c", enc('\\'.code, ctrl))
        assertEquals("\u001f", enc('_'.code, ctrl))
        assertEquals("\u0000", enc(KeyCodes.SPACE, ctrl))
        assertEquals("\u0008", enc(KeyCodes.DELETE, ctrl))
    }

    @Test
    fun altPrefixesEscape() {
        assertEquals("${esc}b", enc('b'.code, alt))
        assertEquals("${esc}.", enc('.'.code, alt))
        assertEquals("$esc\u0018", enc('x'.code, ctrl + alt))
        assertEquals("${esc}\u007f", enc(KeyCodes.DELETE, alt))
        assertEquals("$esc\r", enc(KeyCodes.DONE, alt))
        assertEquals("${esc}f", enc('f'.code, setOf(StickyModifier.META)))
    }

    @Test
    fun noSequenceFallsBackToKeyEvents() {
        assertNull(enc('é'.code, ctrl))
        assertNull(enc('1'.code, ctrl))
        assertNull(enc('a'.code))
    }
}
