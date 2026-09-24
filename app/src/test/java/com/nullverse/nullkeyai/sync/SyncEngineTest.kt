package com.nullverse.nullkeyai.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SyncEngineTest {

    @Test
    fun twoDevices_exchangeNewRecordsThroughMemoryTransport() = runBlocking {
        val transport = InMemorySyncTransport()
        val deviceA = DeviceIdentity(MemoryDeviceIdStore("device-a"))
        val deviceB = DeviceIdentity(MemoryDeviceIdStore("device-b"))
        val storeA = InMemorySyncRecordStore(
            listOf(liveRecord(syncId = "from-a", origin = "device-a", content = "hello from A"))
        )
        val storeB = InMemorySyncRecordStore(
            listOf(liveRecord(syncId = "from-b", origin = "device-b", content = "hello from B"))
        )

        val engineA = SyncEngine(deviceA, storeA, transport, clock = { 10L })
        val engineB = SyncEngine(deviceB, storeB, transport, clock = { 20L })

        engineA.sync()
        engineB.sync()
        engineA.sync()

        val contentsA = storeA.all().mapNotNull { it.payload?.content }.toSet()
        val contentsB = storeB.all().mapNotNull { it.payload?.content }.toSet()
        assertEquals(setOf("hello from A", "hello from B"), contentsA)
        assertEquals(contentsA, contentsB)
        assertEquals("memory", transport.providerId)
    }

    @Test
    fun excludedRecords_areNotPushed() = runBlocking {
        val transport = InMemorySyncTransport()
        val store = InMemorySyncRecordStore(
            listOf(
                liveRecord(syncId = "public", content = "ok"),
                liveRecord(syncId = "secret", content = "hidden", excluded = true)
            )
        )
        val engine = SyncEngine(DeviceIdentity(MemoryDeviceIdStore("dev")), store, transport)
        val report = engine.sync()
        assertEquals(1, report.pushed)
        val pulled = transport.pull()
        assertEquals(listOf("public"), pulled.records.map { it.syncId })
        assertTrue(store.find("secret") != null)
    }

    @Test
    fun tombstones_arePushedAndDoNotResurrectAfterPurgeWindow() = runBlocking {
        val transport = InMemorySyncTransport()
        val now = 5_000_000L
        val store = InMemorySyncRecordStore(
            listOf(tombstoneRecord(syncId = "gone", revision = 2, updatedAt = now))
        )
        val engine = SyncEngine(
            DeviceIdentity(MemoryDeviceIdStore("dev")),
            store,
            transport,
            clock = { now }
        )
        engine.sync()
        assertTrue(transport.pull().records.single().isTombstone)

        val purged = engine.purgeExpiredTombstones(
            now = now + TimeUnit.DAYS.toMillis(91),
            retentionDays = 90
        )
        assertEquals(1, purged)
        assertFalse(store.all().any { it.syncId == "gone" })
    }

    @Test
    fun concurrentEdits_bothDevicesEndWithBothRecords() = runBlocking {
        val transport = InMemorySyncTransport()
        val sharedId = "shared"
        val storeA = InMemorySyncRecordStore(
            listOf(liveRecord(syncId = sharedId, revision = 2, origin = "device-a", content = "A-edit"))
        )
        val storeB = InMemorySyncRecordStore(
            listOf(liveRecord(syncId = sharedId, revision = 2, origin = "device-b", modified = "device-b", content = "B-edit"))
        )
        val engineA = SyncEngine(DeviceIdentity(MemoryDeviceIdStore("device-a")), storeA, transport)
        val engineB = SyncEngine(DeviceIdentity(MemoryDeviceIdStore("device-b")), storeB, transport)

        val reportA = engineA.sync()
        val reportB = engineB.sync()
        engineA.sync()

        assertEquals(1, reportB.conflictsPreserved)
        val contentsA = storeA.all().mapNotNull { it.payload?.content }.toSet()
        val contentsB = storeB.all().mapNotNull { it.payload?.content }.toSet()
        assertEquals(setOf("A-edit", "B-edit"), contentsA)
        assertEquals(contentsA, contentsB)
        assertEquals("memory", reportA.providerId)
    }
}
