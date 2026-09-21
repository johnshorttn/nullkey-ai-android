package com.nullverse.nullkeyai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.clipboard.ClipCaptureRequest
import com.nullverse.nullkeyai.clipboard.VaultArchive
import com.nullverse.nullkeyai.clipboard.VaultAssetStore
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.security.PortableVaultCrypto
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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NullKeyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = VaultAssetStore(context)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun portableEnvelope_rejectsWrongPassword() {
        val document = PortableVaultCrypto.encrypt("secret-ish", "right-password".toCharArray())
        assertThrows(Exception::class.java) {
            PortableVaultCrypto.decrypt(document, "wrong-password".toCharArray())
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
