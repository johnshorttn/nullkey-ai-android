package com.nullverse.nullkeyai.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for [SyncEnvelope]. The document contains only record metadata and
 * payloads — never hostnames, API tokens, or provider endpoints.
 */
object SyncEnvelopeCodec {

    fun toJson(envelope: SyncEnvelope): String {
        val records = JSONArray()
        for (record in envelope.records) {
            records.put(recordToJson(record))
        }
        return JSONObject()
            .put("app", SyncEnvelope.APP_TAG)
            .put("kind", SyncEnvelope.KIND)
            .put("format", envelope.format)
            .put("producerDeviceId", envelope.producerDeviceId)
            .put("producedAt", envelope.producedAt)
            .put("records", records)
            .toString(2)
    }

    fun fromJson(text: String): SyncEnvelope {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Empty sync envelope")
        val root = JSONObject(trimmed)
        if (root.optString("kind") != SyncEnvelope.KIND) {
            throw IllegalArgumentException("Not a NullKey sync envelope")
        }
        if (root.optString("app") != SyncEnvelope.APP_TAG) {
            throw IllegalArgumentException("Not a NullKey sync envelope")
        }
        val format = root.optInt("format", 0)
        if (format != SyncEnvelope.FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported sync envelope format: $format")
        }
        val array = root.optJSONArray("records")
            ?: throw IllegalArgumentException("Missing records array")
        val records = ArrayList<SyncClipRecord>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            records += recordFromJson(obj)
        }
        return SyncEnvelope(
            format = format,
            producerDeviceId = root.getString("producerDeviceId"),
            producedAt = root.getLong("producedAt"),
            records = records
        )
    }

    private fun recordToJson(record: SyncClipRecord): JSONObject {
        val obj = JSONObject()
            .put("syncId", record.syncId)
            .put("revision", record.revision)
            .put("originDeviceId", record.originDeviceId)
            .put("modifiedByDeviceId", record.modifiedByDeviceId)
            .put("updatedAt", record.updatedAt)
            .put("state", record.state.name)
        if (record.deletedAt != null) obj.put("deletedAt", record.deletedAt)
        record.payload?.let { obj.put("payload", payloadToJson(it)) }
        return obj
    }

    private fun payloadToJson(payload: SyncClipPayload): JSONObject {
        val obj = JSONObject()
            .put("content", payload.content)
            .put("isFile", payload.isFile)
            .put("pinned", payload.pinned)
            .put("createdAt", payload.createdAt)
            .put("contentType", payload.contentType)
            .put("notes", payload.notes)
            .put("protected", payload.protectedPayload)
            .put("captureMethod", payload.captureMethod)
            .put("sourceConfidence", payload.sourceConfidence)
            .put("syncExcluded", payload.syncExcluded)
        payload.mimeType?.let { obj.put("mimeType", it) }
        payload.tag?.let { obj.put("tag", it) }
        payload.localAssetPath?.let { obj.put("localAssetPath", it) }
        payload.sourcePackage?.let { obj.put("sourcePackage", it) }
        payload.sourceAppLabel?.let { obj.put("sourceAppLabel", it) }
        payload.sourceUri?.let { obj.put("sourceUri", it) }
        payload.ocrText?.let { obj.put("ocrText", it) }
        payload.trashedAt?.let { obj.put("trashedAt", it) }
        if (payload.tags.isNotEmpty()) {
            val tags = JSONArray()
            payload.tags.forEach { tags.put(it) }
            obj.put("tags", tags)
        }
        return obj
    }

    private fun recordFromJson(obj: JSONObject): SyncClipRecord {
        val deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt")) obj.getLong("deletedAt") else null
        val payloadObj = obj.optJSONObject("payload")
        return SyncClipRecord(
            syncId = obj.getString("syncId"),
            revision = obj.getLong("revision"),
            originDeviceId = obj.getString("originDeviceId"),
            modifiedByDeviceId = obj.getString("modifiedByDeviceId"),
            updatedAt = obj.getLong("updatedAt"),
            deletedAt = deletedAt,
            state = obj.optString("state", SyncRecordState.PENDING.name)
                .let { runCatching { SyncRecordState.valueOf(it) }.getOrDefault(SyncRecordState.PENDING) },
            payload = if (deletedAt != null) null else payloadObj?.let { payloadFromJson(it) }
        )
    }

    private fun payloadFromJson(obj: JSONObject): SyncClipPayload {
        val tagArray = obj.optJSONArray("tags")
        val tags = buildList {
            if (tagArray != null) for (i in 0 until tagArray.length()) add(tagArray.getString(i))
        }
        return SyncClipPayload(
            content = obj.optString("content", ""),
            isFile = obj.optBoolean("isFile", false),
            mimeType = obj.optStringOrNull("mimeType"),
            tag = obj.optStringOrNull("tag"),
            pinned = obj.optBoolean("pinned", false),
            createdAt = obj.optLong("createdAt", 0L),
            contentType = obj.optString("contentType", "TEXT"),
            notes = obj.optString("notes", ""),
            protectedPayload = obj.optBoolean("protected", false),
            localAssetPath = obj.optStringOrNull("localAssetPath"),
            sourcePackage = obj.optStringOrNull("sourcePackage"),
            sourceAppLabel = obj.optStringOrNull("sourceAppLabel"),
            sourceUri = obj.optStringOrNull("sourceUri"),
            captureMethod = obj.optString("captureMethod", "UNKNOWN"),
            sourceConfidence = obj.optString("sourceConfidence", "UNKNOWN"),
            ocrText = obj.optStringOrNull("ocrText"),
            trashedAt = if (obj.has("trashedAt") && !obj.isNull("trashedAt")) obj.getLong("trashedAt") else null,
            syncExcluded = obj.optBoolean("syncExcluded", false),
            tags = tags
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
