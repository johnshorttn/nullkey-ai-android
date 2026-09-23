package com.nullverse.nullkeyai.ime.engine

import com.nullverse.nullkeyai.ime.GestureWordRanker
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
        val gestures = mutableListOf<String>()
        val cursorSteps = mutableListOf<Int>()
        var pressFeedback = 0
        override fun requestRedraw() {}
        override fun onKey(code: Int) { keys += code }
        override fun onCursorSteps(steps: Int) { cursorSteps += steps }
        override fun onLongPress(code: Int, popupCharacters: String) {
            longPresses += code to popupCharacters
        }
        override fun onGestureWord(path: String) { gestures += path }
        override fun onPressFeedback() { pressFeedback += 1 }
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
        assertEquals(2, host.pressFeedback)
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
    fun shiftedSwipeCapitalizesAndConsumesOneShotShift() {
        tap("⇧")
        val h = key("h")
        val e = key("e")
        controller.down(0, h.slot.centerX, h.slot.centerY)
        controller.move(0, e.slot.centerX, e.slot.centerY)
        controller.up(0, e.slot.centerX, e.slot.centerY)
        val path = host.gestures.single()
        assertTrue(path.startsWith("H"))
        assertTrue(path.endsWith("e"))
        assertTrue(path.all { it.isLetter() })
        assertEquals(ShiftState.OFF, controller.modifiers.shift)
    }

    @Test
    fun capsLockRemainsActiveAfterSwipe() {
        tap("⇧")
        tap("⇧")
        val h = key("h")
        val e = key("e")
        controller.down(0, h.slot.centerX, h.slot.centerY)
        controller.move(0, e.slot.centerX, e.slot.centerY)
        controller.up(0, e.slot.centerX, e.slot.centerY)
        val path = host.gestures.single()
        assertTrue(path.startsWith("H"))
        assertTrue(path.endsWith("e"))
        assertTrue(path.all { it.isLetter() })
        assertEquals(ShiftState.LOCKED, controller.modifiers.shift)
    }

    @Test
    fun disablingSwipeTypingFallsBackToFinalKeyTap() {
        controller.swipeTypingEnabled = false
        val h = key("h")
        val e = key("e")
        controller.down(0, h.slot.centerX, h.slot.centerY)
        controller.move(0, e.slot.centerX, e.slot.centerY)
        controller.up(0, e.slot.centerX, e.slot.centerY)
        assertTrue(host.gestures.isEmpty())
        assertEquals(listOf('e'.code), host.keys)
    }

    @Test
    fun disablingSwipeTypingStillConsumesOneShotShiftOnFallbackTap() {
        controller.swipeTypingEnabled = false
        tap("⇧")
        val h = key("h")
        val e = key("e")
        controller.down(0, h.slot.centerX, h.slot.centerY)
        controller.move(0, e.slot.centerX, e.slot.centerY)
        controller.up(0, e.slot.centerX, e.slot.centerY)
        assertEquals(listOf('E'.code), host.keys)
        assertEquals(ShiftState.OFF, controller.modifiers.shift)
    }

    @Test
    fun slowDragAfterLongPressRanksHello() {
        val h = key("h")
        val e = key("e")
        val l = key("l")
        val o = key("o")
        controller.down(0, h.slot.centerX, h.slot.centerY)
        scheduler.advance(450)
        controller.move(0, e.slot.centerX, e.slot.centerY)
        controller.move(0, l.slot.centerX, l.slot.centerY)
        controller.up(0, o.slot.centerX, o.slot.centerY)
        val path = host.gestures.single()
        assertTrue(path.startsWith("h"))
        assertTrue(path.endsWith("o"))
        assertEquals("hello", GestureWordRanker.rank(path, seedLexicon(), 3).first())
        assertTrue(host.keys.isEmpty())
    }

    @Test
    fun flickFromHToORanksHello() {
        val h = key("h")
        val o = key("o")
        controller.down(0, h.slot.centerX, h.slot.centerY)
        controller.move(0, o.slot.centerX, o.slot.centerY)
        controller.up(0, o.slot.centerX, o.slot.centerY)
        val path = host.gestures.single()
        assertEquals("hjio", path)
        assertEquals("hello", GestureWordRanker.rank(path, seedLexicon(), 3).first())
    }

    @Test
    fun flickFromTToERanksThe() {
        val t = key("t")
        val e = key("e")
        controller.down(0, t.slot.centerX, t.slot.centerY)
        controller.move(0, e.slot.centerX, e.slot.centerY)
        controller.up(0, e.slot.centerX, e.slot.centerY)
        assertEquals("the", GestureWordRanker.rank(host.gestures.single(), seedLexicon(), 3).first())
    }

    @Test
    fun flickFromTToSRanksThis() {
        val t = key("t")
        val s = key("s")
        controller.down(0, t.slot.centerX, t.slot.centerY)
        controller.move(0, s.slot.centerX, s.slot.centerY)
        controller.up(0, s.slot.centerX, s.slot.centerY)
        assertEquals("this", GestureWordRanker.rank(host.gestures.single(), seedLexicon(), 3).first())
    }

    private fun seedLexicon(): Map<String, Int> = mapOf(
        "the" to 1, "there" to 1, "this" to 1, "thanks" to 1, "hello" to 1,
        "to" to 1, "two" to 1, "time" to 1, "take" to 1, "these" to 1,
    )

    @Test
    fun longPressThenSwipeDoesNotCommitGestureWord() {
        val e = key("e")
        val r = key("r")
        controller.down(0, e.slot.centerX, e.slot.centerY)
        scheduler.advance(400)
        controller.move(0, r.slot.centerX, r.slot.centerY)
        controller.up(0, r.slot.centerX, r.slot.centerY)
        assertEquals(1, host.longPresses.size)
        assertTrue(host.gestures.isEmpty())
        assertTrue(host.keys.isEmpty())
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

    @Test
    fun spaceDragReportsCursorStepsAndDoesNotCommitSpace() {
        val space = key("space")
        val step = space.slot.height
        controller.down(0, space.slot.centerX, space.slot.centerY)
        controller.move(0, space.slot.centerX - step, space.slot.centerY)
        controller.move(0, space.slot.centerX + step, space.slot.centerY)
        controller.up(0, space.slot.centerX + step, space.slot.centerY)
        assertEquals(listOf(-1, 2), host.cursorSteps)
        assertTrue(host.keys.none { it == KeyCodes.SPACE })
    }

    @Test
    fun configurableLongPressDelayIsHonored() {
        controller.applyTypingSettings(swipeTypingEnabled = true, longPressMs = 200L)
        val e = key("e")
        controller.down(0, e.slot.centerX, e.slot.centerY)
        scheduler.advance(199)
        assertTrue(host.longPresses.isEmpty())
        scheduler.advance(1)
        assertEquals(1, host.longPresses.size)
        assertEquals('e'.code, host.longPresses[0].first)
        controller.up(0, e.slot.centerX, e.slot.centerY)
        assertTrue(host.keys.isEmpty())
    }
}
