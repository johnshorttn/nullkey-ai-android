package com.nullverse.nullkeyai.sync

import java.util.concurrent.TimeUnit

/**
 * Tombstones outlive local Trash so an offline device cannot resurrect a clip
 * that was permanently deleted elsewhere.
 */
object TombstonePolicy {
    const val DEFAULT_RETENTION_DAYS = 90

    fun cutoffMillis(now: Long, retentionDays: Int = DEFAULT_RETENTION_DAYS): Long {
        require(retentionDays > 0) { "retentionDays must be positive" }
        return now - TimeUnit.DAYS.toMillis(retentionDays.toLong())
    }

    fun isExpired(
        deletedAt: Long,
        now: Long,
        retentionDays: Int = DEFAULT_RETENTION_DAYS
    ): Boolean = deletedAt < cutoffMillis(now, retentionDays)

    fun isExpired(
        tombstone: Tombstone,
        now: Long,
        retentionDays: Int = DEFAULT_RETENTION_DAYS
    ): Boolean = isExpired(tombstone.deletedAt, now, retentionDays)

    /**
     * A live incoming record must not replace a tombstone of equal or newer
     * revision — that would resurrect a deleted clip.
     */
    fun blocksResurrection(tombstone: Tombstone, incomingLive: SyncClipRecord): Boolean {
        if (incomingLive.isTombstone) return false
        if (tombstone.syncId != incomingLive.syncId) return false
        return tombstone.revision >= incomingLive.revision
    }
}
