package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierControllerTest {

    @Test
    fun shiftTapTurnsOnThenOff() {
        val mods = ModifierController()
        mods.onShiftTap(0L)
        assertEquals(ShiftState.ON, mods.shift)
        assertTrue(mods.isShifted)
        mods.onShiftTap(1_000L)
        assertEquals(ShiftState.OFF, mods.shift)
    }

    @Test
    fun doubleTapLocksCaps() {
        val mods = ModifierController()
        mods.onShiftTap(0L)
        mods.onShiftTap(120L)
        assertEquals(ShiftState.LOCKED, mods.shift)
        assertTrue(mods.isCapsLock)
    }

    @Test
    fun longPressLocksCaps() {
        val mods = ModifierController()
        mods.onShiftLongPress()
        assertEquals(ShiftState.LOCKED, mods.shift)
        mods.onShiftTap(50L)
        assertEquals(ShiftState.OFF, mods.shift)
    }

    @Test
    fun oneShotShiftConsumesAfterLetter() {
        val mods = ModifierController()
        mods.onShiftTap(0L)
        assertEquals('A'.code, mods.applyToCode('a'.code))
        mods.onLetterCommitted()
        assertEquals(ShiftState.OFF, mods.shift)
        assertEquals('a'.code, mods.applyToCode('a'.code))
    }

    @Test
    fun capsLockSurvivesLetters() {
        val mods = ModifierController()
        mods.onShiftLongPress()
        mods.onLetterCommitted()
        assertEquals(ShiftState.LOCKED, mods.shift)
        assertEquals('Z'.code, mods.applyToCode('z'.code))
    }

    @Test
    fun applyToCodeLeavesNonLettersAlone() {
        val mods = ModifierController()
        mods.onShiftTap(0L)
        assertEquals('.'.code, mods.applyToCode('.'.code))
        assertEquals(KeyCodes.DELETE, mods.applyToCode(KeyCodes.DELETE))
    }

    @Test
    fun layerToggleSwitchesLabels() {
        val mods = ModifierController()
        val mode = KeySpec(KeyCodes.MODE_CHANGE, "?123", isModifier = true)
        assertEquals("?123", mods.labelFor(mode))
        mods.toggleLayer()
        assertEquals(KeyboardLayer.SYMBOLS, mods.layer)
        assertEquals("ABC", mods.labelFor(mode))
    }

    @Test
    fun shiftLabelShowsCapsLockGlyph() {
        val mods = ModifierController()
        val shift = KeySpec(KeyCodes.SHIFT, "⇧", shiftedLabel = "⇪", isModifier = true)
        assertEquals("⇧", mods.labelFor(shift))
        mods.onShiftLongPress()
        assertEquals("⇪", mods.labelFor(shift))
    }

    @Test
    fun resetClearsShiftAndLayer() {
        val mods = ModifierController()
        mods.onShiftLongPress()
        mods.toggleLayer()
        mods.reset()
        assertEquals(ShiftState.OFF, mods.shift)
        assertEquals(KeyboardLayer.LETTERS, mods.layer)
        assertFalse(mods.isShifted)
    }

    @Test
    fun heldModifiersApplyWithoutChangingTapState() {
        val mods = ModifierController()
        mods.holdSticky(StickyModifier.CTRL)
        mods.holdShift()
        assertTrue(mods.isShifted)
        assertTrue(mods.isStickyActive(StickyModifier.CTRL))
        assertEquals(setOf(StickyModifier.CTRL), mods.activeStickyModifiers())
        assertEquals(ShiftState.OFF, mods.shift)
        assertEquals(ShiftState.OFF, mods.stickyState(StickyModifier.CTRL))
        // Typing keys does not clear a held modifier.
        mods.consumeOneShot()
        mods.onLetterCommitted()
        assertTrue(mods.isShifted)
        assertTrue(mods.isStickyHeld(StickyModifier.CTRL))
        mods.releaseShift()
        mods.releaseSticky(StickyModifier.CTRL)
        assertFalse(mods.isShifted)
        assertFalse(mods.isAnyHeld)
        assertTrue(mods.activeStickyModifiers().isEmpty())
    }

    @Test
    fun releasingAHeldModifierKeepsOneShotAndLock() {
        val mods = ModifierController()
        mods.onStickyTap(StickyModifier.ALT, nowMs = 0)
        mods.onShiftLongPress()
        mods.holdSticky(StickyModifier.ALT)
        mods.holdShift()
        mods.releaseSticky(StickyModifier.ALT)
        mods.releaseShift()
        assertEquals(ShiftState.ON, mods.stickyState(StickyModifier.ALT))
        assertEquals(ShiftState.LOCKED, mods.shift)
    }

    @Test
    fun restoreUndoesALongPressLockAndResetDropsHeld() {
        val mods = ModifierController()
        mods.onStickyLongPress(StickyModifier.META)
        mods.restoreSticky(StickyModifier.META, ShiftState.OFF)
        assertEquals(ShiftState.OFF, mods.stickyState(StickyModifier.META))
        mods.onShiftLongPress()
        mods.restoreShift(ShiftState.ON)
        assertEquals(ShiftState.ON, mods.shift)
        mods.holdSticky(StickyModifier.CTRL)
        mods.reset()
        assertFalse(mods.isAnyHeld)
    }
}
