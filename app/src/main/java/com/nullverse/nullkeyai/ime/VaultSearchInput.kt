package com.nullverse.nullkeyai.ime

/**
 * Edits for the vault search box that lives inside the IME window.
 *
 * The field can take focus: its own clipboard actions (paste, select-all, cut)
 * change its buffer. [android.view.inputmethod.InputConnection.commitText] and
 * [android.view.inputmethod.InputConnection.deleteSurroundingText] from this
 * IME do not. Key output has to change that same buffer while the field is
 * the typing target.
 */
internal interface ImeKeyOutput {
    fun commitText(text: CharSequence)
    fun deleteBeforeCursor(count: Int)
    fun enter()
    fun moveCursor(delta: Int)
}

internal object VaultSearchInput {
    data class Edit(val text: String, val cursor: Int)

    fun commit(text: String, selectionStart: Int, selectionEnd: Int, insert: String): Edit {
        val (start, end) = selection(text, selectionStart, selectionEnd)
        val merged = text.substring(0, start) + insert + text.substring(end)
        return Edit(merged, start + insert.length)
    }

    fun moveCursor(text: String, selectionStart: Int, selectionEnd: Int, delta: Int): Edit {
        val cursor = CursorStep.nextIndex(text.length, selectionStart, selectionEnd, delta)
        return Edit(text, cursor)
    }

    fun deleteBefore(text: String, selectionStart: Int, selectionEnd: Int, count: Int): Edit {
        val (start, end) = selection(text, selectionStart, selectionEnd)
        if (start != end && count <= 1) {
            return Edit(text.removeRange(start, end), start)
        }
        val from = (start - count.coerceAtLeast(0)).coerceAtLeast(0)
        return Edit(text.removeRange(from, end), from)
    }

    private fun selection(text: String, selectionStart: Int, selectionEnd: Int): Pair<Int, Int> {
        val length = text.length
        if (selectionStart < 0 || selectionEnd < 0) return length to length
        val start = selectionStart.coerceIn(0, length)
        val end = selectionEnd.coerceIn(0, length)
        return if (start <= end) start to end else end to start
    }
}
