package com.nullverse.nullkeyai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.clipboard.ClipBackup
import com.nullverse.nullkeyai.clipboard.ClipCaptureRequest
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.ImportFailureText
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
class BackupImportAndTrashPurgeTest {

    private lateinit var db: NullKeyDatabase
    private lateinit var store: VaultAssetStore
    private lateinit var repo: ClipRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NullKeyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = VaultAssetStore(context)
        repo = ClipRepository(db.clipDao(), store, db.tagDao())
    }

    @After
    fun tearDown() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "vault").deleteRecursively()
    }

    @Test
    fun plainImport_restoresTrashedSyncIdInsteadOfFailing() = runBlocking {
        val kept = repo.capture("stay active")!!
        val trashed = repo.capture("bring me back")!!
        val json = repo.exportJson()
        repo.moveToTrash(trashed)

        val restored = repo.importJson(json)

        assertEquals(1, restored)
        assertEquals(setOf("stay active", "bring me back"), repo.searchOnce("", false).map { it.content }.toSet())
        assertEquals(0, repo.trash().first().size)
        assertEquals(kept, db.clipDao().byId(kept)!!.id)
        assertNull(db.clipDao().byId(trashed)!!.syncDeletedAt)
        assertNull(db.clipDao().byId(trashed)!!.trashedAt)
    }

    @Test
    fun plainImport_skipsActiveClipsAndDoesNotOverwriteLocalEdits() = runBlocking {
        val id = repo.capture("original")!!
        val json = repo.exportJson()
        repo.setContent(id, "edited locally")

        assertEquals(0, repo.importJson(json))
        assertEquals("edited locally", repo.searchOnce("", false).single().content)
    }

    @Test
    fun plainImport_restoresTombstoneLeftByExpiredTrashPurge() = runBlocking {
        val id = repo.capture("ancient")!!
        val json = repo.exportJson()
        val now = System.currentTimeMillis()
        db.clipDao().moveToTrash(id, now - TimeUnit.DAYS.toMillis(40))
        assertEquals(1, repo.purgeExpiredTrash(retentionDays = 30, now = now))
        assertEquals(1, db.clipDao().syncTombstones().size)

        val restored = repo.importJson(json)

        assertEquals(1, restored)
        val clip = repo.searchOnce("", false).single()
        assertEquals("ancient", clip.content)
        assertNull(clip.trashedAt)
        assertNull(clip.syncDeletedAt)
        assertEquals(0, db.clipDao().syncTombstones().size)
    }

    @Test
    fun plainImport_afterEmptyTrashInsertsAFreshRow() = runBlocking {
        val id = repo.capture("gone then imported")!!
        val json = repo.exportJson()
        val syncId = db.clipDao().byId(id)!!.syncId
        repo.moveToTrash(id)
        assertEquals(1, repo.emptyTrash())

        val restored = repo.importJson(json)

        assertEquals(1, restored)
        assertNull(db.clipDao().byId(id))
        assertEquals("gone then imported", repo.searchOnce("", false).single().content)
        assertEquals(syncId, repo.searchOnce("", false).single().syncId)
    }

    @Test
    fun plainImport_rejectsBrokenJsonWithTheParserMessage() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ClipBackup.fromJson("""{"clips":""")
        }
        assertFalse(error.message.isNullOrBlank())
    }

    @Test
    fun plainImport_acceptsLeadingBom() {
        val json = "\uFEFF" + ClipBackup.toJson(listOf(com.nullverse.nullkeyai.db.Clip(content = "bom clip")))
        assertEquals("bom clip", ClipBackup.fromJson(json).single().content)
    }

    @Test
    fun secureImport_roundTripsTextTagsAndFileBytes() = runBlocking {
        val asset = store.importBytes("file-body".toByteArray(), "note.txt")
        val id = repo.capture(
            ClipCaptureRequest(
                content = "file clip",
                contentType = ClipContentType.FILE,
                isFile = true,
                localAssetPath = asset.relativePath
            )
        )!!
        repo.capture("text clip")
        repo.addTag(id, "Work")
        val password = "correct-horse".toCharArray()
        val document = repo.exportPortableEncrypted(password)

        db.clipDao().clear()
        val restored = repo.importPortableEncrypted(document, password.copyOf())

        assertEquals(2, restored)
        val clips = repo.searchOnce("", false).associateBy { it.content }
        assertEquals(setOf("file clip", "text clip"), clips.keys)
        val file = clips.getValue("file clip")
        assertEquals(listOf("Work"), repo.tagsForClip(file.id).map { it.name })
        val restoredFile = store.resolve(file.localAssetPath)
        assertNotNull(restoredFile)
        assertEquals("file-body", restoredFile!!.readText())
    }

    @Test
    fun secureImport_restoresTrashedClipAndRejectsWrongPassword() = runBlocking {
        val id = repo.capture("secret note")!!
        val password = "correct-horse".toCharArray()
        val document = repo.exportPortableEncrypted(password)
        repo.moveToTrash(id)

        val restored = repo.importPortableEncrypted(document, password.copyOf())
        assertEquals(1, restored)
        assertEquals("secret note", repo.searchOnce("", false).single().content)
        assertEquals(0, repo.trash().first().size)

        assertThrows(AEADBadTagException::class.java) {
            runBlocking {
                repo.importPortableEncrypted(document, "wrong-password".toCharArray())
            }
        }
        Unit
    }

    @Test
    fun secureImport_skipsClipsAlreadyActive() = runBlocking {
        repo.capture("already here")
        val password = "correct-horse".toCharArray()
        val document = repo.exportPortableEncrypted(password)

        assertEquals(0, repo.importPortableEncrypted(document, password.copyOf()))
        assertEquals(1, repo.searchOnce("", false).size)
    }

    @Test
    fun emptyTrash_deletesOnlyVisibleTrashAndItsAsset() = runBlocking {
        val asset = store.importBytes("doomed".toByteArray(), "gone.txt")
        val active = repo.capture("keep me")!!
        val trashed = repo.capture(
            ClipCaptureRequest(
                content = "delete me",
                isFile = true,
                localAssetPath = asset.relativePath,
                contentType = ClipContentType.FILE
            )
        )!!
        repo.moveToTrash(trashed)
        assertTrue(store.resolve(asset.relativePath)!!.isFile)

        val removed = repo.emptyTrash()

        assertEquals(1, removed)
        assertEquals(0, repo.trash().first().size)
        assertEquals(0, repo.emptyTrash())
        assertEquals("keep me", repo.searchOnce("", false).single().content)
        assertEquals(active, repo.searchOnce("", false).single().id)
        assertNull(db.clipDao().byId(trashed))
        assertNull(store.resolve(asset.relativePath))
    }

    @Test
    fun emptyTrash_leavesSyncTombstonesAlone() = runBlocking {
        val recent = repo.capture("recent trash")!!
        val old = repo.capture("already tombstoned")!!
        val now = System.currentTimeMillis()
        db.clipDao().moveToTrash(recent, now)
        db.clipDao().moveToTrash(old, now - TimeUnit.DAYS.toMillis(40))
        assertEquals(1, repo.purgeExpiredTrash(retentionDays = 30, now = now))

        assertEquals(1, repo.emptyTrash())

        assertEquals(0, repo.trash().first().size)
        assertNull(db.clipDao().byId(recent))
        assertEquals(1, db.clipDao().syncTombstones().size)
        assertEquals(old, db.clipDao().syncTombstones().single().id)
    }

    @Test
    fun importFailureText_usesCauseMessageAndCollapsesWhitespace() {
        val error = RuntimeException("   ", IllegalStateException("UNIQUE constraint failed:\nclips.syncId"))
        assertEquals("UNIQUE constraint failed: clips.syncId", ImportFailureText.detail(error))
        assertNull(ImportFailureText.detail(IllegalStateException("   ")))
    }

    @Test
    fun trashScreenExposesEmptyAction() {
        val root = repoRoot()
        val layout = File(root, "app/src/main/res/layout/activity_trash.xml").readText()
        val activity = File(root, "app/src/main/java/com/nullverse/nullkeyai/ui/TrashActivity.kt").readText()
        assertTrue(layout.contains("@+id/btn_empty_trash"))
        assertTrue(layout.contains("@string/empty_trash"))
        assertTrue(activity.contains("repository.emptyTrash()"))
        assertTrue(activity.contains("R.string.trash_purge_failed_detail"))
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("tools:node=\"remove\""))
    }

    private fun repoRoot(): File {
        val cwd = File(".").canonicalFile
        val candidates = listOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()
        return candidates.firstOrNull { File(it, "app/proguard-rules.pro").isFile }
            ?: error("Could not locate repo root from $cwd")
    }
}
