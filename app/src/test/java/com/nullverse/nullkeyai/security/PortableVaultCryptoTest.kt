package com.nullverse.nullkeyai.security

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/**
 * Runs on the JDK's own AES-GCM, PBKDF2 and SecureRandom. Robolectric's default
 * Conscrypt mode loads a host-native BoringSSL library the first time
 * [PortableVaultCrypto] creates its SecureRandom; on CI that load intermittently
 * failed inside the class initializer (ExceptionInInitializerError ->
 * NoClassDefFoundError) and then left the Gradle test worker hung. The
 * envelope format, KDF parameters and tamper checks under test are the same
 * standard algorithms on either provider, so nothing is skipped or relaxed.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PortableVaultCryptoTest {
    @Test
    fun roundTripRestoresPlaintext() {
        val password = "correct horse battery staple".toCharArray()
        val plain = """{"vault":"NullKey","value":"secret"}"""
        val encrypted = PortableVaultCrypto.encrypt(plain, password)
        assertNotEquals(plain, encrypted)
        assertEquals(plain, PortableVaultCrypto.decrypt(encrypted, password))
        password.fill('\u0000')
    }

    @Test
    fun wrongPasswordFailsAuthentication() {
        val encrypted = PortableVaultCrypto.encrypt("secret", "correct-password".toCharArray())
        assertThrows(Exception::class.java) {
            PortableVaultCrypto.decrypt(encrypted, "wrong-password".toCharArray())
        }
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val password = "backup-password".toCharArray()
        val encrypted = PortableVaultCrypto.encrypt("secret", password)
        val obj = JSONObject(encrypted)
        val payload = obj.getString("payload")
        val replacement = if (payload.first() == 'A') 'B' else 'A'
        obj.put("payload", replacement + payload.drop(1))
        assertThrows(Exception::class.java) {
            PortableVaultCrypto.decrypt(obj.toString(), password)
        }
    }

    @Test
    fun rejectsUnsupportedEnvelopeMetadata() {
        val password = "backup-password".toCharArray()
        val encrypted = PortableVaultCrypto.encrypt("secret", password)

        val wrongFormat = JSONObject(encrypted).put("format", 999).toString()
        assertThrows(IllegalArgumentException::class.java) {
            PortableVaultCrypto.decrypt(wrongFormat, password)
        }

        val wrongKdf = JSONObject(encrypted).put("kdf", "unsupported").toString()
        assertThrows(IllegalArgumentException::class.java) {
            PortableVaultCrypto.decrypt(wrongKdf, password)
        }
    }

    @Test
    fun rejectsMalformedIvBeforeCipherUse() {
        val password = "backup-password".toCharArray()
        val obj = JSONObject(PortableVaultCrypto.encrypt("secret", password))
        obj.put("iv", "AA==")
        assertThrows(IllegalArgumentException::class.java) {
            PortableVaultCrypto.decrypt(obj.toString(), password)
        }
    }

    @Test
    fun rejectsWeakPassword() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableVaultCrypto.encrypt("secret", "short".toCharArray())
        }
    }
}
