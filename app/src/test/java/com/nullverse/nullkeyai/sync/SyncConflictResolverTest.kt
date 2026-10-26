package com.nullverse.nullkeyai.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictResolverTest {

    @Test
    fun identicalVersions_keepLocal() {
        val local = liveRecord(content = "same")
        val remote = liveRecord(content = "same")
        val decision = SyncConflictResolver.resolve(local, remote) as MergeDecision.Keep
        assertEquals(local, decision.record)
    }

    @Test
    fun sequentialEdit_keepsHigherRevision() {
        val local = liveRecord(revision = 2, content = "older", updatedAt = 10)
        val remote = liveRecord(revision = 3, content = "newer", updatedAt = 20, modified = "dev-b")
        val decision = SyncConflictResolver.resolve(local, remote) as MergeDecision.Keep
        assertEquals("newer", decision.record.payload?.content)
        assertEquals(3L, decision.record.revision)
    }

    @Test
    fun concurrentLiveEdits_preserveBothWithDeterministicFork() {
        val a = liveRecord(revision = 2, modified = "dev-a", updatedAt = 100, content = "from A")
        val b = liveRecord(revision = 2, modified = "dev-b", updatedAt = 100, content = "from B")

        val ab = SyncConflictResolver.resolve(a, b) as MergeDecision.PreserveBoth
        val ba = SyncConflictResolver.resolve(b, a) as MergeDecision.PreserveBoth

        assertEquals(ab.canonical.syncId, ba.canonical.syncId)
        assertEquals(ab.fork.syncId, ba.fork.syncId)
        assertEquals("clip-1", ab.canonical.syncId)
        assertNotEquals("clip-1", ab.fork.syncId)
        val contents = setOf(ab.canonical.payload?.content, ab.fork.payload?.content)
        assertEquals(setOf("from A", "from B"), contents)
        assertEquals(SyncRecordState.CONFLICT, ab.fork.state)
        assertEquals(1L, ab.fork.revision)
    }

    @Test
    fun concurrentEdits_forkIdDoesNotDependOnLocalVsRemote() {
        val a = liveRecord(revision = 2, modified = "dev-a", updatedAt = 50, content = "alpha")
        val b = liveRecord(revision = 2, modified = "dev-b", updatedAt = 50, content = "beta")
        val expectedFork = SyncConflictResolver.forkSyncId("clip-1", SyncConflictResolver.newer(a, b).let {
            if (it.payload?.content == "alpha") b else a
        })
        val decision = SyncConflictResolver.resolve(a, b) as MergeDecision.PreserveBoth
        assertEquals(expectedFork, decision.fork.syncId)
    }

    @Test
    fun newerTombstone_winsOverOlderLive() {
        val live = liveRecord(revision = 2, content = "still here")
        val tomb = tombstoneRecord(revision = 3)
        val decision = SyncConflictResolver.resolve(live, tomb) as MergeDecision.Keep
        assertTrue(decision.record.isTombstone)
    }

    @Test
    fun newerLive_winsOverOlderTombstone() {
        val tomb = tombstoneRecord(revision = 2)
        val live = liveRecord(revision = 3, content = "edited after delete")
        val decision = SyncConflictResolver.resolve(tomb, live) as MergeDecision.Keep
        assertEquals("edited after delete", decision.record.payload?.content)
    }

    @Test
    fun concurrentDeleteAndEdit_keepsTombstoneAndForksLive() {
        val live = liveRecord(revision = 2, modified = "dev-a", content = "keep me")
        val tomb = tombstoneRecord(revision = 2, modified = "dev-b")
        val ab = SyncConflictResolver.resolve(live, tomb) as MergeDecision.PreserveBoth
        val ba = SyncConflictResolver.resolve(tomb, live) as MergeDecision.PreserveBoth
        assertTrue(ab.canonical.isTombstone)
        assertEquals("keep me", ab.fork.payload?.content)
        assertEquals(ab.fork.syncId, ba.fork.syncId)
        assertEquals("clip-1", ab.canonical.syncId)
    }

    @Test
    fun twoTombstones_keepNewerRevision() {
        val older = tombstoneRecord(revision = 2, updatedAt = 10)
        val newer = tombstoneRecord(revision = 4, updatedAt = 40, modified = "dev-b")
        val decision = SyncConflictResolver.resolve(older, newer) as MergeDecision.Keep
        assertEquals(4L, decision.record.revision)
    }

    @Test
    fun samePayloadDifferentRevision_notAConflict() {
        val local = liveRecord(revision = 1, content = "note")
        val remote = liveRecord(revision = 2, content = "note", modified = "dev-b", updatedAt = 5_000)
        val decision = SyncConflictResolver.resolve(local, remote) as MergeDecision.Keep
        assertEquals(2L, decision.record.revision)
    }
}
