package com.nullverse.nullkeyai.ime.engine

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeysTest {

    private fun events(stroke: KeyStroke, allowCtrlA: Boolean = false): List<KeyEventSpec> {
        val plan = KeyStrokePlanner.plan(stroke, allowCtrlA)
        assertTrue("expected events, got $plan", plan is KeyStrokePlan.Events)
        return (plan as KeyStrokePlan.Events).events
    }

    @Test
    fun terminalCodesAreNeverMistakenForLetters() {
        val codes = listOf(
            KeyCodes.ESCAPE, KeyCodes.TAB, KeyCodes.CTRL, KeyCodes.META,
            KeyCodes.ARROW_LEFT, KeyCodes.ARROW_RIGHT, KeyCodes.ARROW_UP, KeyCodes.ARROW_DOWN,
            KeyCodes.HOME, KeyCodes.END, KeyCodes.PAGE_UP, KeyCodes.PAGE_DOWN,
            KeyCodes.INSERT, KeyCodes.FORWARD_DELETE,
        ) + KeyCodes.FUNCTION_KEYS
        assertEquals(codes.size, codes.toSet().size)
        for (code in codes) {
            assertFalse("$code reads as a letter", code.toChar().isLetter())
        }
    }

    @Test
    fun functionKeysMapToF1ThroughF12() {
        assertEquals(12, KeyCodes.FUNCTION_KEYS.size)
        for (n in 1..12) {
            val code = KeyCodes.functionKey(n)
            assertEquals(n, KeyCodes.functionKeyNumber(code))
            assertEquals(KeyEvent.KEYCODE_F1 + n - 1, KeyStrokePlanner.keyCodeForSpecial(code))
            assertEquals("F$n", TerminalKeys.spec(code).label)
        }
        assertNull(KeyCodes.functionKeyNumber(KeyCodes.ESCAPE))
    }

    @Test
    fun specialKeysMapToRealKeyCodes() {
        val expected = mapOf(
            KeyCodes.ESCAPE to KeyEvent.KEYCODE_ESCAPE,
            KeyCodes.TAB to KeyEvent.KEYCODE_TAB,
            KeyCodes.ARROW_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            KeyCodes.ARROW_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyCodes.ARROW_UP to KeyEvent.KEYCODE_DPAD_UP,
            KeyCodes.ARROW_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            KeyCodes.HOME to KeyEvent.KEYCODE_MOVE_HOME,
            KeyCodes.END to KeyEvent.KEYCODE_MOVE_END,
            KeyCodes.PAGE_UP to KeyEvent.KEYCODE_PAGE_UP,
            KeyCodes.PAGE_DOWN to KeyEvent.KEYCODE_PAGE_DOWN,
            KeyCodes.INSERT to KeyEvent.KEYCODE_INSERT,
            KeyCodes.FORWARD_DELETE to KeyEvent.KEYCODE_FORWARD_DEL,
        )
        for ((code, keyCode) in expected) {
            assertEquals(keyCode, KeyStrokePlanner.keyCodeForSpecial(code))
            assertTrue(KeyCodes.isKeyEventKey(code))
        }
    }

    @Test
    fun plainEscapeIsADownUpPairWithNoMeta() {
        assertEquals(
            listOf(
                KeyEventSpec(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0),
                KeyEventSpec(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0),
            ),
            events(KeyStroke(KeyCodes.ESCAPE)),
        )
    }

    @Test
    fun ctrlCWrapsTheKeyInCtrlDownAndUp() {
        val ctrl = KeyStrokePlanner.CTRL_META
        assertEquals(
            listOf(
                KeyEventSpec(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, ctrl),
                KeyEventSpec(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, ctrl),
                KeyEventSpec(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, ctrl),
                KeyEventSpec(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0),
            ),
            events(KeyStroke('c'.code, setOf(StickyModifier.CTRL))),
        )
    }

    @Test
    fun uppercaseLetterAndShiftedSymbolsAddShiftMeta() {
        val upper = events(KeyStroke('C'.code, setOf(StickyModifier.CTRL)))
        val keyDown = upper.first { it.keyCode == KeyEvent.KEYCODE_C }
        assertEquals(KeyStrokePlanner.CTRL_META or KeyStrokePlanner.SHIFT_META, keyDown.metaState)

        val pipe = events(KeyStroke('|'.code, setOf(StickyModifier.ALT)))
        val pipeDown = pipe.first { it.keyCode == KeyEvent.KEYCODE_BACKSLASH }
        assertEquals(KeyStrokePlanner.ALT_META or KeyStrokePlanner.SHIFT_META, pipeDown.metaState)
    }

    @Test
    fun combinedModifiersStackAndUnwindInReverse() {
        val list = events(
            KeyStroke(KeyCodes.ARROW_RIGHT, setOf(StickyModifier.CTRL, StickyModifier.ALT), shift = true),
        )
        val all = KeyStrokePlanner.CTRL_META or KeyStrokePlanner.ALT_META or KeyStrokePlanner.SHIFT_META
        val arrowDown = list.first { it.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && it.action == KeyEvent.ACTION_DOWN }
        assertEquals(all, arrowDown.metaState)
        assertEquals(0, list.last().metaState)
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, list.last().keyCode)
        assertEquals(8, list.size)
    }

    @Test
    fun shiftTabSendsShiftMeta() {
        val list = events(KeyStroke(KeyCodes.TAB, shift = true))
        assertEquals(
            KeyStrokePlanner.SHIFT_META,
            list.first { it.keyCode == KeyEvent.KEYCODE_TAB }.metaState,
        )
    }

    @Test
    fun plainCharactersPassThrough() {
        assertEquals(KeyStrokePlan.PassThrough, KeyStrokePlanner.plan(KeyStroke('a'.code), allowCtrlA = false))
    }

    @Test
    fun modifiedCharacterWithNoKeyCodeIsCommittedAsText() {
        assertEquals(
            KeyStrokePlan.Text("é"),
            KeyStrokePlanner.plan(KeyStroke('é'.code, setOf(StickyModifier.CTRL)), allowCtrlA = false),
        )
    }

    @Test
    fun ctrlBackspaceEnterAndSpaceBecomeKeyEvents() {
        val ctrl = setOf(StickyModifier.CTRL)
        assertTrue(events(KeyStroke(KeyCodes.DELETE, ctrl)).any { it.keyCode == KeyEvent.KEYCODE_DEL })
        assertTrue(events(KeyStroke(KeyCodes.DONE, ctrl)).any { it.keyCode == KeyEvent.KEYCODE_ENTER })
        assertTrue(events(KeyStroke(KeyCodes.SPACE, ctrl)).any { it.keyCode == KeyEvent.KEYCODE_SPACE })
    }

    @Test
    fun ctrlAIsGuardedInNormalFieldsOnly() {
        val ctrlA = KeyStroke('a'.code, setOf(StickyModifier.CTRL))
        assertEquals(KeyStrokePlan.Blocked, KeyStrokePlanner.plan(ctrlA, allowCtrlA = false))
        assertTrue(KeyStrokePlanner.plan(ctrlA, allowCtrlA = true) is KeyStrokePlan.Events)
        // Ctrl+Alt+A is a deliberate chord, not select-all.
        val ctrlAltA = KeyStroke('a'.code, setOf(StickyModifier.CTRL, StickyModifier.ALT))
        assertTrue(KeyStrokePlanner.plan(ctrlAltA, allowCtrlA = false) is KeyStrokePlan.Events)
    }

    @Test
    fun metaStateHelperCombinesFlags() {
        assertEquals(0, KeyStrokePlanner.metaStateFor(emptySet(), shift = false))
        val meta = KeyStrokePlanner.metaStateFor(setOf(StickyModifier.META), shift = false)
        assertTrue(meta and KeyEvent.META_META_ON != 0)
    }

    @Test
    fun terminalKeySpecsAreModifierColouredAndArrowsRepeat() {
        assertTrue(TerminalKeys.spec(KeyCodes.ESCAPE).isModifier)
        assertTrue(TerminalKeys.spec(KeyCodes.ARROW_UP).isRepeatable)
        assertFalse(TerminalKeys.spec(KeyCodes.HOME).isRepeatable)
        assertEquals("Ctrl", TerminalKeys.spec(KeyCodes.CTRL).label)
    }
}
