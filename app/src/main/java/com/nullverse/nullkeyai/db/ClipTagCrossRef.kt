package com.nullverse.nullkeyai.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "clip_tags",
    primaryKeys = ["clipId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = Clip::class, parentColumns = ["id"], childColumns = ["clipId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("clipId"), Index("tagId")]
)
data class ClipTagCrossRef(val clipId: Long, val tagId: Long)
