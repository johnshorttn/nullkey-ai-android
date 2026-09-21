package com.nullverse.nullkeyai.sync

internal fun liveRecord(
    syncId: String = "clip-1",
    revision: Long = 1L,
    origin: String = "dev-a",
    modified: String = origin,
    updatedAt: Long = 1_000L,
    content: String = "hello",
    excluded: Boolean = false,
    state: SyncRecordState = SyncRecordState.PENDING
): SyncClipRecord = SyncClipRecord(
    syncId = syncId,
    revision = revision,
    originDeviceId = origin,
    modifiedByDeviceId = modified,
    updatedAt = updatedAt,
    state = state,
    payload = SyncClipPayload(
        content = content,
        createdAt = 500L,
        syncExcluded = excluded
    )
)

internal fun tombstoneRecord(
    syncId: String = "clip-1",
    revision: Long = 2L,
    origin: String = "dev-a",
    modified: String = origin,
    updatedAt: Long = 2_000L,
    deletedAt: Long = updatedAt
): SyncClipRecord = SyncClipRecord(
    syncId = syncId,
    revision = revision,
    originDeviceId = origin,
    modifiedByDeviceId = modified,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    state = SyncRecordState.TOMBSTONE,
    payload = null
)
