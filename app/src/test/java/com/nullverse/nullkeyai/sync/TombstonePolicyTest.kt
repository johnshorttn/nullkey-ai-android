package com.nullverse.nullkeyai.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TombstonePolicyTest {

    @Test
    fun recentTombstone_isNotExpired() {
        val now = 1_700_000_000_000L
        val tombstone = Tombstone(
            syncId = "s1",
            revision = 2,
            deletedAt = now - TimeUnit.DAYS.toMillis(10),
            deletedByDeviceId = "dev-a",
            originDeviceId = "dev-a"
        )
        assertFalse(TombstonePolicy.isExpired(tombstone, now))
    }

    @Test
    fun oldTombstone_isExpiredAfterDefaultRetention() {
        val now = 1_700_000_000_000L
        val tombstone = Tombstone(
            syncId = "s1",
            revision = 2,
            deletedAt = now - TimeUnit.DAYS.toMillis(91),
            deletedByDeviceId = "dev-a",
            originDeviceId = "dev-a"
        )
        assertTrue(TombstonePolicy.isExpired(tombstone, now))
    }

    @Test
    fun blocksResurrection_whenTombstoneRevisionIsNewerOrEqual() {
        val tombstone = Tombstone("s1", revision = 3, deletedAt = 50, deletedByDeviceId = "a", originDeviceId = "a")
        val live = liveRecord(syncId = "s1", revision = 3, content = "resurrect me")
        assertTrue(TombstonePolicy.blocksResurrection(tombstone, live))
        assertTrue(TombstonePolicy.blocksResurrection(tombstone, live.copy(revision = 2)))
        assertFalse(TombstonePolicy.blocksResurrection(tombstone, live.copy(revision = 4)))
    }

    @Test
    fun blocksResurrection_ignoresDifferentSyncIds() {
        val tombstone = Tombstone("s1", revision = 9, deletedAt = 50, deletedByDeviceId = "a", originDeviceId = "a")
        val live = liveRecord(syncId = "s2", revision = 1, content = "other")
        assertFalse(TombstonePolicy.blocksResurrection(tombstone, live))
    }
}
