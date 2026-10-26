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
import com.nullverse.nullkeyai.ime.engine.TrackpadPointer
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
        assertEquals(View.VISIBLE, search.rootView.findViewById<View>(R.id.vault_panel).visibility)
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

    @Test
    fun spaceDragMovesTheVaultSearchCursorAndClamps() {
        val (_, search, keyboard) = keyboard()
        tapSearch(search)
        dragSpace(keyboard, -3)
        dragSpace(keyboard, 2)
        assertEquals("", search.text.toString())
        assertEquals(0, search.selectionStart)

        listOf("h", "e", "l", "l", "o").forEach { tapKey(keyboard, it) }
        assertEquals("hello", search.text.toString())
        assertEquals(5, search.selectionStart)

        dragSpace(keyboard, -2)
        assertEquals("hello", search.text.toString())
        assertEquals(3, search.selectionStart)

        dragSpace(keyboard, -10)
        assertEquals(0, search.selectionStart)
        assertEquals("hello", search.text.toString())

        dragSpace(keyboard, 10)
        assertEquals(5, search.selectionStart)
        assertEquals("hello", search.text.toString())
    }

    @Test
    fun vaultStaysHiddenUntilToolsOpensItAndCloseStopsSearchKeys() {
        val (_, search, keyboard) = keyboard()
        val root = search.rootView
        val panel = root.findViewById<View>(R.id.vault_panel)
        val tools = root.findViewById<android.widget.ImageButton>(R.id.keyboard_tools)
        assertEquals(View.GONE, panel.visibility)
        tapKey(keyboard, "q")
        assertEquals("", search.text.toString())

        tools.performClick()
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(search.context.getString(R.string.keyboard_tools_close), tools.contentDescription.toString())

        tapSearch(search)
        tapKey(keyboard, "q")
        tapKey(keyboard, "w")
        tapDelete(keyboard)
        assertEquals("q", search.text.toString())

        tools.performClick()
        assertEquals(View.GONE, panel.visibility)
        assertEquals(search.context.getString(R.string.keyboard_tools_open), tools.contentDescription.toString())
        tapKey(keyboard, "e")
        assertEquals("q", search.text.toString())
    }

    @Test
    fun trackpadReplacesKeysAndBackOrToggleReturnsToKeys() {
        val (_, search, keyboard) = keyboard()
        val root = search.rootView
        val panel = root.findViewById<View>(R.id.vault_panel)
        val surface = root.findViewById<View>(R.id.keyboard_trackpad_surface)
        val trackpad = root.findViewById<View>(R.id.keyboard_trackpad)
        val tools = root.findViewById<View>(R.id.keyboard_tools)
        assertEquals(View.GONE, panel.visibility)
        assertEquals(View.GONE, surface.visibility)

        tools.performClick()
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(View.VISIBLE, keyboard.visibility)
        trackpad.performClick()
        assertEquals(View.VISIBLE, surface.visibility)
        assertEquals(View.GONE, keyboard.visibility)
        assertEquals(search.context.getString(R.string.keyboard_trackpad_exit), trackpad.contentDescription.toString())

        trackpad.performClick()
        assertEquals(View.GONE, panel.visibility)
        assertEquals(View.GONE, surface.visibility)
        assertEquals(View.VISIBLE, keyboard.visibility)

        tools.performClick()
        trackpad.performClick()
        tools.performClick()
        assertEquals(View.GONE, panel.visibility)
        assertEquals(View.GONE, surface.visibility)
        assertEquals(View.VISIBLE, keyboard.visibility)
        assertEquals(search.context.getString(R.string.keyboard_tools_open), tools.contentDescription.toString())
    }

    @Test
    fun trackpadDragMovesTheCursorAndDoesNotType() {
        val (_, search, keyboard) = keyboard()
        val root = search.rootView
        tapSearch(search)
        listOf("h", "e", "l", "l", "o").forEach { tapKey(keyboard, it) }
        assertEquals("hello", search.text.toString())
        root.findViewById<View>(R.id.keyboard_trackpad).performClick()

        tapKey(keyboard, "q")
        tapKey(keyboard, "space")
        assertEquals("hello", search.text.toString())
        assertEquals(5, search.selectionStart)

        val step = TrackpadPointer.STEP_X_DP * keyboard.resources.displayMetrics.density
        dragTrackpad(root.findViewById(R.id.keyboard_trackpad_surface), dx = -2f * step, dy = 0f)
        assertEquals("hello", search.text.toString())
        assertEquals(3, search.selectionStart)

        dragTrackpad(root.findViewById(R.id.keyboard_trackpad_surface), dx = 0f, dy = step * 4f)
        assertEquals("hello", search.text.toString())
        assertEquals(3, search.selectionStart)

        dragTrackpad(root.findViewById(R.id.keyboard_trackpad_surface), dx = -10f * step, dy = 0f)
        assertEquals(0, search.selectionStart)
        assertEquals("hello", search.text.toString())
    }

    @Test
    fun closingTheKeyboardHidesTheVaultPanel() {
        val (service, search, keyboard) = keyboard()
        search.rootView.findViewById<View>(R.id.keyboard_tools).performClick()
        tapSearch(search)
        tapKey(keyboard, "q")
        service.onFinishInputView(true)
        assertEquals(View.GONE, search.rootView.findViewById<View>(R.id.vault_panel).visibility)
        tapKey(keyboard, "w")
        assertEquals("q", search.text.toString())
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
        val root = search.rootView
        val panel = root.findViewById<View>(R.id.vault_panel)
        if (panel.visibility != View.VISIBLE) {
            root.findViewById<View>(R.id.keyboard_tools).performClick()
        }
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

    private fun dragTrackpad(surface: View, dx: Float, dy: Float) {
        val downTime = SystemClock.uptimeMillis()
        surface.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 40f, 40f, 0),
        )
        surface.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_MOVE, 40f + dx, 40f + dy, 0),
        )
        surface.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, 40f + dx, 40f + dy, 0),
        )
    }

    private fun dragSpace(view: NullKeyKeyboardView, steps: Int) {
        val space = view.keyWithLabel("space")
        val step = space.slot.height
        val startX = space.slot.centerX
        val y = space.slot.centerY
        val endX = startX + steps * step
        val downTime = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, y, 0),
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_MOVE, endX, y, 0),
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, endX, y, 0),
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
