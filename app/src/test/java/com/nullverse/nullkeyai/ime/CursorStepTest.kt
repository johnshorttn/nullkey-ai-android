package com.nullverse.nullkeyai.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorStepTest {

    @Test
    fun movesAndClampsInsideTheBuffer() {
        assertEquals(3, CursorStep.nextIndex(5, 5, 5, -2))
        assertEquals(5, CursorStep.nextIndex(5, 5, 5, 4))
        assertEquals(0, CursorStep.nextIndex(5, 1, 1, -8))
        assertEquals(0, CursorStep.nextIndex(0, 0, 0, -1))
        assertEquals(0, CursorStep.nextIndex(0, -1, -1, 3))
    }

    @Test
    fun selectionCollapsesTowardTheDrag() {
        assertEquals(1, CursorStep.nextIndex(8, 2, 5, -1))
        assertEquals(6, CursorStep.nextIndex(8, 2, 5, 1))
    }

    @Test
    fun readableFieldSetsTheClampedCursor() {
        val field = FakeCursor(CursorSnapshot(length = 4, selectionStart = 4, selectionEnd = 4))
        CursorStep.apply(field, -6)
        assertEquals(listOf(0), field.cursors)
        assertEquals(0, field.arrows)
    }

    @Test
    fun missingOrPasswordTextUsesArrowKeys() {
        val hidden = FakeCursor(snapshot = null)
        CursorStep.apply(hidden, 2)
        assertTrue(hidden.cursors.isEmpty())
        assertEquals(2, hidden.arrows)
        assertEquals(false, hidden.lastArrowLeft)

        val broken = FakeCursor(snapshot = null, readFails = true)
        CursorStep.apply(broken, -1)
        assertEquals(1, broken.arrows)
        assertEquals(true, broken.lastArrowLeft)
        assertTrue(broken.cursors.isEmpty())
    }

    private class FakeCursor(
        private val snapshot: CursorSnapshot?,
        private val readFails: Boolean = false,
    ) : CursorConnection {
        val cursors = mutableListOf<Int>()
        var arrows = 0
        var lastArrowLeft: Boolean? = null

        override fun read(): CursorSnapshot? {
            if (readFails) throw RuntimeException("editor refused")
            return snapshot
        }

        override fun setCursor(index: Int) {
            cursors += index
        }

        override fun sendArrow(left: Boolean) {
            arrows += 1
            lastArrowLeft = left
        }
    }
}
