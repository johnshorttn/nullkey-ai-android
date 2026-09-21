package com.nullverse.nullkeyai.clipboard

import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.ClipDao
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.ClipSourceConfidence
import com.nullverse.nullkeyai.db.ClipTagCrossRef
import com.nullverse.nullkeyai.db.Tag
import com.nullverse.nullkeyai.db.TagDao
import com.nullverse.nullkeyai.security.VaultCrypto
import com.nullverse.nullkeyai.security.PortableVaultCrypto
import com.nullverse.nullkeyai.sync.DeviceIdentity
import com.nullverse.nullkeyai.sync.TombstonePolicy
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

class ClipRepository(private val dao: ClipDao, private val assetStore: VaultAssetStore? = null, private val tagDao: TagDao? = null, private val crypto: VaultCrypto? = null, private val deviceIdentity: DeviceIdentity? = null) {

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
            newClip(content = content, isFile = isFile, mimeType = mimeType, tag = tag)
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
            newClip(
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

    suspend fun moveToTrash(id: Long) { dao.moveToTrash(id); markLocalMutation(id) }

    suspend fun restore(id: Long) { dao.restore(id); markLocalMutation(id) }

    suspend fun setPinned(id: Long, pinned: Boolean) { dao.setPinned(id, pinned); markLocalMutation(id) }

    suspend fun setNotes(id: Long, notes: String) { dao.setNotes(id, notes); markLocalMutation(id) }

    suspend fun setProtected(id: Long, isProtected: Boolean) {
        val vaultCrypto = crypto ?: throw IllegalStateException("Vault crypto unavailable; protection state cannot be changed safely")
        val clip = dao.byId(id) ?: return
        if (clip.protected == isProtected) return
        if (isProtected) {
            clip.localAssetPath?.let { path ->
                assetStore?.resolve(path)?.let { file ->
                    if (!vaultCrypto.isEncryptedFile(file)) {
                        val encrypted = vaultCrypto.encryptFileBytes(file.readBytes())
                        assetStore.replaceAtomically(file, encrypted, ".encpart")
                    }
                }
            }
            dao.setProtectionPayload(
                id,
                vaultCrypto.encrypt(clip.content),
                vaultCrypto.encrypt(clip.notes),
                true
            )
        } else {
            clip.localAssetPath?.let { path ->
                assetStore?.resolve(path)?.let { file ->
                    if (vaultCrypto.isEncryptedFile(file)) {
                        val plain = vaultCrypto.decryptFile(file)
                        assetStore.replaceAtomically(file, plain, ".decpart")
                    }
                }
            }
            dao.setProtectionPayload(
                id,
                vaultCrypto.decrypt(clip.content),
                vaultCrypto.decrypt(clip.notes),
                false
            )
        }
        markLocalMutation(id)
    }

    suspend fun revealed(id: Long): Clip? {
        val clip = dao.byId(id) ?: return null
        if (!clip.protected) return clip
        val vaultCrypto = crypto ?: return null
        return clip.copy(
            content = vaultCrypto.decrypt(clip.content),
            notes = vaultCrypto.decrypt(clip.notes)
        )
    }

    suspend fun tagsForClip(id: Long): List<Tag> = tagDao?.forClip(id).orEmpty()

    suspend fun addTag(id: Long, rawName: String) {
        val tags = tagDao ?: return
        val name = rawName.trim()
        if (name.isBlank()) return
        val existing = tags.findByName(name)
        val tagId = existing?.id ?: tags.insert(Tag(name = name)).takeIf { it > 0 }
            ?: tags.findByName(name)?.id ?: return
        tags.attach(ClipTagCrossRef(id, tagId))
    }

    suspend fun removeTag(id: Long, tagId: Long) {
        tagDao?.detach(id, tagId)
    }

    suspend fun ensureDefaultTags() {
        val tags = tagDao ?: return
        DEFAULT_TAGS.forEach { name ->
            if (tags.findByName(name) == null) tags.insert(Tag(name = name))
        }
    }

    /** Serialize all active clips to JSON. Protected records remain device-key ciphertext. */
    suspend fun exportJson(): String = ClipBackup.toJson(dao.allActive())

    /**
     * Create a cross-device backup. Protected records are first decrypted with the
     * local Keystore key, then the entire document is re-encrypted under the user's
     * backup password. Plaintext exists only in memory for this operation.
     */
    suspend fun exportPortableEncrypted(password: CharArray): String {
        val vaultCrypto = crypto ?: throw IllegalStateException("Vault crypto unavailable")
        val store = assetStore ?: throw IllegalStateException("Vault asset store unavailable")
        val clips = dao.allActive()
        val archive = VaultArchive.build(clips, store, vaultCrypto) { id ->
            tagDao?.forClip(id)?.map { it.name }.orEmpty()
        }
        return PortableVaultCrypto.encrypt(archive, password)
    }

    suspend fun importPortableEncrypted(document: String, password: CharArray): Int {
        val store = assetStore ?: throw IllegalStateException("Vault asset store unavailable")
        val vaultCrypto = crypto ?: throw IllegalStateException("Vault crypto unavailable")
        val entries = VaultArchive.parse(PortableVaultCrypto.decrypt(document, password))
        var inserted = 0
        for (entry in entries) {
            if (dao.countByContent(entry.clip.content) > 0) continue
            // Re-encrypt protected material before it is persisted on the destination device.
            // This avoids a crash window where restored protected content could exist plaintext
            // in Room or in the private asset store.
            val assetBytes = entry.assetBytes?.let { bytes ->
                if (entry.restoreProtected) vaultCrypto.encryptFileBytes(bytes) else bytes
            }
            val asset = assetBytes?.let { store.importBytes(it, entry.clip.localAssetPath) }
            val restoredClip = if (entry.restoreProtected) {
                entry.clip.copy(
                    id = 0,
                    trashedAt = null,
                    protected = true,
                    content = vaultCrypto.encrypt(entry.clip.content),
                    notes = vaultCrypto.encrypt(entry.clip.notes),
                    localAssetPath = asset?.relativePath
                )
            } else {
                entry.clip.copy(
                    id = 0,
                    trashedAt = null,
                    protected = false,
                    localAssetPath = asset?.relativePath
                )
            }
            val rowId = runCatching { dao.insert(restoredClip) }.getOrElse {
                asset?.let { stored -> store.delete(stored.relativePath) }
                throw it
            }
            if (rowId <= 0) {
                asset?.let { store.delete(it.relativePath) }
                continue
            }
            entry.tags.forEach { addTag(rowId, it) }
            inserted++
        }
        return inserted
    }

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
        val deviceId = deviceIdentity?.current()
        var converted = 0
        for (clip in expired) {
            assetStore?.delete(clip.localAssetPath)
            dao.update(
                clip.copy(
                    content = "",
                    notes = "",
                    ocrText = null,
                    localAssetPath = null,
                    revision = clip.revision + 1,
                    modifiedByDeviceId = deviceId ?: clip.modifiedByDeviceId,
                    originDeviceId = clip.originDeviceId ?: deviceId,
                    syncDeletedAt = clip.trashedAt ?: now,
                    syncState = "TOMBSTONE",
                    updatedAt = now
                )
            )
            converted++
        }
        return converted
    }

    suspend fun purgeExpiredTombstones(
        retentionDays: Int = TombstonePolicy.DEFAULT_RETENTION_DAYS,
        now: Long = System.currentTimeMillis()
    ): Int = dao.purgeExpiredTombstones(TombstonePolicy.cutoffMillis(now, retentionDays))

    private fun newClip(content: String, isFile: Boolean = false, mimeType: String? = null, tag: String? = null, contentType: String = ClipContentType.TEXT.name, localAssetPath: String? = null, sourcePackage: String? = null, sourceAppLabel: String? = null, sourceUri: String? = null, captureMethod: String = ClipCaptureMethod.UNKNOWN.name, sourceConfidence: String = ClipSourceConfidence.UNKNOWN.name): Clip {
        val deviceId = deviceIdentity?.current()
        return Clip(content = content, isFile = isFile, mimeType = mimeType, tag = tag, contentType = contentType, localAssetPath = localAssetPath, sourcePackage = sourcePackage, sourceAppLabel = sourceAppLabel, sourceUri = sourceUri, captureMethod = captureMethod, sourceConfidence = sourceConfidence, originDeviceId = deviceId, modifiedByDeviceId = deviceId)
    }

    private suspend fun markLocalMutation(id: Long) {
        val clip = dao.byId(id) ?: return
        if (clip.syncDeletedAt != null) return
        val deviceId = deviceIdentity?.current()
        dao.update(clip.copy(revision = clip.revision + 1, modifiedByDeviceId = deviceId ?: clip.modifiedByDeviceId, originDeviceId = clip.originDeviceId ?: deviceId, syncState = "PENDING", updatedAt = maxOf(clip.updatedAt, System.currentTimeMillis())))
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        val DEFAULT_TAGS = listOf("Personal", "Work", "Coding")
    }
}
