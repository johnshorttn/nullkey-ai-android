package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TouchEngineTest {

    private lateinit var scheduler: ManualScheduler
    private lateinit var geometry: KeyboardGeometry
    private lateinit var recorder: Recorder
    private lateinit var engine: TouchEngine

    private class Recorder : TouchEngine.Listener {
        lateinit var geometry: KeyboardGeometry
        val presses = mutableListOf<String>()
        val releases = mutableListOf<String?>()
        val taps = mutableListOf<String>()
        val longPresses = mutableListOf<String>()
        val repeats = mutableListOf<String>()
        val gestures = mutableListOf<List<String>>()
        val gestureProgress = mutableListOf<List<String>>()
        var consumeLongPress = false

        override fun hitTest(x: Float, y: Float): PlacedKey? = geometry.hitTest(x, y)
        override fun onPress(key: PlacedKey) { presses += key.spec.label }
        override fun onRelease(key: PlacedKey?) { releases += key?.spec?.label }
        override fun onTap(key: PlacedKey) { taps += key.spec.label }
        override fun onLongPress(key: PlacedKey): Boolean {
            longPresses += key.spec.label
            return consumeLongPress
        }
        override fun onRepeat(key: PlacedKey) { repeats += key.spec.label }
        override fun onGesturePath(keys: List<PlacedKey>) {
            gestures += keys.map { it.spec.label }
        }
        override fun onGestureProgress(keys: List<PlacedKey>) {
            gestureProgress += keys.map { it.spec.label }
        }
    }

    @Before
    fun setUp() {
        scheduler = ManualScheduler()
        geometry = KeyboardGeometry.place(
            spec = DefaultKeyboardLayoutProvider.LETTERS_PORTRAIT,
            orientation = LayoutOrientation.PORTRAIT,
            widthPx = 1000f,
            heightPx = 400f,
            paddingHorizontalPx = 0f,
            paddingVerticalPx = 0f,
            gapPx = 0f,
        )
        recorder = Recorder().also { it.geometry = geometry }
        engine = TouchEngine(scheduler, listener = recorder)
    }

    private fun key(label: String): PlacedKey =
        geometry.placedKeys.first { it.spec.label.equals(label, ignoreCase = true) }

    @Test
    fun tapEmitsOnRelease() {
        val q = key("q")
        engine.down(1, q.slot.centerX, q.slot.centerY)
        assertEquals(listOf("q"), recorder.presses)
        assertTrue(recorder.taps.isEmpty())
        engine.up(1, q.slot.centerX, q.slot.centerY)
        assertEquals(listOf("q"), recorder.taps)
    }

    @Test
    fun slideChangesPressedKeyAndTapsTheReleaseKey() {
        val q = key("q")
        val w = key("w")
        engine.down(1, q.slot.centerX, q.slot.centerY)
        engine.move(1, w.slot.centerX, w.slot.centerY)
        engine.up(1, w.slot.centerX, w.slot.centerY)
        assertEquals(listOf("q", "w"), recorder.presses)
        assertTrue(recorder.taps.isEmpty())
        assertEquals(listOf(listOf("q", "w")), recorder.gestures)
    }

    @Test
    fun smallNeighborSlipDoesNotBecomeGesture() {
        val q = key("q")
        val w = key("w")
        val boundaryX = q.slot.right + 1f
        engine.down(1, q.slot.right - 1f, q.slot.centerY)
        engine.move(1, boundaryX, q.slot.centerY)
        engine.up(1, boundaryX, q.slot.centerY)
        assertTrue(recorder.gestures.isEmpty())
        assertEquals(listOf("w"), recorder.taps)
    }

    @Test
    fun gesturePathDoesNotDuplicateSameKeyMoves() {
        val q = key("q")
        val w = key("w")
        engine.down(1, q.slot.centerX, q.slot.centerY)
        engine.move(1, q.slot.centerX + 1f, q.slot.centerY)
        engine.move(1, w.slot.centerX, w.slot.centerY)
        engine.move(1, w.slot.centerX + 1f, w.slot.centerY)
        engine.up(1, w.slot.centerX, w.slot.centerY)
        assertEquals(listOf(listOf("q", "w")), recorder.gestures)
    }

    @Test
    fun gestureProgressTracksVisitedKeysAndClearsOnRelease() {
        val q = key("q")
        val w = key("w")
        val e = key("e")
        engine.down(1, q.slot.centerX, q.slot.centerY)
        engine.move(1, w.slot.centerX, w.slot.centerY)
        engine.move(1, e.slot.centerX, e.slot.centerY)
        assertTrue(recorder.gestureProgress.contains(listOf("q", "w")))
        assertTrue(recorder.gestureProgress.contains(listOf("q", "w", "e")))
        engine.up(1, e.slot.centerX, e.slot.centerY)
        assertEquals(emptyList<String>(), recorder.gestureProgress.last())
    }

    @Test
    fun swipeAcrossLettersCancelsLongPressOnVisitedKey() {
        val e = key("e")
        val r = key("r")
        engine.down(1, e.slot.centerX, e.slot.centerY)
        engine.move(1, r.slot.centerX, r.slot.centerY)
        scheduler.advance(800)
        assertTrue(recorder.longPresses.isEmpty())
        engine.up(1, r.slot.centerX, r.slot.centerY)
        assertEquals(listOf(listOf("e", "r")), recorder.gestures)
    }

    @Test
    fun longPressStillWorksBeforeSwipeBegins() {
        val e = key("e")
        engine.down(1, e.slot.centerX, e.slot.centerY)
        scheduler.advance(399)
        assertTrue(recorder.longPresses.isEmpty())
        scheduler.advance(1)
        assertEquals(listOf("e"), recorder.longPresses)
    }

    @Test
    fun consumedLongPressSuppressesTap() {
        recorder.consumeLongPress = true
        val shift = key("⇧")
        engine.down(1, shift.slot.centerX, shift.slot.centerY)
        scheduler.advance(400)
        engine.up(1, shift.slot.centerX, shift.slot.centerY)
        assertEquals(listOf("⇧"), recorder.longPresses)
        assertTrue(recorder.taps.isEmpty())
    }

    @Test
    fun unconsumedLongPressStillTapsOnRelease() {
        val e = key("e")
        engine.down(1, e.slot.centerX, e.slot.centerY)
        scheduler.advance(400)
        engine.up(1, e.slot.centerX, e.slot.centerY)
        assertEquals(listOf("e"), recorder.longPresses)
        assertEquals(listOf("e"), recorder.taps)
    }

    @Test
    fun repeatableKeyFiresImmediatelyThenRepeats() {
        val del = geometry.placedKeys.first { it.spec.code == KeyCodes.DELETE }
        engine.down(1, del.slot.centerX, del.slot.centerY)
        assertEquals(1, recorder.repeats.size)
        scheduler.advance(400)
        assertEquals(2, recorder.repeats.size)
        scheduler.advance(50)
        assertEquals(3, recorder.repeats.size)
        engine.up(1, del.slot.centerX, del.slot.centerY)
        scheduler.advance(200)
        assertEquals(3, recorder.repeats.size)
        assertTrue(recorder.taps.isEmpty())
    }

    @Test
    fun secondPointerIsIgnored() {
        val q = key("q")
        val w = key("w")
        engine.down(1, q.slot.centerX, q.slot.centerY)
        engine.down(2, w.slot.centerX, w.slot.centerY)
        engine.up(2, w.slot.centerX, w.slot.centerY)
        assertEquals(1, recorder.presses.size)
        engine.up(1, q.slot.centerX, q.slot.centerY)
        assertEquals(listOf("q"), recorder.taps)
    }

    @Test
    fun downOutsideKeyboardDoesNotCapturePointer() {
        engine.down(1, -100f, -100f)
        assertNull(engine.activePointerId)
        assertNull(engine.pressed)

        val q = key("q")
        engine.down(2, q.slot.centerX, q.slot.centerY)
        engine.up(2, q.slot.centerX, q.slot.centerY)
        assertEquals(listOf("q"), recorder.taps)
    }

    @Test
    fun cancelDropsThePressWithoutATap() {
        val q = key("q")
        engine.down(1, q.slot.centerX, q.slot.centerY)
        engine.cancel()
        assertTrue(recorder.taps.isEmpty())
        assertNull(engine.pressed)
        assertNull(engine.activePointerId)
    }
}
