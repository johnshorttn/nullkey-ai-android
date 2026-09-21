package com.nullverse.nullkeyai

import com.nullverse.nullkeyai.security.PortableVaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultArchiveRoundTripTest {

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
