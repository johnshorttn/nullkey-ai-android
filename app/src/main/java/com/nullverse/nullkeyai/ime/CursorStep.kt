package com.nullverse.nullkeyai.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import kotlin.math.abs
import kotlin.math.min

/**
 * Moves a text cursor by whole characters and stays inside the buffer.
 * Editors that refuse to share their text (some password fields) are nudged
 * with arrow keys, which clamp on their own.
 */
internal object CursorStep {
    const val READ_LIMIT = 10_000

    fun nextIndex(length: Int, selectionStart: Int, selectionEnd: Int, delta: Int): Int {
        val safeLength = length.coerceAtLeast(0)
        val rawStart = if (selectionStart < 0) safeLength else selectionStart
        val rawEnd = if (selectionEnd < 0) safeLength else selectionEnd
        val start = rawStart.coerceIn(0, safeLength)
        val end = rawEnd.coerceIn(0, safeLength)
        val anchor = if (delta < 0) min(start, end) else kotlin.math.max(start, end)
        return (anchor + delta).coerceIn(0, safeLength)
    }

    fun apply(connection: CursorConnection, delta: Int) {
        if (delta == 0) return
        val snapshot = runCatching { connection.read() }.getOrNull()
        if (snapshot == null || snapshot.startOffset < 0) {
            nudge(connection, delta)
            return
        }
        val relative = nextIndex(
            snapshot.length,
            snapshot.selectionStart,
            snapshot.selectionEnd,
            delta,
        )
        runCatching { connection.setCursor(snapshot.startOffset + relative) }
    }

    private fun nudge(connection: CursorConnection, delta: Int) {
        val left = delta < 0
        repeat(abs(delta)) {
            if (runCatching { connection.sendArrow(left) }.isFailure) return
        }
    }
}

internal data class CursorSnapshot(
    val length: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val startOffset: Int = 0,
)

internal interface CursorConnection {
    fun read(): CursorSnapshot?
    fun setCursor(index: Int)
    fun sendArrow(left: Boolean)
}

internal class InputConnectionCursor(
    private val connection: InputConnection,
    private val allowRead: Boolean,
) : CursorConnection {
    override fun read(): CursorSnapshot? {
        if (!allowRead) return null
        val before = connection.getTextBeforeCursor(CursorStep.READ_LIMIT, 0) ?: return null
        val after = connection.getTextAfterCursor(CursorStep.READ_LIMIT, 0) ?: return null
        if (before.length >= CursorStep.READ_LIMIT || after.length >= CursorStep.READ_LIMIT) {
            return null
        }
        val selectedLength = connection.getSelectedText(0)?.length ?: 0
        return CursorSnapshot(
            length = before.length + selectedLength + after.length,
            selectionStart = before.length,
            selectionEnd = before.length + selectedLength,
        )
    }

    override fun setCursor(index: Int) {
        connection.setSelection(index, index)
    }

    override fun sendArrow(left: Boolean) {
        val code = if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
