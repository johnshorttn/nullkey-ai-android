package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Two-finger chording: one finger holds Shift/Ctrl/Alt/Meta, another types.
 *
 * Behaviour decisions under test:
 * - Held + tap: the tapped key gets the held modifier.
 * - Held + several taps: every key typed while it is held gets it.
 * - Held, then released with no second finger: an ordinary tap (one-shot,
 *   double-tap lock, long-press lock all unchanged). The chord only starts
 *   when a second finger lands on a key.
 * - Releasing a held modifier never toggles one-shot or lock.
 */
class ChordingTest {

    private object Layout : KeyboardLayoutProvider {
        private fun l(c: Char) = KeySpec(code = c.code, label = c.toString())
        override fun spec(layer: KeyboardLayer, orientation: LayoutOrientation) = KeyboardLayoutSpec(
            id = "test_chording",
            layer = layer,
            rows = listOf(
                KeyRow(
                    listOf(
                        TerminalKeys.spec(KeyCodes.ESCAPE),
                        TerminalKeys.spec(KeyCodes.CTRL),
                        TerminalKeys.spec(KeyCodes.ALT),
                        TerminalKeys.spec(KeyCodes.META),
                        TerminalKeys.spec(KeyCodes.ARROW_LEFT),
                    ),
                ),
                KeyRow(listOf(l('a'), l('c'), l('d'), l('e'), KeySpec(KeyCodes.SHIFT, "⇧", isModifier = true))),
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
        controller = KeyboardController(layouts = Layout, scheduler = scheduler, host = host)
        controller.resize(1000f, 300f, LayoutOrientation.PORTRAIT, 0f, 0f, 0f)
    }

    private fun key(code: Int) = controller.geometry.placedKeys.first { it.spec.code == code }
    private fun key(c: Char) = key(c.code)

    private fun press(pointer: Int, code: Int) {
        val k = key(code)
        controller.down(pointer, k.slot.centerX, k.slot.centerY)
    }

    private fun lift(pointer: Int, code: Int) {
        val k = key(code)
        controller.up(pointer, k.slot.centerX, k.slot.centerY)
    }

    private fun tap(pointer: Int, code: Int) {
        press(pointer, code)
        lift(pointer, code)
    }

    private fun ctrl(c: Char, vararg more: StickyModifier) =
        KeyStroke(c.code, setOf(StickyModifier.CTRL, *more))

    @Test
    fun heldCtrlPlusTapSendsCtrlChord() {
        press(0, KeyCodes.CTRL)
        tap(1, 'c'.code)
        assertEquals(listOf(ctrl('c')), host.strokes)
        assertTrue(controller.isModifierActive(key(KeyCodes.CTRL)))
        lift(0, KeyCodes.CTRL)
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.CTRL))
        assertFalse(controller.isModifierActive(key(KeyCodes.CTRL)))
        tap(0, 'd'.code)
        assertEquals(listOf('d'.code), host.keys)
    }

    @Test
    fun heldCtrlAppliesToEveryTapUntilReleased() {
        press(0, KeyCodes.CTRL)
        tap(1, 'c'.code)
        tap(2, 'd'.code)
        tap(1, 'a'.code)
        assertEquals(listOf(ctrl('c'), ctrl('d'), ctrl('a')), host.strokes)
        lift(0, KeyCodes.CTRL)
        tap(0, 'e'.code)
        assertEquals(listOf('e'.code), host.keys)
        assertEquals(3, host.strokes.size)
    }

    @Test
    fun heldThenReleasedWithNoTapIsAnOrdinaryOneShotTap() {
        press(0, KeyCodes.CTRL)
        lift(0, KeyCodes.CTRL)
        assertEquals(ShiftState.ON, controller.modifiers.stickyState(StickyModifier.CTRL))
        tap(0, 'c'.code)
        assertEquals(listOf(ctrl('c')), host.strokes)
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun doubleTapStillLocksAndHoldingAnotherModifierLeavesTheLock() {
        tap(0, KeyCodes.CTRL)
        tap(0, KeyCodes.CTRL)
        assertEquals(ShiftState.LOCKED, controller.modifiers.stickyState(StickyModifier.CTRL))
        press(0, KeyCodes.ALT)
        tap(1, 'c'.code)
        lift(0, KeyCodes.ALT)
        assertEquals(listOf(ctrl('c', StickyModifier.ALT)), host.strokes)
        assertEquals(ShiftState.LOCKED, controller.modifiers.stickyState(StickyModifier.CTRL))
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.ALT))
    }

    @Test
    fun releasingAHeldModifierDoesNotToggleItsOwnOneShot() {
        scheduler.advance(1_000)
        tap(0, KeyCodes.CTRL)
        assertEquals(ShiftState.ON, controller.modifiers.stickyState(StickyModifier.CTRL))
        scheduler.advance(1_000) // past the double-tap window
        press(0, KeyCodes.CTRL)
        tap(1, 'c'.code)
        lift(0, KeyCodes.CTRL)
        // The one-shot was used by 'c' as usual; the chord release added nothing.
        assertEquals(listOf(ctrl('c')), host.strokes)
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun longPressOnModifierBeforeTheChordDoesNotLeaveItLocked() {
        press(0, KeyCodes.CTRL)
        scheduler.advance(KeyboardEngineDefaults.LONG_PRESS_MS + 50)
        assertEquals(ShiftState.LOCKED, controller.modifiers.stickyState(StickyModifier.CTRL))
        tap(1, 'c'.code)
        lift(0, KeyCodes.CTRL)
        assertEquals(listOf(ctrl('c')), host.strokes)
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun longPressWithoutSecondFingerStillLocks() {
        press(0, KeyCodes.CTRL)
        scheduler.advance(KeyboardEngineDefaults.LONG_PRESS_MS + 50)
        lift(0, KeyCodes.CTRL)
        assertEquals(ShiftState.LOCKED, controller.modifiers.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun rollingChordKeepsModifierForKeyAlreadyDown() {
        press(0, KeyCodes.CTRL)
        press(1, 'c'.code)
        lift(0, KeyCodes.CTRL) // modifier lifts first
        assertTrue(controller.isModifierActive(key(KeyCodes.CTRL)))
        lift(1, 'c'.code)
        assertEquals(listOf(ctrl('c')), host.strokes)
        assertFalse(controller.isModifierActive(key(KeyCodes.CTRL)))
    }

    @Test
    fun heldShiftUppercasesWithoutConsumingAndLeavesShiftOff() {
        press(0, KeyCodes.SHIFT)
        tap(1, 'c'.code)
        tap(1, 'd'.code)
        lift(0, KeyCodes.SHIFT)
        tap(1, 'e'.code)
        assertEquals(listOf('C'.code, 'D'.code, 'e'.code), host.keys)
        assertEquals(ShiftState.OFF, controller.modifiers.shift)
    }

    @Test
    fun heldShiftWithCtrlSendsShiftedStroke() {
        press(0, KeyCodes.SHIFT)
        press(1, KeyCodes.CTRL)
        tap(2, 'c'.code)
        lift(1, KeyCodes.CTRL)
        lift(0, KeyCodes.SHIFT)
        val stroke = host.strokes.single()
        assertEquals(setOf(StickyModifier.CTRL), stroke.modifiers)
        assertTrue(stroke.shift)
        assertEquals(ShiftState.OFF, controller.modifiers.shift)
        assertEquals(ShiftState.OFF, controller.modifiers.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun heldCtrlPlusRepeatingKeySendsStrokesAndKeepsCtrlHeld() {
        press(0, KeyCodes.CTRL)
        press(1, KeyCodes.DELETE)
        lift(1, KeyCodes.DELETE)
        assertEquals(KeyCodes.DELETE, host.strokes.single().code)
        assertEquals(setOf(StickyModifier.CTRL), host.strokes.single().modifiers)
        assertTrue(controller.isModifierActive(key(KeyCodes.CTRL)))
        lift(0, KeyCodes.CTRL)
    }

    @Test
    fun heldCtrlAStillGoesThroughTheCtrlAGuard() {
        press(0, KeyCodes.CTRL)
        tap(1, 'a'.code)
        lift(0, KeyCodes.CTRL)
        val stroke = host.strokes.single()
        assertEquals(ctrl('a'), stroke)
        // Same stroke as one-shot Ctrl, so the service's planner guard applies.
        assertEquals(KeyStrokePlan.Blocked, KeyStrokePlanner.plan(stroke, allowCtrlA = false))
    }

    @Test
    fun swipeIsNotStartedByAChordedFinger() {
        val a = key('a')
        val d = key('d')
        press(0, KeyCodes.SHIFT)
        controller.down(1, a.slot.centerX, a.slot.centerY)
        controller.move(1, key('c').slot.centerX, key('c').slot.centerY)
        controller.move(1, d.slot.centerX, d.slot.centerY)
        controller.up(1, d.slot.centerX, d.slot.centerY)
        lift(0, KeyCodes.SHIFT)
        assertTrue(host.words.isEmpty())
        assertEquals(listOf('D'.code), host.keys)
    }

    @Test
    fun singleFingerSwipeStillTypesAWord() {
        val a = key('a')
        val d = key('d')
        controller.down(0, a.slot.centerX, a.slot.centerY)
        controller.move(0, key('c').slot.centerX, key('c').slot.centerY)
        controller.move(0, d.slot.centerX, d.slot.centerY)
        controller.up(0, d.slot.centerX, d.slot.centerY)
        assertEquals(listOf("acd"), host.words)
    }

    @Test
    fun secondFingerDuringALetterSwipeIsIgnored() {
        val a = key('a')
        val d = key('d')
        controller.down(0, a.slot.centerX, a.slot.centerY)
        controller.move(0, key('c').slot.centerX, key('c').slot.centerY)
        tap(1, 'e'.code)
        controller.move(0, d.slot.centerX, d.slot.centerY)
        controller.up(0, d.slot.centerX, d.slot.centerY)
        assertEquals(listOf("acd"), host.words)
        assertTrue(host.keys.isEmpty())
    }

    @Test
    fun secondFingerOnALetterHeldByTheFirstIsIgnored() {
        press(0, 'c'.code)
        tap(1, 'd'.code)
        lift(0, 'c'.code)
        assertEquals(listOf('c'.code), host.keys)
    }

    @Test
    fun chordingOffIgnoresTheSecondFinger() {
        controller.chordingEnabled = false
        press(0, KeyCodes.CTRL)
        tap(1, 'c'.code)
        lift(0, KeyCodes.CTRL)
        assertTrue(host.strokes.isEmpty())
        assertTrue(host.keys.isEmpty())
        assertEquals(ShiftState.ON, controller.modifiers.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun cancelReleasesHeldModifiers() {
        press(0, KeyCodes.CTRL)
        press(1, 'c'.code)
        controller.cancel()
        assertFalse(controller.isModifierActive(key(KeyCodes.CTRL)))
        assertTrue(host.strokes.isEmpty())
        assertTrue(controller.touch.heldChordKeys.isEmpty())
    }

    @Test
    fun secondFingerOffTheKeysDoesNotStartAChord() {
        press(0, KeyCodes.CTRL)
        controller.down(1, -50f, -50f)
        controller.up(1, -50f, -50f)
        lift(0, KeyCodes.CTRL)
        assertEquals(ShiftState.ON, controller.modifiers.stickyState(StickyModifier.CTRL))
    }
}
