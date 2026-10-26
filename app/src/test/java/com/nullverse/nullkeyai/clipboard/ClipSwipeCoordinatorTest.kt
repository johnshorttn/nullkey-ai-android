package com.nullverse.nullkeyai.clipboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipSwipeCoordinatorTest {

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
    fun presentationMapsActionsToCancelSafeFlows() {
        assertEquals(ClipSwipePresentation.APPLY, ClipSwipeCoordinator.presentation(ClipSwipeAction.PIN))
        assertEquals(ClipSwipePresentation.APPLY, ClipSwipeCoordinator.presentation(ClipSwipeAction.PROTECT))
        assertEquals(ClipSwipePresentation.CONFIRM, ClipSwipeCoordinator.presentation(ClipSwipeAction.DELETE))
        assertEquals(ClipSwipePresentation.PROMPT_TAG, ClipSwipeCoordinator.presentation(ClipSwipeAction.TAG))
    }

    @Test
    fun blankTagInputIsCancelled() {
        assertEquals(null, ClipSwipeCoordinator.parseTagInput(null))
        assertEquals(null, ClipSwipeCoordinator.parseTagInput("   "))
        assertEquals("Work", ClipSwipeCoordinator.parseTagInput("  Work  "))
    }

    @Test
    fun applyPinTogglesPinnedState() = runBlocking {
        val clip = captured("pin me")
        val result = ClipSwipeCoordinator.apply(repo, ClipSwipeAction.PIN, clip)
        assertEquals(ClipSwipeApplyResult.Success, result)
        assertTrue(repo.searchOnce("", false).single().pinned)
    }

    @Test
    fun applyDeleteMovesClipToTrash() = runBlocking {
        val clip = captured("trash me")
        val result = ClipSwipeCoordinator.apply(repo, ClipSwipeAction.DELETE, clip)
        assertEquals(ClipSwipeApplyResult.Success, result)
        assertEquals(0, repo.searchOnce("", false).size)
        assertEquals(1, repo.trash().first().size)
    }

    @Test
    fun applyTagWithoutNameIsCancelled() = runBlocking {
        val clip = captured("needs a tag")
        val result = ClipSwipeCoordinator.apply(repo, ClipSwipeAction.TAG, clip, tagName = "  ")
        assertEquals(ClipSwipeApplyResult.Cancelled, result)
        assertTrue(repo.tagsForClip(clip.id).isEmpty())
    }

    @Test
    fun applyTagAttachesName() = runBlocking {
        val clip = captured("tag me")
        val result = ClipSwipeCoordinator.apply(repo, ClipSwipeAction.TAG, clip, tagName = "Work")
        assertEquals(ClipSwipeApplyResult.Success, result)
        assertEquals(listOf("Work"), repo.tagsForClip(clip.id).map { it.name })
    }

    @Test
    fun applyProtectWithoutCryptoFails() = runBlocking {
        val clip = captured("secret")
        val result = ClipSwipeCoordinator.apply(repo, ClipSwipeAction.PROTECT, clip)
        assertTrue(result is ClipSwipeApplyResult.Failure)
        assertEquals(false, repo.searchOnce("", false).single().protected)
    }

    private fun captured(content: String): Clip = runBlocking {
        val id = repo.capture(content)!!
        repo.searchOnce("", false).first { it.id == id }
    }
}
