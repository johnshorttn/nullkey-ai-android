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

    /**
     * Horizontal steps are characters. Vertical steps are lines, keeping the
     * column when the line is long enough and stopping on a short line.
     * Positive [dy] moves toward the end of the text.
     */
    fun nextPosition(text: String, selectionStart: Int, selectionEnd: Int, dx: Int, dy: Int): Int {
        val length = text.length
        val start = if (selectionStart < 0) length else selectionStart.coerceIn(0, length)
        val end = if (selectionEnd < 0) length else selectionEnd.coerceIn(0, length)
        val origin = if (dx < 0 || (dx == 0 && dy < 0)) min(start, end) else maxOf(start, end)
        val horizontal = (origin + dx).coerceIn(0, length)
        return offsetByLines(text, horizontal, dy)
    }

    fun offsetByLines(text: String, index: Int, lines: Int): Int {
        val length = text.length
        val safe = index.coerceIn(0, length)
        if (lines == 0 || length == 0) return safe
        val lineStarts = ArrayList<Int>(4)
        lineStarts.add(0)
        text.forEachIndexed { offset, character ->
            if (character == '\n') lineStarts.add(offset + 1)
        }
        var line = lineStarts.indexOfLast { it <= safe }
        if (line < 0) line = 0
        val column = safe - lineStarts[line]
        val target = (line + lines).coerceIn(0, lineStarts.lastIndex)
        val lineStart = lineStarts[target]
        val lineEndExclusive = if (target == lineStarts.lastIndex) length else lineStarts[target + 1] - 1
        val room = (lineEndExclusive - lineStart).coerceAtLeast(0)
        return lineStart + column.coerceIn(0, room)
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

    fun apply2D(connection: CursorConnection, dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val snapshot = runCatching { connection.read() }.getOrNull()
        val text = snapshot?.text
        if (snapshot == null || snapshot.startOffset < 0 || text == null) {
            nudge(connection, dx)
            nudgeVertical(connection, dy)
            return
        }
        val index = nextPosition(text, snapshot.selectionStart, snapshot.selectionEnd, dx, dy)
        runCatching { connection.setCursor(snapshot.startOffset + index) }
    }

    private fun nudge(connection: CursorConnection, delta: Int) {
        val left = delta < 0
        repeat(abs(delta)) {
            if (runCatching { connection.sendArrow(left) }.isFailure) return
        }
    }

    private fun nudgeVertical(connection: CursorConnection, lines: Int) {
        if (lines == 0) return
        val up = lines < 0
        repeat(abs(lines)) {
            if (runCatching { connection.sendLine(up) }.isFailure) return
        }
    }
}

internal data class CursorSnapshot(
    val length: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val startOffset: Int = 0,
    val text: String? = null,
)

internal interface CursorConnection {
    fun read(): CursorSnapshot?
    fun setCursor(index: Int)
    fun sendArrow(left: Boolean)
    fun sendLine(up: Boolean) {}
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
        val selected = connection.getSelectedText(0)?.toString().orEmpty()
        val text = before.toString() + selected + after.toString()
        return CursorSnapshot(
            length = text.length,
            selectionStart = before.length,
            selectionEnd = before.length + selected.length,
            text = text,
        )
    }

    override fun setCursor(index: Int) {
        connection.setSelection(index, index)
    }

    override fun sendArrow(left: Boolean) {
        sendKey(if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun sendLine(up: Boolean) {
        sendKey(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
    }

    private fun sendKey(code: Int) {
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
