package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGeometryTest {

    private fun qwertyPortrait(
        width: Float = 1000f,
        height: Float = 400f,
        gap: Float = 8f,
    ): KeyboardGeometry = KeyboardGeometry.place(
        spec = DefaultKeyboardLayoutProvider.LETTERS_PORTRAIT,
        orientation = LayoutOrientation.PORTRAIT,
        widthPx = width,
        heightPx = height,
        paddingHorizontalPx = 10f,
        paddingVerticalPx = 10f,
        gapPx = gap,
    )

    @Test
    fun placesEveryKeyInsideKeyboardBounds() {
        val geo = qwertyPortrait()
        assertTrue(geo.placedKeys.isNotEmpty())
        for (key in geo.placedKeys) {
            assertTrue(key.slot.left >= 0f)
            assertTrue(key.slot.top >= 0f)
            assertTrue(key.slot.right <= geo.widthPx + 0.01f)
            assertTrue(key.slot.bottom <= geo.heightPx + 0.01f)
            assertTrue(key.visual.width > 0f)
            assertTrue(key.visual.height > 0f)
        }
    }

    @Test
    fun keysInARowDoNotOverlap() {
        val geo = qwertyPortrait()
        val rows = geo.placedKeys.groupBy { it.slot.top }
        for (row in rows.values) {
            val ordered = row.sortedBy { it.slot.left }
            for (i in 0 until ordered.lastIndex) {
                assertTrue(
                    "${ordered[i].spec.label} overlaps ${ordered[i + 1].spec.label}",
                    ordered[i].slot.right <= ordered[i + 1].slot.left + 0.01f
                )
            }
        }
    }

    @Test
    fun visualRectIsInsetFromSlot() {
        val geo = qwertyPortrait(gap = 10f)
        val key = geo.placedKeys.first()
        assertTrue(key.visual.left >= key.slot.left)
        assertTrue(key.visual.right <= key.slot.right)
        assertTrue(key.visual.top >= key.slot.top)
        assertTrue(key.visual.bottom <= key.slot.bottom)
    }

    @Test
    fun hitTestFindsKeyAtCenter() {
        val geo = qwertyPortrait()
        for (key in geo.placedKeys) {
            val hit = geo.hitTest(key.slot.centerX, key.slot.centerY)
            assertEquals(key.spec.label, hit?.spec?.label)
        }
    }

    @Test
    fun hitTestUsesSlotSoGapsStillRegister() {
        val geo = qwertyPortrait(gap = 12f)
        val key = geo.placedKeys.first { it.spec.label == "q" }
        val x = key.visual.right + 1f
        val y = key.slot.centerY
        assertTrue(x < key.slot.right)
        assertEquals("q", geo.hitTest(x, y)?.spec?.label)
    }

    @Test
    fun hitTestWithSlopPicksNearestKey() {
        val geo = qwertyPortrait()
        val enter = geo.placedKeys.first { it.spec.code == KeyCodes.DONE }
        val hit = geo.hitTest(enter.slot.right + 6f, enter.slot.centerY, maxSlopPx = 20f)
        assertEquals(KeyCodes.DONE, hit?.spec?.code)
        assertNull(geo.hitTest(enter.slot.right + 6f, enter.slot.centerY, maxSlopPx = 0f))
    }

    @Test
    fun landscapeKeysAreShorterThanPortrait() {
        val portrait = KeyboardGeometry.place(
            spec = DefaultKeyboardLayoutProvider.LETTERS_PORTRAIT,
            orientation = LayoutOrientation.PORTRAIT,
            widthPx = 1000f,
            heightPx = 400f,
            paddingHorizontalPx = 0f,
            paddingVerticalPx = 0f,
            gapPx = 0f,
        )
        val landscape = KeyboardGeometry.place(
            spec = DefaultKeyboardLayoutProvider.LETTERS_LANDSCAPE,
            orientation = LayoutOrientation.LANDSCAPE,
            widthPx = 1000f,
            heightPx = 400f,
            paddingHorizontalPx = 0f,
            paddingVerticalPx = 0f,
            gapPx = 0f,
        )
        val portraitHeight = portrait.placedKeys.first().slot.height
        val landscapeHeight = landscape.placedKeys.first().slot.height
        assertTrue(landscapeHeight < portraitHeight)
        assertEquals(4, portrait.spec.rows.size)
        assertEquals(5, landscape.spec.rows.size)
    }

    @Test
    fun emptySizeYieldsNoKeys() {
        val geo = KeyboardGeometry.place(
            spec = DefaultKeyboardLayoutProvider.LETTERS_PORTRAIT,
            orientation = LayoutOrientation.PORTRAIT,
            widthPx = 0f,
            heightPx = 0f,
            paddingHorizontalPx = 0f,
            paddingVerticalPx = 0f,
            gapPx = 0f,
        )
        assertTrue(geo.placedKeys.isEmpty())
        assertNull(geo.hitTest(1f, 1f))
    }

    @Test
    fun preferredHeightIsLargerInPortraitForSameRowCount() {
        val portrait = preferredKeyboardHeightPx(3f, LayoutOrientation.PORTRAIT, 4)
        val landscape = preferredKeyboardHeightPx(3f, LayoutOrientation.LANDSCAPE, 4)
        assertTrue(portrait > landscape)
        assertNotNull(portrait)
    }
}
