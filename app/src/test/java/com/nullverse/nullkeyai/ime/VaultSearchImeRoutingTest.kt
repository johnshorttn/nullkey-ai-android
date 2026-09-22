package com.nullverse.nullkeyai.ime

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.ime.engine.NullKeyKeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The vault search box is inside the IME window, so it never becomes the host
 * InputConnection. Tapping it must make later NullKey keys edit that field.
 */
@RunWith(RobolectricTestRunner::class)
class VaultSearchImeRoutingTest {

    @Test
    fun keysBeforeSearchStayOutOfTheField() {
        val (search, keyboard) = keyboard()
        tapKey(keyboard, "q")
        assertEquals("", search.text.toString())
    }

    @Test
    fun tappingSearchThenTypingUpdatesTheQuery() {
        val (search, keyboard) = keyboard()
        tapSearch(search)
        tapKey(keyboard, "q")
        tapKey(keyboard, "w")
        assertEquals("qw", search.text.toString())
    }

    @Test
    fun deleteAndSpaceEditTheQuery() {
        val (search, keyboard) = keyboard()
        tapSearch(search)
        tapKey(keyboard, "q")
        tapKey(keyboard, "w")
        tapKey(keyboard, "space")
        tapKey(keyboard, "e")
        tapDelete(keyboard)
        assertEquals("qw ", search.text.toString())
    }

    @Test
    fun enterLeavesSearchModeSoLaterKeysDoNotChangeTheQuery() {
        val (search, keyboard) = keyboard()
        tapSearch(search)
        tapKey(keyboard, "q")
        tapKey(keyboard, "enter")
        tapKey(keyboard, "w")
        assertEquals("q", search.text.toString())
        assertTrue(!search.isActivated)
    }

    private fun keyboard(): Pair<EditText, NullKeyKeyboardView> {
        val service = Robolectric.buildService(NullKeyImeService::class.java).create().get()
        val root = service.onCreateInputView()
        val search = root.findViewById<EditText>(R.id.clip_search)
        val keyboard = layoutKeyboard(root.findViewById(R.id.keyboard_engine_view))
        return search to keyboard
    }

    private fun layoutKeyboard(view: NullKeyKeyboardView): NullKeyKeyboardView {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun tapSearch(search: EditText) {
        val downTime = SystemClock.uptimeMillis()
        search.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 8f, 8f, 0),
        )
    }

    private fun tapKey(view: NullKeyKeyboardView, label: String) {
        val key = view.keyWithLabel(label)
        val downTime = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, key.slot.centerX, key.slot.centerY, 0),
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_UP, key.slot.centerX, key.slot.centerY, 0),
        )
    }

    private fun tapDelete(view: NullKeyKeyboardView) {
        val key = view.controller.geometry.placedKeys.first { it.spec.label == "⌫" }
        val downTime = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, key.slot.centerX, key.slot.centerY, 0),
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_UP, key.slot.centerX, key.slot.centerY, 0),
        )
    }
}
