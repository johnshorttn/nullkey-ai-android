package com.nullverse.nullkeyai.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {

    private val provider = DefaultKeyboardLayoutProvider()

    @Test
    fun portraitLettersContainQwertyAndModifiers() {
        val spec = provider.spec(KeyboardLayer.LETTERS, LayoutOrientation.PORTRAIT)
        val labels = spec.rows.flatMap { row -> row.keys.map { it.label } }
        val letters = ('a'..'z').map { it.toString() }
        assertTrue(labels.containsAll(letters))
        assertEquals(4, spec.rows.size)
        assertTrue(spec.rows.flatMap { it.keys }.any { it.code == KeyCodes.SHIFT })
        assertTrue(spec.rows.flatMap { it.keys }.any { it.code == KeyCodes.DELETE && it.isRepeatable })
        assertTrue(spec.rows.flatMap { it.keys }.any { it.code == KeyCodes.SPACE })
        assertTrue(spec.rows.flatMap { it.keys }.any { it.code == KeyCodes.DONE })
        assertTrue(spec.rows.flatMap { it.keys }.any { it.code == KeyCodes.MODE_CHANGE })
    }

    @Test
    fun landscapeLettersAddANumberRow() {
        val spec = provider.spec(KeyboardLayer.LETTERS, LayoutOrientation.LANDSCAPE)
        assertEquals(5, spec.rows.size)
        val firstRow = spec.rows.first().keys.map { it.label }
        assertEquals((1..9).map { it.toString() } + "0", firstRow)
    }

    @Test
    fun symbolsContainDigitsAndCommonPunctuation() {
        val spec = provider.spec(KeyboardLayer.SYMBOLS, LayoutOrientation.PORTRAIT)
        val labels = spec.rows.flatMap { row -> row.keys.map { it.label } }
        assertTrue(labels.containsAll((0..9).map { it.toString() }))
        assertTrue(labels.contains("@"))
        assertTrue(labels.contains("#"))
        assertTrue(labels.contains("ABC"))
    }

    @Test
    fun vowelsExposeLongPressPopupCharacters() {
        val spec = provider.spec(KeyboardLayer.LETTERS, LayoutOrientation.PORTRAIT)
        val keys = spec.rows.flatMap { it.keys }.associateBy { it.label }
        assertTrue(keys.getValue("e").popupCharacters.contains("é"))
        assertTrue(keys.getValue("a").popupCharacters.contains("à"))
        assertTrue(keys.getValue("n").popupCharacters.contains("ñ"))
    }

    @Test
    fun orientationIsPartOfTheLayoutContract() {
        val portrait = provider.spec(KeyboardLayer.LETTERS, LayoutOrientation.PORTRAIT)
        val landscape = provider.spec(KeyboardLayer.LETTERS, LayoutOrientation.LANDSCAPE)
        assertTrue(portrait.id != landscape.id)
        assertEquals(KeyboardLayer.LETTERS, portrait.layer)
        assertEquals(KeyboardLayer.LETTERS, landscape.layer)
    }
}
