package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyboardControllerTest {

    private lateinit var scheduler: ManualScheduler
    private lateinit var host: RecordingHost
    private lateinit var controller: KeyboardController

    private class RecordingHost : KeyboardController.Host {
        val keys = mutableListOf<Int>()
        val longPresses = mutableListOf<Pair<Int, String>>()
        override fun requestRedraw() {}
        override fun onKey(code: Int) { keys += code }
        override fun onLongPress(code: Int, popupCharacters: String) {
            longPresses += code to popupCharacters
        }
    }

    @Before
    fun setUp() {
        scheduler = ManualScheduler()
        host = RecordingHost()
        controller = KeyboardController(scheduler = scheduler, host = host)
        controller.resize(
            widthPx = 1000f,
            heightPx = 400f,
            orientation = LayoutOrientation.PORTRAIT,
            paddingHorizontalPx = 0f,
            paddingVerticalPx = 0f,
            gapPx = 0f,
        )
    }

    private fun key(label: String): PlacedKey =
        controller.geometry.placedKeys.first { it.spec.label.equals(label, ignoreCase = true) }

    private fun tap(label: String) {
        val k = key(label)
        controller.down(0, k.slot.centerX, k.slot.centerY)
        controller.up(0, k.slot.centerX, k.slot.centerY)
    }

    @Test
    fun tapLetterCommitsLowercase() {
        tap("h")
        tap("i")
        assertEquals(listOf('h'.code, 'i'.code), host.keys)
    }

    @Test
    fun shiftThenLetterCommitsUppercaseAndClearsShift() {
        tap("⇧")
        assertEquals(ShiftState.ON, controller.modifiers.shift)
        tap("a")
        assertEquals(listOf('A'.code), host.keys)
        assertEquals(ShiftState.OFF, controller.modifiers.shift)
    }

    @Test
    fun doubleTapShiftLocksCaps() {
        tap("⇧")
        tap("⇧")
        assertEquals(ShiftState.LOCKED, controller.modifiers.shift)
        tap("b")
        tap("c")
        assertEquals(listOf('B'.code, 'C'.code), host.keys)
        assertEquals(ShiftState.LOCKED, controller.modifiers.shift)
    }

    @Test
    fun longPressShiftLocksCapsWithoutAToggleTap() {
        val shift = key("⇧")
        controller.down(0, shift.slot.centerX, shift.slot.centerY)
        scheduler.advance(400)
        controller.up(0, shift.slot.centerX, shift.slot.centerY)
        assertEquals(ShiftState.LOCKED, controller.modifiers.shift)
        assertTrue(host.keys.isEmpty())
    }

    @Test
    fun modeChangeSwapsToSymbolsAndBack() {
        tap("?123")
        assertEquals(KeyboardLayer.SYMBOLS, controller.modifiers.layer)
        assertTrue(controller.geometry.placedKeys.any { it.spec.label == "1" })
        assertTrue(controller.geometry.placedKeys.any { it.spec.label == "@" })
        tap("ABC")
        assertEquals(KeyboardLayer.LETTERS, controller.modifiers.layer)
        assertTrue(controller.geometry.placedKeys.any { it.spec.label == "q" })
    }

    @Test
    fun letterLongPressExposesPopupCharactersWithoutTypingBaseCharacter() {
        val e = key("e")
        controller.down(0, e.slot.centerX, e.slot.centerY)
        scheduler.advance(400)
        controller.up(0, e.slot.centerX, e.slot.centerY)
        assertEquals(1, host.longPresses.size)
        assertEquals('e'.code, host.longPresses[0].first)
        assertTrue(host.longPresses[0].second.contains("é"))
        assertTrue(host.keys.isEmpty())
    }

    @Test
    fun popupCharacterCommitsSelectionAndConsumesShift() {
        tap("⇧")
        controller.commitPopupCharacter('é')
        assertEquals(listOf('É'.code), host.keys)
        assertEquals(ShiftState.OFF, controller.modifiers.shift)
    }

    @Test
    fun landscapeResizeUsesNumberRow() {
        controller.resize(
            widthPx = 1600f,
            heightPx = 320f,
            orientation = LayoutOrientation.LANDSCAPE,
            paddingHorizontalPx = 0f,
            paddingVerticalPx = 0f,
            gapPx = 0f,
        )
        assertEquals(LayoutOrientation.LANDSCAPE, controller.orientation)
        assertEquals(5, controller.geometry.spec.rows.size)
        assertTrue(controller.geometry.placedKeys.any { it.spec.label == "5" })
        tap("5")
        assertEquals(listOf('5'.code), host.keys)
    }

    @Test
    fun deleteRepeatsWhileHeld() {
        val del = controller.geometry.placedKeys.first { it.spec.code == KeyCodes.DELETE }
        controller.down(0, del.slot.centerX, del.slot.centerY)
        scheduler.advance(500)
        controller.up(0, del.slot.centerX, del.slot.centerY)
        assertTrue(host.keys.count { it == KeyCodes.DELETE } >= 3)
    }
}
