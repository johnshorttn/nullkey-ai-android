package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Controller routing for terminal keys and Ctrl/Alt/Meta. */
class TerminalKeyControllerTest {

    private object TerminalLayout : KeyboardLayoutProvider {
        private fun l(c: Char) = KeySpec(code = c.code, label = c.toString())
        override fun spec(layer: KeyboardLayer, orientation: LayoutOrientation) = KeyboardLayoutSpec(
            id = "test_terminal",
            layer = layer,
            rows = listOf(
                KeyRow(
                    listOf(
                        TerminalKeys.spec(KeyCodes.ESCAPE),
                        TerminalKeys.spec(KeyCodes.CTRL),
                        TerminalKeys.spec(KeyCodes.ALT),
                        TerminalKeys.spec(KeyCodes.ARROW_LEFT),
                        TerminalKeys.spec(KeyCodes.F1),
                    ),
                ),
                KeyRow(listOf(l('a'), l('c'), l('d'), KeySpec(KeyCodes.SHIFT, "⇧", isModifier = true))),
                KeyRow(listOf(KeySpec(KeyCodes.SPACE, "space"), KeySpec(KeyCodes.DELETE, "⌫", isRepeatable = true))),
            ),
        )
    }

    private class Host : KeyboardController.Host {
        val keys = mutableListOf<Int>()
        val strokes = mutableListOf<KeyStroke>()
        val words = mutableListOf<String>()
        override fun requestRedraw() {}
        override fun onKey(code: Int) { keys += code }
        override fun onKeyStroke(stroke: KeyStroke) { strokes += stroke }
        override fun onLongPress(code: Int, popupCharacters: String) {}
        override fun onGestureWord(path: String) { words += path }
    }

    private lateinit var scheduler: ManualScheduler
    private lateinit var host: Host
    private lateinit var controller: KeyboardController

    @Before
    fun setUp() {
        scheduler = ManualScheduler()
        host = Host()
        controller = KeyboardController(layouts = TerminalLayout, scheduler = scheduler, host = host)
        controller.resize(1000f, 300f, LayoutOrientation.PORTRAIT, 0f, 0f, 0f)
    }

    private fun key(code: Int) = controller.geometry.placedKeys.first { it.spec.code == code }

    private fun tap(code: Int) {
        val k = key(code)
        controller.down(0, k.slot.centerX, k.slot.centerY)
        controller.up(0, k.slot.centerX, k.slot.centerY)
        scheduler.advance(1_000)
    }

    @Test
    fun escapeIsSentAsAKeyStroke() {
        tap(KeyCodes.ESCAPE)
        assertEquals(listOf(KeyStroke(KeyCodes.ESCAPE)), host.strokes)
        assertTrue(host.keys.isEmpty())
    }

    @Test
    fun functionKeyIsSentAsAKeyStroke() {
        tap(KeyCodes.F1)
        assertEquals(KeyCodes.F1, host.strokes.single().code)
    }

    @Test
    fun ctrlThenLetterSendsOneChordAndClearsCtrl() {
        tap(KeyCodes.CTRL)
        assertTrue(controller.isModifierActive(key(KeyCodes.CTRL)))
        tap('c'.code)
        assertEquals(listOf(KeyStroke('c'.code, setOf(StickyModifier.CTRL))), host.strokes)
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.CTRL))
        tap('d'.code)
        assertEquals(listOf('d'.code), host.keys)
    }

    @Test
    fun longPressLocksCtrlAcrossKeys() {
        val ctrl = key(KeyCodes.CTRL)
        controller.down(0, ctrl.slot.centerX, ctrl.slot.centerY)
        scheduler.advance(KeyboardEngineDefaults.LONG_PRESS_MS + 10)
        controller.up(0, ctrl.slot.centerX, ctrl.slot.centerY)
        assertEquals(ShiftState.LOCKED, controller.modifiers.stickyState(StickyModifier.CTRL))
        tap('c'.code)
        tap('d'.code)
        assertEquals(2, host.strokes.size)
        assertTrue(host.strokes.all { StickyModifier.CTRL in it.modifiers })
    }

    @Test
    fun shiftAppliesToSpecialKeysAndIsConsumed() {
        tap(KeyCodes.SHIFT)
        tap(KeyCodes.ESCAPE)
        assertEquals(KeyStroke(KeyCodes.ESCAPE, shift = true), host.strokes.single())
        assertEquals(ShiftState.OFF, controller.modifiers.shift)
    }

    @Test
    fun repeatingArrowKeepsModifiersUntilRelease() {
        tap(KeyCodes.ALT)
        val arrow = key(KeyCodes.ARROW_LEFT)
        controller.down(0, arrow.slot.centerX, arrow.slot.centerY)
        scheduler.advance(KeyboardEngineDefaults.REPEAT_START_MS + KeyboardEngineDefaults.REPEAT_INTERVAL_MS * 2)
        assertTrue(host.strokes.size >= 2)
        assertTrue(host.strokes.all { StickyModifier.ALT in it.modifiers })
        assertTrue(controller.modifiers.isStickyActive(StickyModifier.ALT))
        controller.up(0, arrow.slot.centerX, arrow.slot.centerY)
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.ALT))
    }

    @Test
    fun ctrlBackspaceIsAChord() {
        tap(KeyCodes.CTRL)
        val del = key(KeyCodes.DELETE)
        controller.down(0, del.slot.centerX, del.slot.centerY)
        controller.up(0, del.slot.centerX, del.slot.centerY)
        assertEquals(KeyStroke(KeyCodes.DELETE, setOf(StickyModifier.CTRL)), host.strokes.first())
    }

    @Test
    fun plainLettersStillUseOnKey() {
        tap('a'.code)
        assertEquals(listOf('a'.code), host.keys)
        assertTrue(host.strokes.isEmpty())
    }
}
