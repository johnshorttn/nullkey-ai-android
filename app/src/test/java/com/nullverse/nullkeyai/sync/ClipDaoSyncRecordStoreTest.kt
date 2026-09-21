package com.nullverse.nullkeyai.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ClipDaoSyncRecordStoreTest {

    private lateinit var db: NullKeyDatabase
    private lateinit var identity: DeviceIdentity
    private lateinit var repo: ClipRepository
    private lateinit var store: ClipDaoSyncRecordStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NullKeyDatabase::class.java
        ).allowMainThreadQueries().build()
        identity = DeviceIdentity(MemoryDeviceIdStore("device-under-test"))
        repo = ClipRepository(db.clipDao(), deviceIdentity = identity)
        store = ClipDaoSyncRecordStore(db.clipDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun capture_stampsStableDeviceIdentityAndSyncId() = runBlocking {
        val id = repo.capture("hello sync")!!
        val clip = db.clipDao().byId(id)!!
        assertEquals("device-under-test", clip.originDeviceId)
        assertEquals("device-under-test", clip.modifiedByDeviceId)
        assertTrue(clip.syncId.isNotBlank())
        assertEquals(1L, clip.revision)
        val record = store.find(clip.syncId)
        assertNotNull(record)
        assertEquals("hello sync", record!!.payload?.content)
    }

    @Test
    fun pin_incrementsRevisionAndMarksPending() = runBlocking {
        val id = repo.capture("pin me")!!
        repo.setPinned(id, true)
        val clip = db.clipDao().byId(id)!!
        assertEquals(2L, clip.revision)
        assertEquals("PENDING", clip.syncState)
        assertTrue(clip.pinned)
    }

    @Test
    fun purgeExpiredTrash_writesTombstoneInsteadOfHardDelete() = runBlocking {
        val keep = repo.capture("recent")!!
        val old = repo.capture("ancient")!!
        val now = System.currentTimeMillis()
        db.clipDao().moveToTrash(keep, now)
        db.clipDao().moveToTrash(old, now - TimeUnit.DAYS.toMillis(40))

        val converted = repo.purgeExpiredTrash(retentionDays = 30, now = now)
        assertEquals(1, converted)
        assertEquals(1, repo.trash().first().size)

        val tombstones = store.tombstones()
        assertEquals(1, tombstones.size)
        val gone = db.clipDao().byId(old)!!
        assertEquals("TOMBSTONE", gone.syncState)
        assertNotNull(gone.syncDeletedAt)
        assertEquals("", gone.content)
        assertEquals(0, repo.searchOnce("", false).size)
    }

    @Test
    fun upsert_insertsRemoteRecordBySyncId() = runBlocking {
        val remote = liveRecord(syncId = "remote-1", origin = "other-device", content = "from elsewhere")
        store.upsert(remote)
        val found = db.clipDao().bySyncId("remote-1")
        assertEquals("from elsewhere", found?.content)
        assertEquals("other-device", found?.originDeviceId)
    }

    @Test
    fun deviceIdentity_prefsSurviveNewInstance() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = DeviceIdentity.from(context)
        val id = first.current()
        val second = DeviceIdentity.from(context)
        assertEquals(id, second.current())
        assertTrue(id.isNotBlank())
    }
}
