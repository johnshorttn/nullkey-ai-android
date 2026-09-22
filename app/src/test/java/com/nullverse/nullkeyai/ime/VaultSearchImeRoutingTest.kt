package com.nullverse.nullkeyai.ime

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
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
 * The vault search field accepts clipboard paste and cut on its own buffer.
 * NullKey commit and backspace must edit that same buffer, including after an
 * input restart, because InputConnection commit/delete does not reach it.
 */
@RunWith(RobolectricTestRunner::class)
class VaultSearchImeRoutingTest {

    @Test
    fun keysBeforeSearchStayOutOfTheField() {
        val (_, search, keyboard) = keyboard()
        tapKey(keyboard, "q")
        assertEquals("", search.text.toString())
    }

    @Test
    fun tappingSearchThenTypingUpdatesTheQuery() {
        val (_, search, keyboard) = keyboard()
        tapSearch(search)
        tapKey(keyboard, "q")
        tapKey(keyboard, "w")
        assertEquals("qw", search.text.toString())
    }

    @Test
    fun deleteAndSpaceEditTheQuery() {
        val (_, search, keyboard) = keyboard()
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
        val (_, search, keyboard) = keyboard()
        tapSearch(search)
        tapKey(keyboard, "q")
        tapKey(keyboard, "enter")
        tapKey(keyboard, "w")
        assertEquals("q", search.text.toString())
        assertTrue(!search.isActivated)
    }

    @Test
    fun inputRestartWhileSearchIsFocusedStillAcceptsKeysAndBackspace() {
        val (service, search, keyboard) = keyboard()
        tapSearch(search)
        tapKey(keyboard, "a")
        tapKey(keyboard, "b")
        service.onFinishInputView(false)
        tapDelete(keyboard)
        assertEquals("a", search.text.toString())
        tapKey(keyboard, "c")
        assertEquals("ac", search.text.toString())
    }

    @Test
    fun focusedSearchStillReceivesKeysWhenTheModeFlagIsCleared() {
        val (service, search, keyboard) = keyboard()
        tapSearch(search)
        assertTrue(search.requestFocus())
        val flag = NullKeyImeService::class.java.getDeclaredField("vaultSearchActive")
        flag.isAccessible = true
        flag.setBoolean(service, false)
        tapKey(keyboard, "q")
        tapDelete(keyboard)
        assertEquals("", search.text.toString())
    }

    @Test
    fun backspaceDeletesOnePastedCharacterWithoutSelectAll() {
        val (_, search, keyboard) = keyboard()
        tapSearch(search)
        tapKey(keyboard, "a")
        tapKey(keyboard, "b")
        tapDelete(keyboard)
        assertEquals("a", search.text.toString())

        val clipboard = search.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("paste", "xy"))
        assertTrue(search.requestFocus())
        assertTrue(search.onTextContextMenuItem(android.R.id.paste))
        assertEquals("axy", search.text.toString())

        tapDelete(keyboard)
        assertEquals("ax", search.text.toString())
        tapDelete(keyboard)
        assertEquals("a", search.text.toString())
    }

    private fun keyboard(): Triple<NullKeyImeService, EditText, NullKeyKeyboardView> {
        val service = Robolectric.buildService(NullKeyImeService::class.java).create().get()
        val root = service.onCreateInputView()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(root)
        val search = root.findViewById<EditText>(R.id.clip_search)
        search.clearFocus()
        val keyboard = layoutKeyboard(root.findViewById(R.id.keyboard_engine_view))
        return Triple(service, search, keyboard)
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
