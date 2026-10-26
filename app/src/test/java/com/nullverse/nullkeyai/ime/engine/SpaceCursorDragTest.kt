package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceCursorDragTest {

    @Test
    fun movementShorterThanAStepDoesNotEngage() {
        val drag = SpaceCursorDrag(stepPx = 40f)
        drag.begin(100f, 50f)
        assertEquals(0, drag.move(120f, 50f))
        assertFalse(drag.engaged)
        assertEquals(SpaceCursorDrag.Axis.HORIZONTAL, drag.axis(120f, 50f))
    }

    @Test
    fun eachStepRightThenBackLeftFollowsTheFinger() {
        val drag = SpaceCursorDrag(stepPx = 40f)
        drag.begin(200f, 80f)
        assertEquals(1, drag.move(240f, 80f))
        assertEquals(1, drag.move(280f, 82f))
        assertTrue(drag.engaged)
        assertEquals(-1, drag.move(240f, 80f))
        assertEquals(0, drag.move(240f, 90f))
    }

    @Test
    fun verticalTravelDoesNotEngage() {
        val drag = SpaceCursorDrag(stepPx = 40f)
        drag.begin(10f, 10f)
        assertEquals(SpaceCursorDrag.Axis.VERTICAL, drag.axis(12f, 80f))
        assertEquals(0, drag.move(12f, 80f))
        assertFalse(drag.engaged)
    }
}
