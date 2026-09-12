package com.nullverse.nullkeyai.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single captured clipboard entry.
 *
 * Text clips store their text in [content]. File/image clips store the content
 * URI string in [content] with [isFile] = true and the resolved [mimeType].
 */
@Entity(tableName = "clips")
data class Clip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val isFile: Boolean = false,
    val mimeType: String? = null,
    val tag: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Non-null when the clip has been moved to Trash. */
    val trashedAt: Long? = null
)
