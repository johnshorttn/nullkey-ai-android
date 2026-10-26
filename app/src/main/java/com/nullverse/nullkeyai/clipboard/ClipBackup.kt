package com.nullverse.nullkeyai.clipboard

import com.nullverse.nullkeyai.db.Clip
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes the clip vault to/from a portable JSON document so users can export
 * their clipped saves and import them on another device or after a reinstall.
 *
 * Format:
 * ```
 * { "app": "NullKey AI", "format": 1, "exportedAt": <epochMs>,
 *   "clips": [ { "content":…, "isFile":…, "mimeType":…, "tag":…,
 *               "pinned":…, "createdAt":… }, … ] }
 * ```
 */
object ClipBackup {

    const val APP_TAG = "NullKey AI"
    const val FORMAT_VERSION = 2

    fun toJson(clips: List<Clip>, exportedAt: Long = System.currentTimeMillis()): String {
        val array = JSONArray()
        for (clip in clips) {
            val obj = JSONObject()
                // Protected payloads remain ciphertext in portable JSON; never decrypt during export.
                .put("content", clip.content)
                .put("isFile", clip.isFile)
                .put("pinned", clip.pinned)
                .put("createdAt", clip.createdAt)
                .put("contentType", clip.contentType)
                .put("notes", clip.notes)
                .put("protectionPayload", if (clip.protected) "ANDROID_KEYSTORE_AES_GCM_V1" else "NONE")
                .put("protected", clip.protected)
                .put("captureMethod", clip.captureMethod)
                .put("sourceConfidence", clip.sourceConfidence)
                .put("updatedAt", clip.updatedAt)
                .put("syncId", clip.syncId)
                .put("revision", clip.revision)
                .put("syncState", clip.syncState)
                .put("syncExcluded", clip.syncExcluded)
            clip.mimeType?.let { obj.put("mimeType", it) }
            clip.tag?.let { obj.put("tag", it) }
            clip.localAssetPath?.let { obj.put("localAssetPath", it) }
            clip.sourcePackage?.let { obj.put("sourcePackage", it) }
            clip.sourceAppLabel?.let { obj.put("sourceAppLabel", it) }
            clip.sourceUri?.let { obj.put("sourceUri", it) }
            clip.ocrText?.let { obj.put("ocrText", it) }
            clip.originDeviceId?.let { obj.put("originDeviceId", it) }
            clip.modifiedByDeviceId?.let { obj.put("modifiedByDeviceId", it) }
            array.put(obj)
        }
        return JSONObject()
            .put("app", APP_TAG)
            .put("format", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("clips", array)
            .toString(2)
    }

    /**
     * Parses an exported document into clips. Accepts either the wrapped object
     * form above or a bare JSON array of clips. Throws [IllegalArgumentException]
     * if the text is not valid NullKey backup JSON.
     */
    fun fromJson(text: String): List<Clip> {
        try {
            return parse(text)
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException(e.message ?: "Not a NullKey backup file", e)
        }
    }

    private fun parse(text: String): List<Clip> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) throw IllegalArgumentException("Empty backup")
        val array: JSONArray = when (trimmed.first()) {
            '[' -> JSONArray(trimmed)
            '{' -> JSONObject(trimmed).optJSONArray("clips")
                ?: throw IllegalArgumentException("Missing 'clips' array")
            else -> throw IllegalArgumentException("Not a NullKey backup file")
        }
        val result = ArrayList<Clip>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val content = obj.optString("content", "")
            if (content.isBlank()) continue
            val isProtected = obj.optBoolean("protected", false)
            val protectionPayload = obj.optString("protectionPayload", "NONE")
            if (isProtected && protectionPayload == "ANDROID_KEYSTORE_AES_GCM_V1") {
                throw DeviceBoundProtectedImportException()
            }
            result.add(
                Clip(
                    content = content,
                    isFile = obj.optBoolean("isFile", false),
                    mimeType = obj.optStringOrNull("mimeType"),
                    tag = obj.optStringOrNull("tag"),
                    pinned = obj.optBoolean("pinned", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    contentType = obj.optString("contentType", "TEXT"),
                    notes = obj.optString("notes", ""),
                    protected = isProtected,
                    localAssetPath = obj.optStringOrNull("localAssetPath"),
                    sourcePackage = obj.optStringOrNull("sourcePackage"),
                    sourceAppLabel = obj.optStringOrNull("sourceAppLabel"),
                    sourceUri = obj.optStringOrNull("sourceUri"),
                    captureMethod = obj.optString("captureMethod", "UNKNOWN"),
                    sourceConfidence = obj.optString("sourceConfidence", "UNKNOWN"),
                    ocrText = obj.optStringOrNull("ocrText"),
                    updatedAt = obj.optLong("updatedAt", obj.optLong("createdAt", System.currentTimeMillis())),
                    syncId = obj.optString("syncId").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    revision = obj.optLong("revision", 1L),
                    originDeviceId = obj.optStringOrNull("originDeviceId"),
                    modifiedByDeviceId = obj.optStringOrNull("modifiedByDeviceId"),
                    syncState = obj.optString("syncState", "LOCAL"),
                    syncExcluded = obj.optBoolean("syncExcluded", false)
                )
            )
        }
        return result
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
