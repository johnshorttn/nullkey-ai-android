package com.nullverse.nullkeyai.sync

import com.nullverse.nullkeyai.db.ClipDao

/**
 * [SyncRecordStore] backed by the existing clips table. Rows without a device
 * origin cannot yet participate in sync and are skipped until capture stamps
 * [com.nullverse.nullkeyai.db.Clip.originDeviceId].
 */
class ClipDaoSyncRecordStore(
    private val dao: ClipDao
) : SyncRecordStore {

    override suspend fun all(): List<SyncClipRecord> =
        dao.allRows().mapNotNull { ClipSyncMapper.toRecord(it) }

    override suspend fun upsert(record: SyncClipRecord) {
        val existing = dao.bySyncId(record.syncId)
        val clip = ClipSyncMapper.toClip(record, localId = existing?.id ?: 0L).let { mapped ->
            if (existing == null) mapped
            else mapped.copy(
                createdAt = existing.createdAt,
                localAssetPath = mapped.localAssetPath ?: existing.localAssetPath.takeIf { !record.isTombstone }
            )
        }
        if (existing == null) {
            dao.insert(clip.copy(id = 0))
        } else {
            dao.update(clip)
        }
    }

    override suspend fun find(syncId: String): SyncClipRecord? =
        dao.bySyncId(syncId)?.let { ClipSyncMapper.toRecord(it) }

    override suspend fun tombstones(): List<Tombstone> =
        dao.syncTombstones().mapNotNull { ClipSyncMapper.toRecord(it)?.toTombstoneOrNull() }

    override suspend fun remove(syncId: String) {
        dao.deleteBySyncId(syncId)
    }
}
