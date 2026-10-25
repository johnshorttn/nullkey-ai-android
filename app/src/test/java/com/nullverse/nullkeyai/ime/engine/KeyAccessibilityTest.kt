package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyAccessibilityTest {

    private fun strings() =
        KeyAccessibility.Strings.from(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun lettersUseTheDisplayedGlyph() {
        val spoken = KeyAccessibility.spokenLabel(
            code = 'q'.code,
            displayLabel = "Q",
            shift = ShiftState.ON,
            layer = KeyboardLayer.LETTERS,
            strings = strings(),
        )
        assertEquals("Q", spoken)
    }

    @Test
    fun functionKeysUseSpokenNames() {
        val labels = strings()
        assertEquals(
            "Space",
            KeyAccessibility.spokenLabel(KeyCodes.SPACE, "space", ShiftState.OFF, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Shift",
            KeyAccessibility.spokenLabel(KeyCodes.SHIFT, "⇧", ShiftState.OFF, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Shift on",
            KeyAccessibility.spokenLabel(KeyCodes.SHIFT, "⇧", ShiftState.ON, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Caps lock",
            KeyAccessibility.spokenLabel(KeyCodes.SHIFT, "⇪", ShiftState.LOCKED, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Delete",
            KeyAccessibility.spokenLabel(KeyCodes.DELETE, "⌫", ShiftState.OFF, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Enter",
            KeyAccessibility.spokenLabel(KeyCodes.DONE, "enter", ShiftState.OFF, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Symbols",
            KeyAccessibility.spokenLabel(KeyCodes.MODE_CHANGE, "?123", ShiftState.OFF, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Letters",
            KeyAccessibility.spokenLabel(KeyCodes.MODE_CHANGE, "ABC", ShiftState.OFF, KeyboardLayer.SYMBOLS, labels),
        )
    }

    @Test
    fun punctuationUsesSpokenNames() {
        val labels = strings()
        assertEquals(
            "Comma",
            KeyAccessibility.spokenLabel(','.code, ",", ShiftState.OFF, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Period",
            KeyAccessibility.spokenLabel('.'.code, ".", ShiftState.OFF, KeyboardLayer.LETTERS, labels),
        )
        assertEquals(
            "Apostrophe",
            KeyAccessibility.spokenLabel('\''.code, "'", ShiftState.OFF, KeyboardLayer.SYMBOLS, labels),
        )
        assertEquals(
            "Quotation mark",
            KeyAccessibility.spokenLabel('"'.code, "\"", ShiftState.OFF, KeyboardLayer.SYMBOLS, labels),
        )
    }

    @Test
    fun terminalKeysHaveSpokenNamesAndModifierState() {
        val labels = strings()
        fun spoken(code: Int, label: String, sticky: ShiftState = ShiftState.OFF) =
            KeyAccessibility.spokenLabel(code, label, ShiftState.OFF, KeyboardLayer.LETTERS, labels) { sticky }
        assertEquals("Escape", spoken(KeyCodes.ESCAPE, "Esc"))
        assertEquals("Tab", spoken(KeyCodes.TAB, "Tab"))
        assertEquals("Left arrow", spoken(KeyCodes.ARROW_LEFT, "←"))
        assertEquals("Page down", spoken(KeyCodes.PAGE_DOWN, "PgDn"))
        assertEquals("Forward delete", spoken(KeyCodes.FORWARD_DELETE, "Del"))
        assertEquals("F7", spoken(KeyCodes.functionKey(7), "F7"))
        assertEquals("Control", spoken(KeyCodes.CTRL, "Ctrl"))
        assertEquals("Control on", spoken(KeyCodes.CTRL, "Ctrl", ShiftState.ON))
        assertEquals("Alt locked", spoken(KeyCodes.ALT, "ALT", ShiftState.LOCKED))
    }
}
