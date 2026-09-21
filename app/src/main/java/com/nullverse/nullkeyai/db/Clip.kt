package com.nullverse.nullkeyai.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ClipContentType { TEXT, IMAGE, FILE, URI, RICH }
enum class ClipCaptureMethod { CLIPBOARD, IME, SHARE, MANUAL, OCR, IMPORT, REMOTE, UNKNOWN }
enum class ClipSourceConfidence { CONFIRMED, INFERRED, UNKNOWN }

@Entity(tableName = "clips")
data class Clip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val isFile: Boolean = false,
    val mimeType: String? = null,
    /** Legacy v1 single-tag field. New code uses clip_tags. */
    val tag: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val trashedAt: Long? = null,

    // Vault 2.0 metadata.
    val contentType: String = ClipContentType.TEXT.name,
    val notes: String = "",
    val protected: Boolean = false,
    val localAssetPath: String? = null,
    val sourcePackage: String? = null,
    val sourceAppLabel: String? = null,
    val sourceUri: String? = null,
    val captureMethod: String = ClipCaptureMethod.UNKNOWN.name,
    val sourceConfidence: String = ClipSourceConfidence.UNKNOWN.name,
    val ocrText: String? = null,
    val updatedAt: Long = createdAt
)
