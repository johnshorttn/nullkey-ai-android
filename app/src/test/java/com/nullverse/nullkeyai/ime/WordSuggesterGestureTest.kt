package com.nullverse.nullkeyai.ime

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class WordSuggesterGestureTest {
    private fun prefs(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var proxy: SharedPreferences
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putString" -> { values[args!![0] as String] = args[1]; editor }
                "apply" -> null
                else -> editor
            }
        } as SharedPreferences.Editor
        proxy = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] as? String ?: args[1]
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
        return proxy
    }

    private fun suggester(): WordSuggester {
        val constructor = WordSuggester::class.java.getDeclaredConstructor(
            SharedPreferences::class.java,
            MutableMap::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            prefs(),
            mutableMapOf("hello" to 5, "help" to 2, "hero" to 1, "world" to 3)
        )
    }

    @Test
    fun gestureRankingUsesOrderedPathAndEndpoints() {
        val results = suggester().suggestGesture("heelllo", 3)
        assertEquals("hello", results.first())
        assertTrue("world" !in results)
    }

    @Test
    fun repeatedLetterWordMatchesSingleKeyVisit() {
        val results = suggester().suggestGesture("helo", 3)
        assertEquals("hello", results.first())
    }

    @Test
    fun shortGestureIsIgnored() {
        assertTrue(suggester().suggestGesture("h").isEmpty())
    }

    @Test
    fun learnedFrequencyBreaksEquivalentMatches() {
        val results = suggester().suggestGesture("hello", 3)
        assertEquals("hello", results.first())
    }

    @Test
    fun extraKeysOnThePathStillResolveHello() {
        val results = suggester().suggestGesture("hweirlo", 3)
        assertEquals("hello", results.first())
    }

    @Test
    fun unknownPathReturnsNoCandidates() {
        assertTrue(suggester().suggestGesture("qzxv", 3).isEmpty())
    }
}
