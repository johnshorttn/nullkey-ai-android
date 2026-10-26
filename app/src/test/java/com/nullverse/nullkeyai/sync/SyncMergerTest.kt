package com.nullverse.nullkeyai.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerTest {

    private val merger = SyncMerger()

    @Test
    fun insertsUnknownRemoteRecords() {
        val local = listOf(liveRecord(syncId = "a", content = "local"))
        val remote = listOf(liveRecord(syncId = "b", origin = "dev-b", content = "remote"))
        val result = merger.merge(local, remote)
        assertEquals(setOf("a", "b"), result.records.map { it.syncId }.toSet())
        assertEquals(1, result.applied)
        assertEquals(0, result.conflictsPreserved)
    }

    @Test
    fun concurrentEdits_resultContainsBothPayloads() {
        val local = listOf(liveRecord(revision = 2, modified = "dev-a", content = "A"))
        val remote = listOf(liveRecord(revision = 2, modified = "dev-b", content = "B"))
        val result = merger.merge(local, remote)
        val contents = result.records.mapNotNull { it.payload?.content }.toSet()
        assertEquals(setOf("A", "B"), contents)
        assertEquals(1, result.conflictsPreserved)
        assertEquals(2, result.records.size)
    }

    @Test
    fun mergeIsCommutativeForConcurrentEdits() {
        val a = liveRecord(revision = 2, modified = "dev-a", content = "A")
        val b = liveRecord(revision = 2, modified = "dev-b", content = "B")
        val left = merger.merge(listOf(a), listOf(b)).records.associateBy { it.syncId }
        val right = merger.merge(listOf(b), listOf(a)).records.associateBy { it.syncId }
        assertEquals(left.keys, right.keys)
        left.forEach { (id, record) ->
            assertEquals(record.payloadFingerprint(), right.getValue(id).payloadFingerprint())
        }
    }

    @Test
    fun tombstonePreventsApplyingEqualRevisionLiveIncomingAsKeep() {
        val local = listOf(tombstoneRecord(revision = 2))
        val remote = listOf(liveRecord(revision = 2, content = "resurrect"))
        val result = merger.merge(local, remote)
        val original = result.records.first { it.syncId == "clip-1" }
        assertTrue(original.isTombstone)
        assertTrue(result.records.any { it.payload?.content == "resurrect" })
    }
}
