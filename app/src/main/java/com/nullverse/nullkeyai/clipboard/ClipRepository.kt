package com.nullverse.nullkeyai.clipboard

import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipDao
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.ClipSourceConfidence
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Application-facing API over [ClipDao]. Owns the capture de-duplication and the
 * 30-day Trash retention rules so they can be unit-tested without Android.
 */
data class ClipCaptureRequest(
    val content: String,
    val contentType: ClipContentType = ClipContentType.TEXT,
    val isFile: Boolean = false,
    val mimeType: String? = null,
    val legacyTag: String? = null,
    val localAssetPath: String? = null,
    val sourcePackage: String? = null,
    val sourceAppLabel: String? = null,
    val sourceUri: String? = null,
    val captureMethod: ClipCaptureMethod = ClipCaptureMethod.UNKNOWN,
    val sourceConfidence: ClipSourceConfidence = ClipSourceConfidence.UNKNOWN
)

class ClipRepository(private val dao: ClipDao, private val assetStore: VaultAssetStore? = null) {

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

    suspend fun capture(request: ClipCaptureRequest): Long? {
        if (request.content.isBlank() && request.localAssetPath.isNullOrBlank()) return null
        val latest = dao.latestActive()
        if (latest != null &&
            latest.content == request.content &&
            latest.contentType == request.contentType.name &&
            latest.localAssetPath == request.localAssetPath
        ) return null
        return dao.insert(
            Clip(
                content = request.content,
                isFile = request.isFile,
                mimeType = request.mimeType,
                tag = request.legacyTag,
                contentType = request.contentType.name,
                localAssetPath = request.localAssetPath,
                sourcePackage = request.sourcePackage,
                sourceAppLabel = request.sourceAppLabel,
                sourceUri = request.sourceUri,
                captureMethod = request.captureMethod.name,
                sourceConfidence = request.sourceConfidence.name
            )
        )
    }

    suspend fun moveToTrash(id: Long) = dao.moveToTrash(id)

    suspend fun restore(id: Long) = dao.restore(id)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun setNotes(id: Long, notes: String) = dao.setNotes(id, notes)

    suspend fun setProtected(id: Long, isProtected: Boolean) = dao.setProtected(id, isProtected)

    /** Serialize all active clips to a portable JSON backup document. */
    suspend fun exportJson(): String = ClipBackup.toJson(dao.allActive())

    /**
     * Import clips from a JSON backup. Blank clips and clips whose content already
     * exists in the active vault are skipped. Returns the number actually inserted.
     * Throws [IllegalArgumentException] if [json] is not a valid backup document.
     */
    suspend fun importJson(json: String): Int {
        val clips = ClipBackup.fromJson(json)
        var inserted = 0
        for (clip in clips) {
            if (clip.content.isBlank()) continue
            if (dao.countByContent(clip.content) > 0) continue
            dao.insert(clip.copy(id = 0, trashedAt = null))
            inserted++
        }
        return inserted
    }

    /**
     * Purge trashed clips whose trash timestamp is older than [retentionDays]
     * relative to [now]. Returns the number of rows removed.
     */
    suspend fun purgeExpiredTrash(
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
        now: Long = System.currentTimeMillis()
    ): Int {
        val cutoff = now - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val expired = dao.expiredTrash(cutoff)
        val deleted = dao.purgeExpired(cutoff)
        if (deleted > 0) expired.forEach { assetStore?.delete(it.localAssetPath) }
        return deleted
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
    }
}
