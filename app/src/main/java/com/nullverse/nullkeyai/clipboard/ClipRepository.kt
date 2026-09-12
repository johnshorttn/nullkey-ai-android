package com.nullverse.nullkeyai.clipboard

import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipDao
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Application-facing API over [ClipDao]. Owns the capture de-duplication and the
 * 30-day Trash retention rules so they can be unit-tested without Android.
 */
class ClipRepository(private val dao: ClipDao) {

    fun search(query: String, filesOnly: Boolean): Flow<List<Clip>> =
        dao.search(query.trim(), filesOnly)

    suspend fun searchOnce(query: String, filesOnly: Boolean): List<Clip> =
        dao.searchOnce(query.trim(), filesOnly)

    fun trash(): Flow<List<Clip>> = dao.trash()

    /**
     * Capture a new clip. Blank captures are ignored, and a capture identical to
     * the most recent active clip is skipped so re-copying the same thing does
     * not spam the vault. Returns the new row id, or null if it was skipped.
     */
    suspend fun capture(
        content: String,
        isFile: Boolean = false,
        mimeType: String? = null,
        tag: String? = null
    ): Long? {
        if (content.isBlank()) return null
        val latest = dao.latestActive()
        if (latest != null && latest.content == content && latest.isFile == isFile) {
            return null
        }
        return dao.insert(
            Clip(content = content, isFile = isFile, mimeType = mimeType, tag = tag)
        )
    }

    suspend fun moveToTrash(id: Long) = dao.moveToTrash(id)

    suspend fun restore(id: Long) = dao.restore(id)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    /**
     * Purge trashed clips whose trash timestamp is older than [retentionDays]
     * relative to [now]. Returns the number of rows removed.
     */
    suspend fun purgeExpiredTrash(
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
        now: Long = System.currentTimeMillis()
    ): Int {
        val cutoff = now - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        return dao.purgeExpired(cutoff)
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
    }
}
