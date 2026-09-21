package com.nullverse.nullkeyai.security

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-derived portable encryption for backup payloads.
 * This is intentionally separate from the non-exportable Android Keystore key.
 */
object PortableVaultCrypto {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private val random = SecureRandom()

    fun encrypt(plainText: String, password: CharArray): String {
        require(password.size >= 8) { "Backup password must be at least 8 characters" }
        val salt = ByteArray(16).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "NullKey AI")
            .put("format", 1)
            .put("encryption", "AES-256-GCM")
            .put("kdf", KDF)
            .put("iterations", ITERATIONS)
            .put("salt", b64(salt))
            .put("iv", b64(cipher.iv))
            .put("payload", b64(encrypted))
            .toString(2)
    }

    fun decrypt(document: String, password: CharArray): String {
        val obj = JSONObject(document)
        require(obj.optString("app") == "NullKey AI") { "Not a NullKey portable backup" }
        require(obj.optString("encryption") == "AES-256-GCM") { "Unsupported backup encryption" }
        val iterations = obj.optInt("iterations", 0)
        require(iterations in 100_000..2_000_000) { "Invalid backup KDF cost" }
        val salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val payload = Base64.decode(obj.getString("payload"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt, iterations), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload).toString(Charsets.UTF_8)
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
