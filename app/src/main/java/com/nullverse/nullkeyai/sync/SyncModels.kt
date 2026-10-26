package com.nullverse.nullkeyai.sync

import java.security.MessageDigest

/**
 * Provider-independent clip record. Local Room row ids are never used as the
 * cross-device identity — [syncId] is the stable UUID.
 */
data class SyncClipRecord(
    val syncId: String,
    val revision: Long,
    val originDeviceId: String,
    val modifiedByDeviceId: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val state: SyncRecordState = SyncRecordState.LOCAL,
    val payload: SyncClipPayload? = null
) {
    init {
        require(syncId.isNotBlank()) { "syncId must not be blank" }
        require(revision >= 1L) { "revision must be >= 1" }
        require(originDeviceId.isNotBlank()) { "originDeviceId must not be blank" }
        require(modifiedByDeviceId.isNotBlank()) { "modifiedByDeviceId must not be blank" }
        if (!isTombstone) {
            requireNotNull(payload) { "Live sync records require a payload" }
        }
    }

    val isTombstone: Boolean get() = deletedAt != null

    fun payloadFingerprint(): String =
        if (isTombstone) TOMBSTONE_FINGERPRINT else payload!!.fingerprint()

    fun asTombstone(deletedAt: Long, deletedByDeviceId: String): SyncClipRecord = copy(
        revision = revision + 1,
        modifiedByDeviceId = deletedByDeviceId,
        updatedAt = deletedAt,
        deletedAt = deletedAt,
        state = SyncRecordState.TOMBSTONE,
        payload = null
    )

    companion object {
        const val TOMBSTONE_FINGERPRINT = "tombstone"
    }
}

enum class SyncRecordState {
    LOCAL,
    PENDING,
    SYNCED,
    CONFLICT,
    TOMBSTONE
}

/**
 * User-visible clip fields carried over the wire. Asset bytes stay out of this
 * DTO — attachments sync as managed encrypted files, not database BLOBs.
 */
data class SyncClipPayload(
    val content: String,
    val isFile: Boolean = false,
    val mimeType: String? = null,
    val tag: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
    val contentType: String = "TEXT",
    val notes: String = "",
    val protectedPayload: Boolean = false,
    val localAssetPath: String? = null,
    val sourcePackage: String? = null,
    val sourceAppLabel: String? = null,
    val sourceUri: String? = null,
    val captureMethod: String = "UNKNOWN",
    val sourceConfidence: String = "UNKNOWN",
    val ocrText: String? = null,
    val trashedAt: Long? = null,
    val syncExcluded: Boolean = false,
    val tags: List<String> = emptyList()
) {
    fun fingerprint(): String {
        val raw = buildString {
            append(content).append(SEP)
            append(isFile).append(SEP)
            append(mimeType.orEmpty()).append(SEP)
            append(tag.orEmpty()).append(SEP)
            append(pinned).append(SEP)
            append(createdAt).append(SEP)
            append(contentType).append(SEP)
            append(notes).append(SEP)
            append(protectedPayload).append(SEP)
            append(localAssetPath.orEmpty()).append(SEP)
            append(sourcePackage.orEmpty()).append(SEP)
            append(sourceAppLabel.orEmpty()).append(SEP)
            append(sourceUri.orEmpty()).append(SEP)
            append(captureMethod).append(SEP)
            append(sourceConfidence).append(SEP)
            append(ocrText.orEmpty()).append(SEP)
            append(trashedAt ?: -1L).append(SEP)
            append(syncExcluded).append(SEP)
            append(tags.sorted().joinToString(","))
        }
        return sha256Hex(raw)
    }

    companion object {
        private const val SEP = '\u001f'
    }
}

data class Tombstone(
    val syncId: String,
    val revision: Long,
    val deletedAt: Long,
    val deletedByDeviceId: String,
    val originDeviceId: String
) {
    init {
        require(syncId.isNotBlank())
        require(revision >= 1L)
        require(deletedAt > 0L)
    }

    fun toRecord(): SyncClipRecord = SyncClipRecord(
        syncId = syncId,
        revision = revision,
        originDeviceId = originDeviceId,
        modifiedByDeviceId = deletedByDeviceId,
        updatedAt = deletedAt,
        deletedAt = deletedAt,
        state = SyncRecordState.TOMBSTONE,
        payload = null
    )
}

fun SyncClipRecord.toTombstoneOrNull(): Tombstone? {
    val deleted = deletedAt ?: return null
    return Tombstone(
        syncId = syncId,
        revision = revision,
        deletedAt = deleted,
        deletedByDeviceId = modifiedByDeviceId,
        originDeviceId = originDeviceId
    )
}

data class SyncEnvelope(
    val format: Int = FORMAT_VERSION,
    val producerDeviceId: String,
    val producedAt: Long,
    val records: List<SyncClipRecord>
) {
    init {
        require(producerDeviceId.isNotBlank()) { "producerDeviceId must not be blank" }
        require(format >= 1) { "format must be >= 1" }
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val APP_TAG = "NullKey AI"
        const val KIND = "sync-envelope"
    }
}

internal fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
}
