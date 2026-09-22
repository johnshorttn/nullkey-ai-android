package com.nullverse.nullkeyai.clipboard

/**
 * Short, user-visible reason for a failed import or trash purge.
 * Walks the cause chain so a wrapped SQLite or crypto error is not replaced
 * by a generic toast.
 */
object ImportFailureText {
    const val MAX_DETAIL_CHARS = 140

    fun detail(error: Throwable, limit: Int = MAX_DETAIL_CHARS): String? {
        if (limit <= 0) return null
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf { text -> text.isNotEmpty() } }
            .firstOrNull()
            ?: return null
        return message.replace(Regex("\\s+"), " ").take(limit)
    }
}
