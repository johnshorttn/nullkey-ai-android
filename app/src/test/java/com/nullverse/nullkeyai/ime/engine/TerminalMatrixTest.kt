package com.nullverse.nullkeyai.ime.engine

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The terminal test matrix from docs/TERMINAL_TEST_MATRIX.md, as unit tests:
 * for each app NullKey is checked in, what terminal mode, Ctrl+A and the
 * escape fallback do. Device checks cover the parts a unit test cannot.
 */
class TerminalMatrixTest {

    private data class Target(val name: String, val pkg: String, val inputType: Int, val terminal: Boolean)

    private val prose = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
    private val noSuggest = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

    private val matrix = listOf(
        // Termux's terminal view reports TYPE_NULL by default.
        Target("Termux", "com.termux", InputType.TYPE_NULL, terminal = true),
        Target("JuiceSSH", "com.sonelli.juicessh", prose, terminal = true),
        Target("ConnectBot", "org.connectbot", InputType.TYPE_NULL, terminal = true),
        // xterm.js uses a hidden textarea in Chrome: a normal text field.
        Target("Nullink web terminal (Chrome)", "com.android.chrome", noSuggest, terminal = false),
        Target("Normal text box", "com.google.android.keep", prose, terminal = false),
    )

    private fun decide(t: Target, output: TerminalKeyOutput, ctrlAInFields: Boolean = false) =
        TerminalOutputPolicy.decide(t.pkg, t.inputType, terminalModeAuto = true, ctrlAInTextFields = ctrlAInFields, output = output)

    private val ctrlA = KeyStroke('a'.code, setOf(StickyModifier.CTRL))
    private val ctrlC = KeyStroke('c'.code, setOf(StickyModifier.CTRL))
    private val up = KeyStroke(KeyCodes.ARROW_UP)

    @Test
    fun terminalModeAndCtrlAPerApp() {
        for (t in matrix) {
            val d = decide(t, TerminalKeyOutput.KEY_EVENTS)
            assertEquals(t.name, t.terminal, d.terminalMode)
            assertEquals(t.name, t.terminal, d.allowCtrlA)
        }
    }

    @Test
    fun defaultSendsKeyEventsEverywhere() {
        for (t in matrix) {
            val d = decide(t, TerminalKeyOutput.KEY_EVENTS)
            assertTrue(t.name, TerminalOutputPolicy.plan(up, d) is KeyStrokePlan.Events)
            assertTrue(t.name, TerminalOutputPolicy.plan(ctrlC, d) is KeyStrokePlan.Events)
        }
    }

    @Test
    fun escapesInTerminalModeOnlyTouchTerminalApps() {
        for (t in matrix) {
            val plan = TerminalOutputPolicy.plan(up, decide(t, TerminalKeyOutput.ESCAPES_IN_TERMINAL_MODE))
            if (t.terminal) {
                assertEquals(t.name, KeyStrokePlan.Escape("\u001b[A"), plan)
            } else {
                assertTrue(t.name, plan is KeyStrokePlan.Events)
            }
        }
    }

    @Test
    fun escapesEverywhereReachTheWebTerminal() {
        val web = matrix.first { it.pkg == "com.android.chrome" }
        val d = decide(web, TerminalKeyOutput.ESCAPES_EVERYWHERE)
        assertEquals(KeyStrokePlan.Escape("\u001b[A"), TerminalOutputPolicy.plan(up, d))
        assertEquals(KeyStrokePlan.Escape("\u0003"), TerminalOutputPolicy.plan(ctrlC, d))
    }

    @Test
    fun ctrlAGuardStillAppliesWithEscapes() {
        val web = matrix.first { it.pkg == "com.android.chrome" }
        val blocked = decide(web, TerminalKeyOutput.ESCAPES_EVERYWHERE)
        assertEquals(KeyStrokePlan.Blocked, TerminalOutputPolicy.plan(ctrlA, blocked))
        val optedIn = decide(web, TerminalKeyOutput.ESCAPES_EVERYWHERE, ctrlAInFields = true)
        assertEquals(KeyStrokePlan.Escape("\u0001"), TerminalOutputPolicy.plan(ctrlA, optedIn))
        val termux = matrix.first { it.pkg == "com.termux" }
        assertEquals(
            KeyStrokePlan.Escape("\u0001"),
            TerminalOutputPolicy.plan(ctrlA, decide(termux, TerminalKeyOutput.ESCAPES_IN_TERMINAL_MODE)),
        )
    }

    @Test
    fun plainLettersNeverBecomeEscapes() {
        for (t in matrix) {
            val d = decide(t, TerminalKeyOutput.ESCAPES_EVERYWHERE)
            assertEquals(t.name, KeyStrokePlan.PassThrough, TerminalOutputPolicy.plan(KeyStroke('x'.code), d))
        }
    }

    @Test
    fun vaultSearchNeverGetsEscapes() {
        val d = TerminalOutputPolicy.decide(
            "com.termux", InputType.TYPE_NULL, terminalModeAuto = true,
            ctrlAInTextFields = false, output = TerminalKeyOutput.ESCAPES_EVERYWHERE, vaultSearch = true,
        )
        assertEquals(false, d.terminalMode)
        assertTrue(TerminalOutputPolicy.plan(up, d) is KeyStrokePlan.Events)
    }

    @Test
    fun unknownStoredValueFallsBackToKeyEvents() {
        assertEquals(TerminalKeyOutput.KEY_EVENTS, TerminalKeyOutput.parse(null))
        assertEquals(TerminalKeyOutput.KEY_EVENTS, TerminalKeyOutput.parse("nope"))
        assertEquals(TerminalKeyOutput.ESCAPES_EVERYWHERE, TerminalKeyOutput.parse("ESCAPES_EVERYWHERE"))
    }
}
