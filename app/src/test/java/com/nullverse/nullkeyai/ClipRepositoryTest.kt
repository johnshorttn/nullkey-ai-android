package com.nullverse.nullkeyai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.clipboard.ClipCaptureRequest
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ClipRepositoryTest {

    private lateinit var db: NullKeyDatabase
    private lateinit var repo: ClipRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NullKeyDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = ClipRepository(db.clipDao(), tagDao = db.tagDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun capture_storesTextClip() = runBlocking {
        val id = repo.capture("hello world")
        assertTrue(id != null && id > 0)
        val results = repo.searchOnce("", false)
        assertEquals(1, results.size)
        assertEquals("hello world", results[0].content)
        assertFalse(results[0].isFile)
    }

    @Test
    fun capture_ignoresBlank() = runBlocking {
        assertNull(repo.capture("   "))
        assertEquals(0, repo.searchOnce("", false).size)
    }

    @Test
    fun capture_deduplicatesConsecutiveIdentical() = runBlocking {
        repo.capture("same")
        val second = repo.capture("same")
        assertNull(second)
        assertEquals(1, repo.searchOnce("", false).size)
    }

    @Test
    fun search_filtersByQueryAcrossContentAndTag() = runBlocking {
        repo.capture("meeting notes")
        repo.capture("grocery list")
        repo.capture("secret token", tag = "Work")

        assertEquals(1, repo.searchOnce("grocery", false).size)
        assertEquals(1, repo.searchOnce("Work", false).size)
        assertEquals(3, repo.searchOnce("", false).size)
    }

    @Test
    fun search_filesOnlyReturnsOnlyFileClips() = runBlocking {
        repo.capture("plain text")
        repo.capture("content://media/1", isFile = true, mimeType = "image/png")

        val filesOnly = repo.searchOnce("", true)
        assertEquals(1, filesOnly.size)
        assertTrue(filesOnly[0].isFile)
        assertEquals("image/png", filesOnly[0].mimeType)
    }

    @Test
    fun moveToTrash_removesFromActiveSearch() = runBlocking {
        val id = repo.capture("throw me away")!!
        repo.moveToTrash(id)
        assertEquals(0, repo.searchOnce("", false).size)
        assertEquals(1, repo.trash().first().size)
    }

    @Test
    fun purgeExpiredTrash_removesOnlyOldTrash() = runBlocking {
        val keep = repo.capture("recent")!!
        val old = repo.capture("ancient")!!
        val now = System.currentTimeMillis()

        // Recently trashed
        db.clipDao().moveToTrash(keep, now)
        // Trashed 40 days ago (beyond the 30-day retention)
        db.clipDao().moveToTrash(old, now - TimeUnit.DAYS.toMillis(40))

        val purged = repo.purgeExpiredTrash(retentionDays = 30, now = now)
        assertEquals(1, purged)
        assertEquals(1, repo.trash().first().size)
    }

    @Test
    fun pinnedClipsSortFirst() = runBlocking {
        repo.capture("first")
        val second = repo.capture("second")!!
        repo.setPinned(second, true)

        val results = repo.searchOnce("", false)
        assertEquals("second", results[0].content)
        assertTrue(results[0].pinned)
    }

    @Test
    fun addAndRemoveTag_updatesRelationOnly() = runBlocking {
        val id = repo.capture("tagged")!!
        repo.addTag(id, "Work")
        repo.addTag(id, "Coding")
        repo.addTag(id, "  Work  ")
        assertEquals(listOf("Coding", "Work"), repo.tagsForClip(id).map { it.name })
        val workId = repo.tagsForClip(id).first { it.name == "Work" }.id
        repo.removeTag(id, workId)
        assertEquals(listOf("Coding"), repo.tagsForClip(id).map { it.name })
    }

    @Test
    fun restore_preservesTags() = runBlocking {
        val id = repo.capture("bring me back")!!
        repo.addTag(id, "Personal")
        repo.moveToTrash(id)
        repo.restore(id)
        assertEquals(listOf("Personal"), repo.tagsForClip(id).map { it.name })
        assertEquals(1, repo.searchOnce("bring me back", false).size)
    }

    @Test
    fun setOcrText_isSearchableUntilTheClipIsProtected() = runBlocking {
        val id = repo.capture(
            ClipCaptureRequest(
                content = "content://image",
                contentType = ClipContentType.IMAGE,
                isFile = true,
                localAssetPath = "vault/assets/x.png",
            )
        )!!
        repo.setOcrText(id, "boarding pass")
        assertEquals(1, repo.searchOnce("boarding", false).size)
        assertEquals("boarding pass", db.clipDao().byId(id)!!.ocrText)

        val locked = db.clipDao().byId(id)!!.copy(protected = true)
        db.clipDao().update(locked)
        repo.setOcrText(id, "should not stick")
        assertEquals("boarding pass", db.clipDao().byId(id)!!.ocrText)
    }
}
