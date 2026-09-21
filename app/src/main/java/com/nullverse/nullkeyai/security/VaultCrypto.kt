package com.nullverse.nullkeyai.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.File

/**
 * Device-local authenticated encryption for protected Vault fields.
 * Keys are generated inside Android Keystore and are never exported.
 */
class VaultCrypto {
    private val keyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    private fun key(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        if (!isEncrypted(value)) return value
        val parts = value.removePrefix(PREFIX).split(":", limit = 2)
        require(parts.size == 2) { "Invalid protected Vault payload" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    fun encryptFileBytes(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain)
        return java.io.ByteArrayOutputStream().use { out ->
            out.write(FILE_MAGIC)
            out.write(cipher.iv.size)
            out.write(cipher.iv)
            out.write(encrypted)
            out.toByteArray()
        }
    }

    fun encryptFileInPlace(file: File) {
        val encrypted = encryptFileBytes(file.readBytes())
        val temp = File(file.parentFile, file.name + ".encpart")
        temp.writeBytes(encrypted)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IllegalStateException("Unable to finalize encrypted Vault asset")
        }
    }

    fun decryptFile(file: File): ByteArray {
        val bytes = file.readBytes()
        require(bytes.size > FILE_MAGIC.size + 1 && bytes.copyOfRange(0, FILE_MAGIC.size).contentEquals(FILE_MAGIC)) {
            "Vault asset is not encrypted"
        }
        val ivSize = bytes[FILE_MAGIC.size].toInt() and 0xff
        val ivStart = FILE_MAGIC.size + 1
        val ivEnd = ivStart + ivSize
        require(ivSize in 12..32 && ivEnd < bytes.size) { "Invalid encrypted Vault asset" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(ivStart, ivEnd)))
        return cipher.doFinal(bytes.copyOfRange(ivEnd, bytes.size))
    }

    fun isEncryptedFile(file: File): Boolean {
        if (!file.isFile || file.length() < FILE_MAGIC.size) return false
        return file.inputStream().use { input ->
            val header = ByteArray(FILE_MAGIC.size)
            input.read(header) == header.size && header.contentEquals(FILE_MAGIC)
        }
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "nullkey_vault_local_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "nkenc:v1:"
        private val FILE_MAGIC = byteArrayOf(0x4e, 0x4b, 0x46, 0x31)
    }
}
