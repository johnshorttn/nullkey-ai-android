package com.nullverse.nullkeyai.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultSearchInputTest {

    @Test
    fun commitAppendsWhenTheFieldHasNoCursor() {
        val edit = VaultSearchInput.commit("hi", -1, -1, "!")
        assertEquals(VaultSearchInput.Edit("hi!", 3), edit)
    }

    @Test
    fun commitReplacesASelection() {
        val edit = VaultSearchInput.commit("hello", 1, 4, "a")
        assertEquals(VaultSearchInput.Edit("hao", 2), edit)
    }

    @Test
    fun deleteBeforeRemovesTheCharacterBeforeTheCursor() {
        val edit = VaultSearchInput.deleteBefore("abc", 2, 2, 1)
        assertEquals(VaultSearchInput.Edit("ac", 1), edit)
    }

    @Test
    fun deleteBeforeRemovesASelection() {
        val edit = VaultSearchInput.deleteBefore("hello", 1, 4, 1)
        assertEquals(VaultSearchInput.Edit("ho", 1), edit)
    }

    @Test
    fun deleteBeforeDropsATypedToken() {
        val edit = VaultSearchInput.deleteBefore("say hello", 9, 9, 5)
        assertEquals(VaultSearchInput.Edit("say ", 4), edit)
    }

    @Test
    fun deleteOnEmptyTextStaysEmpty() {
        val edit = VaultSearchInput.deleteBefore("", -1, -1, 1)
        assertEquals(VaultSearchInput.Edit("", 0), edit)
    }
}
