package com.nullverse.nullkeyai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(ref: ClipTagCrossRef)

    @Query("DELETE FROM clip_tags WHERE clipId = :clipId AND tagId = :tagId")
    suspend fun detach(clipId: Long, tagId: Long)

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN clip_tags ON tags.id = clip_tags.tagId
        WHERE clip_tags.clipId = :clipId
        ORDER BY tags.name COLLATE NOCASE
    """)
    suspend fun forClip(clipId: Long): List<Tag>
}
