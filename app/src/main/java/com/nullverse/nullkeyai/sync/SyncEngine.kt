package com.nullverse.nullkeyai.sync

data class SyncReport(
    val providerId: String,
    val pulled: Int,
    val pushed: Int,
    val applied: Int,
    val conflictsPreserved: Int,
    val tombstonesPurged: Int = 0
)

/**
 * Pull-merge-push coordinator. Excluded live payloads stay on the device and
 * are omitted from [SyncTransport.push]. No provider URLs or credentials live
 * here — the [SyncTransport] is injected.
 */
class SyncEngine(
    private val deviceIdentity: DeviceIdentity,
    private val store: SyncRecordStore,
    private val transport: SyncTransport,
    private val merger: SyncMerger = SyncMerger(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun sync(): SyncReport {
        val local = store.all()
        val remote = transport.pull()
        val merged = merger.merge(local, remote.records)
        for (record in merged.records) {
            store.upsert(record)
        }
        val outgoing = store.all().filter { it.shouldPush() }
        transport.push(
            SyncEnvelope(
                producerDeviceId = deviceIdentity.current(),
                producedAt = clock(),
                records = outgoing
            )
        )
        return SyncReport(
            providerId = transport.providerId,
            pulled = remote.records.size,
            pushed = outgoing.size,
            applied = merged.applied,
            conflictsPreserved = merged.conflictsPreserved
        )
    }

    suspend fun purgeExpiredTombstones(
        now: Long = clock(),
        retentionDays: Int = TombstonePolicy.DEFAULT_RETENTION_DAYS
    ): Int {
        val expired = store.tombstones().filter { TombstonePolicy.isExpired(it, now, retentionDays) }
        expired.forEach { store.remove(it.syncId) }
        return expired.size
    }
}

internal fun SyncClipRecord.shouldPush(): Boolean {
    if (isTombstone) return true
    return payload?.syncExcluded != true
}
