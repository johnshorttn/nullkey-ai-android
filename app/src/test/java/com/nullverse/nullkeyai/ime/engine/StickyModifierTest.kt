package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickyModifierTest {

    @Test
    fun tapIsOneShotAndConsumedByTheNextKey() {
        val m = ModifierController()
        m.onStickyTap(StickyModifier.CTRL, nowMs = 0)
        assertEquals(ShiftState.ON, m.stickyState(StickyModifier.CTRL))
        assertEquals(setOf(StickyModifier.CTRL), m.activeStickyModifiers())
        m.consumeOneShot()
        assertEquals(ShiftState.OFF, m.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun doubleTapLocksAndSurvivesKeys() {
        val m = ModifierController()
        m.onStickyTap(StickyModifier.ALT, nowMs = 0)
        m.onStickyTap(StickyModifier.ALT, nowMs = 100)
        assertEquals(ShiftState.LOCKED, m.stickyState(StickyModifier.ALT))
        m.consumeOneShot()
        assertEquals(ShiftState.LOCKED, m.stickyState(StickyModifier.ALT))
        m.onStickyTap(StickyModifier.ALT, nowMs = 2_000)
        assertEquals(ShiftState.OFF, m.stickyState(StickyModifier.ALT))
    }

    @Test
    fun slowSecondTapTurnsOneShotOff() {
        val m = ModifierController()
        m.onStickyTap(StickyModifier.META, nowMs = 0)
        m.onStickyTap(StickyModifier.META, nowMs = 1_000)
        assertEquals(ShiftState.OFF, m.stickyState(StickyModifier.META))
    }

    @Test
    fun longPressLocks() {
        val m = ModifierController()
        m.onStickyLongPress(StickyModifier.CTRL)
        assertEquals(ShiftState.LOCKED, m.stickyState(StickyModifier.CTRL))
    }

    @Test
    fun consumeAlsoClearsOneShotShiftButNotCapsLock() {
        val m = ModifierController()
        m.onShiftTap(0)
        m.consumeOneShot()
        assertEquals(ShiftState.OFF, m.shift)
        m.onShiftLongPress()
        m.consumeOneShot()
        assertEquals(ShiftState.LOCKED, m.shift)
    }

    @Test
    fun lockedModifierLabelIsUppercaseAndResetClearsAll() {
        val m = ModifierController()
        val ctrl = TerminalKeys.spec(KeyCodes.CTRL)
        assertEquals("Ctrl", m.labelFor(ctrl))
        m.onStickyLongPress(StickyModifier.CTRL)
        assertEquals("CTRL", m.labelFor(ctrl))
        m.onStickyTap(StickyModifier.ALT, 0)
        m.reset()
        assertTrue(m.activeStickyModifiers().isEmpty())
    }
}
