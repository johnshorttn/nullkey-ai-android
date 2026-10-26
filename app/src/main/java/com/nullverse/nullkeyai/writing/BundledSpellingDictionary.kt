package com.nullverse.nullkeyai.writing

import android.content.Context

/**
 * Loads the SCOWL-derived word list shipped in assets. The parsed map is
 * cached for the process so the keyboard does not re-read it on every key.
 */
object BundledSpellingDictionary {
    private const val ASSET = "spelling/en_words.txt"

    /** Product names that should not be flagged, ranked after real words. */
    private val EXTRA = listOf("nullkey")

    @Volatile
    private var cached: SpellingDictionary? = null

    fun get(context: Context): SpellingDictionary {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    fun load(context: Context): SpellingDictionary {
        val words = context.assets.open(ASSET).bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
        return SpellingDictionary.fromFrequencyOrder((words + EXTRA).asSequence())
    }
}
