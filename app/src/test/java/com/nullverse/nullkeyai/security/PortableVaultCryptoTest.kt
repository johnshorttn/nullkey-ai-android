package com.nullverse.nullkeyai.security

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
    fun rejectsWeakPassword() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableVaultCrypto.encrypt("secret", "short".toCharArray())
        }
    }
}
