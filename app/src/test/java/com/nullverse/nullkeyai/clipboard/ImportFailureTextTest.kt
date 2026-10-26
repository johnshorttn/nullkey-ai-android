package com.nullverse.nullkeyai.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportFailureTextTest {
    @Test
    fun detail_truncatesLongMessages() {
        val message = "x".repeat(200)
        val detail = ImportFailureText.detail(IllegalStateException(message), limit = 32)
        assertEquals(32, detail?.length)
    }

    @Test
    fun detail_returnsNullWhenLimitIsNotPositive() {
        assertNull(ImportFailureText.detail(IllegalStateException("boom"), limit = 0))
    }
}
