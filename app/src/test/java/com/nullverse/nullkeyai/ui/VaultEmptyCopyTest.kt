package com.nullverse.nullkeyai.ui

import com.nullverse.nullkeyai.R
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultEmptyCopyTest {

    @Test
    fun defaultVaultUsesNoClipsCopy() {
        assertEquals(R.string.no_clips, VaultEmptyCopy.messageRes("", false))
    }

    @Test
    fun searchUsesNoResultsCopy() {
        assertEquals(R.string.no_search_results, VaultEmptyCopy.messageRes("hello", false))
    }

    @Test
    fun filesOnlyUsesFileEmptyCopy() {
        assertEquals(R.string.no_file_clips, VaultEmptyCopy.messageRes("", true))
    }

    @Test
    fun filesOnlySearchUsesFileSearchCopy() {
        assertEquals(R.string.no_file_search_results, VaultEmptyCopy.messageRes("pdf", true))
    }
}
