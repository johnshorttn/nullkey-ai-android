package com.nullverse.nullkeyai.ime.engine

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalModeTest {
    private val text = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT

    @Test
    fun terminalAppsTurnItOn() {
        for (pkg in listOf("com.termux", "com.sonelli.juicessh", "org.connectbot")) {
            assertTrue(pkg, TerminalMode.detect(pkg, text, enabled = true))
        }
    }

    @Test
    fun rawAndVisiblePasswordFieldsTurnItOn() {
        assertTrue(TerminalMode.detect("com.example", InputType.TYPE_NULL, enabled = true))
        assertTrue(
            TerminalMode.detect(
                "com.example",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                enabled = true,
            ),
        )
    }

    @Test
    fun ordinaryFieldsStayNormal() {
        assertFalse(TerminalMode.detect("com.example.notes", text, enabled = true))
        assertFalse(TerminalMode.detect(null, text, enabled = true))
        assertFalse(
            TerminalMode.detect(
                "com.example",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                enabled = true,
            ),
        )
        assertFalse(TerminalMode.detect("com.example", InputType.TYPE_CLASS_NUMBER, enabled = true))
    }

    @Test
    fun toggleOffDisablesDetection() {
        assertFalse(TerminalMode.detect("com.termux", InputType.TYPE_NULL, enabled = false))
    }

    @Test
    fun terminalModeShowsTheTerminalRowOnTheDefaultLayout() {
        val provider = DefaultKeyboardLayoutProvider { KeyboardLayoutConfig(forceTerminalRow = true) }
        for (orientation in LayoutOrientation.entries) {
            val spec = provider.spec(KeyboardLayer.LETTERS, orientation)
            assertEquals(KeyCodes.ESCAPE, spec.rows.first().keys.first().code)
        }
    }
}
