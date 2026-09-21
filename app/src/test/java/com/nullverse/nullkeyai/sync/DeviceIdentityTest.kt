package com.nullverse.nullkeyai.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceIdentityTest {

    @Test
    fun current_generatesOnceAndReuses() {
        val store = MemoryDeviceIdStore()
        var calls = 0
        val identity = DeviceIdentity(store) {
            calls++
            "device-$calls"
        }
        assertEquals("device-1", identity.current())
        assertEquals("device-1", identity.current())
        assertEquals(1, calls)
        assertEquals("device-1", store.read())
    }

    @Test
    fun current_reusesExistingStoreValue() {
        val store = MemoryDeviceIdStore("already-set")
        val identity = DeviceIdentity(store) { "should-not-run" }
        assertEquals("already-set", identity.current())
    }

    @Test
    fun current_isStableAcrossInstancesSharingStore() {
        val store = MemoryDeviceIdStore()
        val first = DeviceIdentity(store) { "stable-id" }
        first.current()
        val second = DeviceIdentity(store) { "other-id" }
        assertEquals("stable-id", second.current())
    }

    @Test
    fun current_twoStoresRemainIndependent() {
        val a = DeviceIdentity(MemoryDeviceIdStore()) { "aaa" }
        val b = DeviceIdentity(MemoryDeviceIdStore()) { "bbb" }
        assertEquals("aaa", a.current())
        assertEquals("bbb", b.current())
        assertNotEquals(a.current(), b.current())
    }

    @Test
    fun memoryStore_roundTrips() {
        val store = MemoryDeviceIdStore()
        store.write("x")
        assertEquals("x", store.read())
        assertSame("x", store.read())
    }
}
