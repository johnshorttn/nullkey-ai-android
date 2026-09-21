package com.nullverse.nullkeyai.sync

interface SyncRecordStore {
    suspend fun all(): List<SyncClipRecord>
    suspend fun upsert(record: SyncClipRecord)
    suspend fun find(syncId: String): SyncClipRecord?
    suspend fun tombstones(): List<Tombstone>
    suspend fun remove(syncId: String)
}

class InMemorySyncRecordStore(
    initial: List<SyncClipRecord> = emptyList()
) : SyncRecordStore {
    private val lock = Any()
    private val records = LinkedHashMap<String, SyncClipRecord>()

    init {
        initial.forEach { records[it.syncId] = it }
    }

    override suspend fun all(): List<SyncClipRecord> = synchronized(lock) { records.values.toList() }

    override suspend fun upsert(record: SyncClipRecord) {
        synchronized(lock) { records[record.syncId] = record }
    }

    override suspend fun find(syncId: String): SyncClipRecord? =
        synchronized(lock) { records[syncId] }

    override suspend fun tombstones(): List<Tombstone> = synchronized(lock) {
        records.values.mapNotNull { it.toTombstoneOrNull() }
    }

    override suspend fun remove(syncId: String) {
        synchronized(lock) { records.remove(syncId) }
    }
}
