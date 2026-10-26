package com.nullverse.nullkeyai.sync

import com.nullverse.nullkeyai.db.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipSyncMapperTest {

    @Test
    fun liveClip_roundTripsThroughMapper() {
        val clip = Clip(
            id = 9,
            content = "meeting notes",
            tag = "Work",
            pinned = true,
            createdAt = 100,
            notes = "follow up",
            updatedAt = 200,
            syncId = "sync-1",
            revision = 3,
            originDeviceId = "dev-a",
            modifiedByDeviceId = "dev-b",
            syncState = "PENDING",
            syncExcluded = true
        )
        val record = ClipSyncMapper.toRecord(clip, tags = listOf("Work", "Coding"))!!
        assertEquals("sync-1", record.syncId)
        assertEquals(3L, record.revision)
        assertEquals("dev-a", record.originDeviceId)
        assertEquals("dev-b", record.modifiedByDeviceId)
        assertEquals(listOf("Work", "Coding"), record.payload?.tags)
        assertTrue(record.payload!!.syncExcluded)

        val mapped = ClipSyncMapper.toClip(record, localId = 9)
        assertEquals(clip.content, mapped.content)
        assertEquals(clip.syncId, mapped.syncId)
        assertEquals(clip.revision, mapped.revision)
        assertEquals(clip.pinned, mapped.pinned)
        assertEquals(true, mapped.syncExcluded)
        assertNull(mapped.syncDeletedAt)
    }

    @Test
    fun clipWithoutDeviceId_isNotSyncableYet() {
        val clip = Clip(content = "local only", syncId = "x")
        assertNull(ClipSyncMapper.toRecord(clip))
    }

    @Test
    fun tombstoneClip_mapsToTombstoneRecord() {
        val clip = Clip(
            content = "",
            createdAt = 10,
            trashedAt = 50,
            updatedAt = 50,
            syncId = "gone",
            revision = 4,
            originDeviceId = "dev-a",
            modifiedByDeviceId = "dev-a",
            syncDeletedAt = 50,
            syncState = "TOMBSTONE"
        )
        val record = ClipSyncMapper.toRecord(clip)!!
        assertTrue(record.isTombstone)
        assertNull(record.payload)
        val roundTrip = ClipSyncMapper.toClip(record, localId = 1)
        assertEquals("gone", roundTrip.syncId)
        assertEquals(50L, roundTrip.syncDeletedAt)
        assertEquals("", roundTrip.content)
    }
}
