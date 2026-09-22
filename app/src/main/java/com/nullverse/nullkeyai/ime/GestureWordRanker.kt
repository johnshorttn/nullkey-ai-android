package com.nullverse.nullkeyai.ime

/**
 * On-device swipe-word ranking. Matches a visited-key path to dictionary words
 * without network or ML: first/last letters must match, extras on the path are
 * cheap, and a small number of missed interior letters is allowed so a swipe
 * that skips a key can still resolve.
 */
object GestureWordRanker {
    const val SKIP_PATH_COST = 1
    const val MISS_WORD_COST = 12
    private const val UNSEEN = -1

    fun rank(path: String, counts: Map<String, Int>, max: Int = 3): List<String> {
        val gesture = lettersOf(path)
        if (gesture.length < 2) return emptyList()
        return counts.asSequence()
            .mapNotNull { (word, frequency) ->
                val score = score(gesture, word) ?: return@mapNotNull null
                Triple(word, score, frequency)
            }
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { it.second }
                    .thenByDescending { it.third }
                    .thenBy { it.first.length }
                    .thenBy { it.first }
            )
            .map { it.first }
            .take(max)
            .toList()
    }

    fun applyCasing(word: String, path: String): String {
        val first = path.trim().firstOrNull { it.isLetter() } ?: return word
        return if (first.isUpperCase()) {
            word.replaceFirstChar { it.uppercaseChar() }
        } else {
            word
        }
    }

    internal fun score(path: String, word: String): Int? {
        val gesture = collapseRepeats(lettersOf(path))
        val target = collapseRepeats(lettersOf(word))
        if (gesture.length < 2 || target.length < 2) return null
        if (gesture.first() != target.first() || gesture.last() != target.last()) return null
        return alignmentCost(gesture, target)
    }

    internal fun collapseRepeats(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (isEmpty() || last() != character) append(character)
        }
    }

    private fun lettersOf(value: String): String =
        value.trim().lowercase().filter { it.isLetter() }

    private fun maxMisses(wordLength: Int): Int = when {
        wordLength <= 3 -> 0
        wordLength <= 5 -> 1
        else -> 2
    }

    /**
     * Minimum edit cost to consume [gesture] against [word]. Both strings are
     * already collapsed and share first/last letters.
     *
     * States are packed ints (path index, word index, misses). A data-class map
     * key loses [equals]/[hashCode] when R8 shrinks it, so release ranking
     * stops memoizing. The queue is an index cursor so the dex does not bind
     * the API 35 List removal call.
     */
    private fun alignmentCost(gesture: String, word: String): Int? {
        val n = gesture.length
        val m = word.length
        val missCap = maxMisses(m)
        val missStride = missCap + 1
        val jStride = m + 1
        val best = IntArray((n + 1) * jStride * missStride) { UNSEEN }
        val queueKeys = ArrayList<Int>()
        val queueCosts = ArrayList<Int>()
        offerState(best, queueKeys, queueCosts, missCap, n, m, missStride, jStride, 1, 1, 0, 0)
        var head = 0
        var result: Int? = null
        while (head < queueKeys.size) {
            val key = queueKeys[head]
            val cost = queueCosts[head]
            head++
            if (cost != best[key]) continue
            val misses = key % missStride
            val ij = key / missStride
            val j = ij % jStride
            val i = ij / jStride
            if (j == m) {
                if (i == n) result = minOf(result ?: cost, cost)
                continue
            }
            if (i == n) continue

            val lastWord = j == m - 1
            val lastPath = i == n - 1
            if (lastWord && lastPath) {
                if (gesture[i] == word[j]) {
                    offerState(best, queueKeys, queueCosts, missCap, n, m, missStride, jStride, n, m, misses, cost)
                }
            } else if (lastWord) {
                offerState(
                    best, queueKeys, queueCosts, missCap, n, m, missStride, jStride,
                    i + 1, j, misses, cost + SKIP_PATH_COST,
                )
            } else if (lastPath) {
                offerState(
                    best, queueKeys, queueCosts, missCap, n, m, missStride, jStride,
                    i, j + 1, misses + 1, cost + MISS_WORD_COST,
                )
            } else {
                if (gesture[i] == word[j]) {
                    offerState(
                        best, queueKeys, queueCosts, missCap, n, m, missStride, jStride,
                        i + 1, j + 1, misses, cost,
                    )
                }
                offerState(
                    best, queueKeys, queueCosts, missCap, n, m, missStride, jStride,
                    i + 1, j, misses, cost + SKIP_PATH_COST,
                )
                if (j > 0) {
                    offerState(
                        best, queueKeys, queueCosts, missCap, n, m, missStride, jStride,
                        i, j + 1, misses + 1, cost + MISS_WORD_COST,
                    )
                }
            }
        }
        return result
    }

    private fun offerState(
        best: IntArray,
        queueKeys: ArrayList<Int>,
        queueCosts: ArrayList<Int>,
        missCap: Int,
        n: Int,
        m: Int,
        missStride: Int,
        jStride: Int,
        i: Int,
        j: Int,
        misses: Int,
        cost: Int,
    ) {
        if (misses > missCap || i > n || j > m || cost < 0) return
        val key = (i * jStride + j) * missStride + misses
        val previous = best[key]
        if (previous != UNSEEN && cost >= previous) return
        best[key] = cost
        queueKeys.add(key)
        queueCosts.add(cost)
    }
}

data class SwipeCommitResult(
    val committed: String,
    val suggestions: List<String>,
)

object SwipeCommit {
    fun resolve(path: String, ranked: List<String>): SwipeCommitResult? {
        if (ranked.isEmpty()) return null
        val cased = ranked.map { GestureWordRanker.applyCasing(it, path) }
        return SwipeCommitResult(committed = cased.first(), suggestions = cased)
    }
}
