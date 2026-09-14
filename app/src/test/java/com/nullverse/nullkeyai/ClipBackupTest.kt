package com.nullverse.nullkeyai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.clipboard.ClipBackup
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipBackupTest {

    private lateinit var db: NullKeyDatabase
    private lateinit var repo: ClipRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NullKeyDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = ClipRepository(db.clipDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun serializer_roundTripsAllFields() {
        val clips = listOf(
            Clip(content = "hello", tag = "Work", pinned = true, createdAt = 1000L),
            Clip(content = "content://media/1", isFile = true, mimeType = "image/png", createdAt = 2000L)
        )
        val json = ClipBackup.toJson(clips)
        val parsed = ClipBackup.fromJson(json)

        assertEquals(2, parsed.size)
        assertEquals("hello", parsed[0].content)
        assertEquals("Work", parsed[0].tag)
        assertTrue(parsed[0].pinned)
        assertEquals(1000L, parsed[0].createdAt)
        assertTrue(parsed[1].isFile)
        assertEquals("image/png", parsed[1].mimeType)
    }

    @Test
    fun fromJson_acceptsBareArray() {
        val parsed = ClipBackup.fromJson("""[{"content":"abc"},{"content":"def"}]""")
        assertEquals(2, parsed.size)
    }

    @Test
    fun fromJson_rejectsGarbage() {
        assertThrows(IllegalArgumentException::class.java) { ClipBackup.fromJson("not json") }
        assertThrows(IllegalArgumentException::class.java) { ClipBackup.fromJson("") }
    }

    @Test
    fun exportThenImport_roundTripsThroughRepository() = runBlocking {
        repo.capture("first clip")
        repo.capture("second clip", tag = "Coding")
        val json = repo.exportJson()

        // Fresh vault imports everything.
        db.clipDao().clear()
        val inserted = repo.importJson(json)
        assertEquals(2, inserted)
        assertEquals(2, repo.searchOnce("", false).size)
    }

    @Test
    fun import_skipsDuplicatesAndBlanks() = runBlocking {
        repo.capture("keep me")
        val json = ClipBackup.toJson(
            listOf(
                Clip(content = "keep me"),      // duplicate of existing
                Clip(content = "brand new"),    // should be inserted
                Clip(content = "   ")           // blank, ignored
            )
        )
        val inserted = repo.importJson(json)
        assertEquals(1, inserted)
        assertEquals(2, repo.searchOnce("", false).size)
    }
}
