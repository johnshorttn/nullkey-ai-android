package com.nullverse.nullkeyai.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncEnvelopeCodecTest {

    @Test
    fun roundTrip_preservesLiveAndTombstoneRecords() {
        val envelope = SyncEnvelope(
            producerDeviceId = "device-a",
            producedAt = 42L,
            records = listOf(
                liveRecord(syncId = "live", content = "hello", excluded = true),
                tombstoneRecord(syncId = "dead", revision = 4, updatedAt = 99L)
            )
        )
        val parsed = SyncEnvelopeCodec.fromJson(SyncEnvelopeCodec.toJson(envelope))
        assertEquals("device-a", parsed.producerDeviceId)
        assertEquals(42L, parsed.producedAt)
        assertEquals(2, parsed.records.size)
        assertEquals("hello", parsed.records[0].payload?.content)
        assertTrue(parsed.records[0].payload!!.syncExcluded)
        assertTrue(parsed.records[1].isTombstone)
        assertNull(parsed.records[1].payload)
        assertEquals(4L, parsed.records[1].revision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromJson_rejectsMissingKind() {
        SyncEnvelopeCodec.fromJson("""{"app":"NullKey AI","format":1,"producerDeviceId":"d","producedAt":1,"records":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromJson_rejectsUnsupportedFormat() {
        SyncEnvelopeCodec.fromJson(
            """{"app":"NullKey AI","kind":"sync-envelope","format":99,"producerDeviceId":"d","producedAt":1,"records":[]}"""
        )
    }

    @Test
    fun json_containsNoProviderEndpoints() {
        val json = SyncEnvelopeCodec.toJson(
            SyncEnvelope(producerDeviceId = "device-a", producedAt = 1L, records = listOf(liveRecord()))
        )
        assertTrue("https://" !in json)
        assertTrue("http://" !in json)
        assertTrue("api.nullkey" !in json.lowercase())
        assertTrue("Authorization" !in json)
    }
}
