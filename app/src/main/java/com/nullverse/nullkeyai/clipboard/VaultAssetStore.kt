package com.nullverse.nullkeyai.clipboard

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Owns durable app-private copies of clipboard/share assets.
 *
 * Clipboard content URIs are frequently temporary. NullKey copies readable assets
 * immediately so a Vault item does not depend on another app keeping its URI alive.
 */
class VaultAssetStore(private val context: Context) {
    private val root: File by lazy {
        File(context.filesDir, "vault/assets").apply { mkdirs() }
    }

    data class StoredAsset(val relativePath: String, val sizeBytes: Long)

    fun importUri(uri: Uri, mimeType: String?, maxBytes: Long = DEFAULT_MAX_BYTES): StoredAsset {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
        val name = UUID.randomUUID().toString() + if (extension == null) "" else ".$extension"
        val target = File(root, name)
        val temp = File(root, "$name.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("Vault asset exceeds size limit")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("Unable to open clipboard asset")
            if (!temp.renameTo(target)) throw IOException("Unable to finalize Vault asset")
            return StoredAsset(relativePath = "vault/assets/$name", sizeBytes = target.length())
        } catch (t: Throwable) {
            temp.delete()
            target.delete()
            throw t
        }
    }

    fun resolve(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        val candidate = File(context.filesDir, relativePath).canonicalFile
        val allowedRoot = root.canonicalFile
        if (!candidate.path.startsWith(allowedRoot.path + File.separator)) return null
        return candidate.takeIf { it.isFile }
    }

    fun delete(relativePath: String?): Boolean {
        if (relativePath.isNullOrBlank()) return false
        return resolve(relativePath)?.delete() ?: false
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 100L * 1024L * 1024L
    }
}
