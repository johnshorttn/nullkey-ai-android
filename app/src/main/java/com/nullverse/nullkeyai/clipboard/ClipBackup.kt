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
    const val FORMAT_VERSION = 1

    fun toJson(clips: List<Clip>, exportedAt: Long = System.currentTimeMillis()): String {
        val array = JSONArray()
        for (clip in clips) {
            val obj = JSONObject()
                .put("content", clip.content)
                .put("isFile", clip.isFile)
                .put("pinned", clip.pinned)
                .put("createdAt", clip.createdAt)
            clip.mimeType?.let { obj.put("mimeType", it) }
            clip.tag?.let { obj.put("tag", it) }
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
        val trimmed = text.trim()
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
            result.add(
                Clip(
                    content = content,
                    isFile = obj.optBoolean("isFile", false),
                    mimeType = obj.optStringOrNull("mimeType"),
                    tag = obj.optStringOrNull("tag"),
                    pinned = obj.optBoolean("pinned", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return result
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
