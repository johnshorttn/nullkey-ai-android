package com.nullverse.nullkeyai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Room stack + repository on an actual (emulated) device, so
 * the emulator/KVM CI job validates the clip vault end-to-end, not just via
 * Robolectric on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class ClipDaoInstrumentedTest {

    private lateinit var db: NullKeyDatabase
    private lateinit var repo: ClipRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NullKeyDatabase::class.java
        ).build()
        repo = ClipRepository(db.clipDao(), tagDao = db.tagDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun captureThenSearch_findsClip() = runBlocking {
        repo.capture("on-device clip: meeting notes", tag = "Work")
        repo.capture("content://media/1", isFile = true, mimeType = "image/png")

        assertEquals(2, repo.searchOnce("", false).size)
        assertEquals(1, repo.searchOnce("meeting", false).size)

        val filesOnly = repo.searchOnce("", true)
        assertEquals(1, filesOnly.size)
        assertTrue(filesOnly[0].isFile)
    }

    @Test
    fun tagsAndTrashRestore_roundTripOnDevice() = runBlocking {
        val id = repo.capture("instrumented tagged clip")!!
        repo.addTag(id, "Personal")
        repo.addTag(id, "Work")
        assertEquals(listOf("Personal", "Work"), repo.tagsForClip(id).map { it.name })

        repo.removeTag(id, repo.tagsForClip(id).first { it.name == "Personal" }.id)
        assertEquals(listOf("Work"), repo.tagsForClip(id).map { it.name })

        repo.moveToTrash(id)
        assertEquals(0, repo.searchOnce("", false).size)
        repo.restore(id)
        assertEquals(1, repo.searchOnce("instrumented tagged", false).size)
        assertEquals(listOf("Work"), repo.tagsForClip(id).map { it.name })
    }
}
