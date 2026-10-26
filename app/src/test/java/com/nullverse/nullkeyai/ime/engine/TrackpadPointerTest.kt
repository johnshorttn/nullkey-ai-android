package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackpadPointerTest {

    @Test
    fun aTapDoesNotMove() {
        val pad = TrackpadPointer(stepX = 24f, stepY = 32f)
        pad.down(10f, 10f)
        pad.reset()
        assertEquals(0 to 0, pad.move(40f, 40f))
    }

    @Test
    fun horizontalAndVerticalStepsAreCharacterSized() {
        val pad = TrackpadPointer(stepX = 24f, stepY = 32f)
        pad.down(0f, 0f)
        assertEquals(0 to 0, pad.move(23f, 31f))
        assertEquals(2 to 0, pad.move(48f, 31f))
        assertEquals(-1 to 1, pad.move(24f, 63f))
    }

    @Test
    fun slowDragAccumulatesAndJitterCancels() {
        val pad = TrackpadPointer(stepX = 20f, stepY = 20f)
        pad.down(0f, 0f)
        assertEquals(0 to 0, pad.move(8f, 0f))
        assertEquals(0 to 0, pad.move(0f, 0f))
        assertEquals(0 to 0, pad.move(19f, 0f))
        assertEquals(1 to 0, pad.move(20f, 0f))
    }
}
