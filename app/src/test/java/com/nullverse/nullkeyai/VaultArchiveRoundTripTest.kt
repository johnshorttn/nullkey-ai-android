package com.nullverse.nullkeyai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.clipboard.ClipCaptureRequest
import com.nullverse.nullkeyai.clipboard.ClipRepository
import com.nullverse.nullkeyai.clipboard.VaultArchive
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.security.PortableVaultCrypto
import com.nullverse.nullkeyai.security.VaultCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultArchiveRoundTripTest {

    private lateinit var context: Context
    private lateinit var db: NullKeyDatabase
    private lateinit var store: VaultAssetStore
    private lateinit var repo: ClipRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NullKeyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = VaultAssetStore(context)
        repo = ClipRepository(db.clipDao(), store, db.tagDao(), VaultCrypto())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun portableArchive_roundTripsClipsTagsAndAssets() = runBlocking {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
        val asset = store.importBytes(bytes, "preview.png")
        val id = repo.capture(
            ClipCaptureRequest(
                content = "screenshot",
                contentType = ClipContentType.IMAGE,
                isFile = true,
                mimeType = "image/png",
                localAssetPath = asset.relativePath
            )
        )!!
        repo.addTag(id, "Work")
        repo.addTag(id, "Coding")
        repo.capture("plain notes")

        val password = "correct-horse".toCharArray()
        val document = repo.exportPortableEncrypted(password)
        db.clipDao().clear()
        store.resolve(asset.relativePath)?.delete()

        val restored = repo.importPortableEncrypted(document, password.copyOf())
        assertEquals(2, restored)

        val clips = repo.searchOnce("", false).associateBy { it.content }
        assertTrue(clips.containsKey("screenshot"))
        assertTrue(clips.containsKey("plain notes"))

        val image = clips.getValue("screenshot")
        assertEquals("IMAGE", image.contentType)
        val names = repo.tagsForClip(image.id).map { it.name }.toSet()
        assertEquals(setOf("Work", "Coding"), names)
        val restoredFile = store.resolve(image.localAssetPath)
        assertNotNull(restoredFile)
        assertArrayEquals(bytes, restoredFile!!.readBytes())
    }

    @Test
    fun archiveBuildParse_roundTripsWithoutPasswordEnvelope() = runBlocking {
        val id = repo.capture("tagged clip")!!
        repo.addTag(id, "Personal")
        val clips = db.clipDao().allActive()
        val json = VaultArchive.build(clips, store, VaultCrypto()) { clipId ->
            db.tagDao().forClip(clipId).map { it.name }
        }
        val parsed = VaultArchive.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("tagged clip", parsed[0].clip.content)
        assertEquals(listOf("Personal"), parsed[0].tags)
        assertFalse(parsed[0].restoreProtected)
    }

    @Test
    fun portableEnvelope_rejectsWrongPassword() = runBlocking {
        repo.capture("secret-ish")
        val document = repo.exportPortableEncrypted("right-password".toCharArray())
        assertThrows(Exception::class.java) {
            runBlocking { repo.importPortableEncrypted(document, "wrong-password".toCharArray()) }
        }
    }

    @Test
    fun portableCrypto_roundTripsPlaintext() {
        val password = "abcdefgh".toCharArray()
        val encrypted = PortableVaultCrypto.encrypt("hello vault", password)
        assertFalse(encrypted.contains("hello vault"))
        assertEquals("hello vault", PortableVaultCrypto.decrypt(encrypted, password.copyOf()))
    }
}
