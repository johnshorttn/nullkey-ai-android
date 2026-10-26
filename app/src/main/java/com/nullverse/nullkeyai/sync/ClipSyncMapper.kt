package com.nullverse.nullkeyai.sync

import com.nullverse.nullkeyai.db.Clip

/**
 * Maps Room [Clip] rows onto provider-independent sync records using the
 * existing syncId / revision / device / tombstone columns.
 */
object ClipSyncMapper {

    fun toRecord(clip: Clip, tags: List<String> = emptyList()): SyncClipRecord? {
        val origin = clip.originDeviceId?.takeIf { it.isNotBlank() } ?: return null
        val modified = clip.modifiedByDeviceId?.takeIf { it.isNotBlank() } ?: origin
        val deletedAt = clip.syncDeletedAt
        return SyncClipRecord(
            syncId = clip.syncId,
            revision = clip.revision.coerceAtLeast(1L),
            originDeviceId = origin,
            modifiedByDeviceId = modified,
            updatedAt = clip.updatedAt,
            deletedAt = deletedAt,
            state = parseState(clip.syncState, deletedAt != null),
            payload = if (deletedAt != null) null else toPayload(clip, tags)
        )
    }

    fun toPayload(clip: Clip, tags: List<String> = emptyList()): SyncClipPayload = SyncClipPayload(
        content = clip.content,
        isFile = clip.isFile,
        mimeType = clip.mimeType,
        tag = clip.tag,
        pinned = clip.pinned,
        createdAt = clip.createdAt,
        contentType = clip.contentType,
        notes = clip.notes,
        protectedPayload = clip.protected,
        localAssetPath = clip.localAssetPath,
        sourcePackage = clip.sourcePackage,
        sourceAppLabel = clip.sourceAppLabel,
        sourceUri = clip.sourceUri,
        captureMethod = clip.captureMethod,
        sourceConfidence = clip.sourceConfidence,
        ocrText = clip.ocrText,
        trashedAt = clip.trashedAt,
        syncExcluded = clip.syncExcluded,
        tags = tags
    )

    fun toClip(record: SyncClipRecord, localId: Long = 0L): Clip {
        val payload = record.payload
        if (record.isTombstone || payload == null) {
            return Clip(
                id = localId,
                content = "",
                createdAt = record.updatedAt,
                trashedAt = record.deletedAt,
                updatedAt = record.updatedAt,
                syncId = record.syncId,
                revision = record.revision,
                originDeviceId = record.originDeviceId,
                modifiedByDeviceId = record.modifiedByDeviceId,
                syncDeletedAt = record.deletedAt,
                syncState = SyncRecordState.TOMBSTONE.name,
                syncExcluded = false
            )
        }
        return Clip(
            id = localId,
            content = payload.content,
            isFile = payload.isFile,
            mimeType = payload.mimeType,
            tag = payload.tag,
            pinned = payload.pinned,
            createdAt = payload.createdAt,
            trashedAt = payload.trashedAt,
            contentType = payload.contentType,
            notes = payload.notes,
            protected = payload.protectedPayload,
            localAssetPath = payload.localAssetPath,
            sourcePackage = payload.sourcePackage,
            sourceAppLabel = payload.sourceAppLabel,
            sourceUri = payload.sourceUri,
            captureMethod = payload.captureMethod,
            sourceConfidence = payload.sourceConfidence,
            ocrText = payload.ocrText,
            updatedAt = record.updatedAt,
            syncId = record.syncId,
            revision = record.revision,
            originDeviceId = record.originDeviceId,
            modifiedByDeviceId = record.modifiedByDeviceId,
            syncDeletedAt = record.deletedAt,
            syncState = record.state.name,
            syncExcluded = payload.syncExcluded
        )
    }

    private fun parseState(raw: String, tombstone: Boolean): SyncRecordState {
        if (tombstone) return SyncRecordState.TOMBSTONE
        return runCatching { SyncRecordState.valueOf(raw) }.getOrDefault(SyncRecordState.LOCAL)
    }
}
