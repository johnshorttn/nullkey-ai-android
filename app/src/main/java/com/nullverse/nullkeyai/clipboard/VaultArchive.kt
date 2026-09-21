package com.nullverse.nullkeyai.clipboard

import android.util.Base64
import com.nullverse.nullkeyai.db.Clip
import com.nullverse.nullkeyai.security.VaultCrypto
import org.json.JSONArray
import org.json.JSONObject

/**
 * Complete portable Vault payload before password encryption.
 * Assets are embedded as base64 so the outer PortableVaultCrypto envelope
 * authenticates metadata and file bytes together.
 */
object VaultArchive {
    const val FORMAT_VERSION = 1

    suspend fun build(
        clips: List<Clip>,
        assetStore: VaultAssetStore,
        crypto: VaultCrypto,
        tagsForClip: suspend (Long) -> List<String>
    ): String {
        val records = JSONArray()
        clips.forEach { clip ->
            val plainClip = if (clip.protected) clip.copy(
                content = crypto.decrypt(clip.content),
                notes = crypto.decrypt(clip.notes),
                protected = false
            ) else clip
            val record = JSONObject(ClipBackup.toJson(listOf(plainClip)))
                .getJSONArray("clips").getJSONObject(0)
            record.put("restoreProtected", clip.protected)
            record.put("tags", JSONArray(tagsForClip(clip.id)))
            assetStore.resolve(clip.localAssetPath)?.let { file ->
                val bytes = if (clip.protected && crypto.isEncryptedFile(file)) crypto.decryptFile(file)
                    else file.readBytes()
                record.put("assetName", file.name)
                record.put("assetBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            records.put(record)
        }
        return JSONObject()
            .put("app", ClipBackup.APP_TAG)
            .put("archiveFormat", FORMAT_VERSION)
            .put("clips", records)
            .toString()
    }

    data class Entry(val clip: Clip, val restoreProtected: Boolean, val tags: List<String>, val assetBytes: ByteArray?)

    fun parse(json: String): List<Entry> {
        val root = JSONObject(json)
        require(root.optString("app") == ClipBackup.APP_TAG) { "Not a NullKey archive" }
        require(root.optInt("archiveFormat") == FORMAT_VERSION) { "Unsupported archive format" }
        val array = root.getJSONArray("clips")
        val out = ArrayList<Entry>(array.length())
        for (i in 0 until array.length()) {
            val record = array.getJSONObject(i)
            val wrapped = JSONObject().put("clips", JSONArray().put(record)).toString()
            val clip = ClipBackup.fromJson(wrapped).firstOrNull() ?: continue
            val tagArray = record.optJSONArray("tags")
            val tags = buildList {
                if (tagArray != null) for (n in 0 until tagArray.length()) add(tagArray.getString(n))
            }
            val bytes = record.optString("assetBase64").takeIf { it.isNotBlank() }
                ?.let { Base64.decode(it, Base64.NO_WRAP) }
            out += Entry(clip, record.optBoolean("restoreProtected", false), tags, bytes)
        }
        return out
    }
}
