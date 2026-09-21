package com.nullverse.nullkeyai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    @Insert
    suspend fun insert(clip: Clip): Long

    @Update
    suspend fun update(clip: Clip): Int

    /** The newest active (non-trashed) clip, used to de-duplicate repeated captures. */
    @Query("SELECT * FROM clips WHERE trashedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestActive(): Clip?

    /**
     * Search active clips. Pinned clips float to the top, then newest first.
     * When [filesOnly] is true, only file/image clips are returned. A blank
     * [query] returns everything (subject to the file filter).
     */
    @Query(
        """
        SELECT * FROM clips
        WHERE trashedAt IS NULL
          AND (:query = '' OR content LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR ocrText LIKE '%' || :query || '%' OR sourceAppLabel LIKE '%' || :query || '%' OR sourcePackage LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%')
          AND (:filesOnly = 0 OR isFile = 1)
        ORDER BY pinned DESC, createdAt DESC
        """
    )
    fun search(query: String, filesOnly: Boolean): Flow<List<Clip>>

    @Query(
        """
        SELECT * FROM clips
        WHERE trashedAt IS NULL
          AND (:query = '' OR content LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR ocrText LIKE '%' || :query || '%' OR sourceAppLabel LIKE '%' || :query || '%' OR sourcePackage LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%')
          AND (:filesOnly = 0 OR isFile = 1)
        ORDER BY pinned DESC, createdAt DESC
        """
    )
    suspend fun searchOnce(query: String, filesOnly: Boolean): List<Clip>

    @Query("SELECT * FROM clips WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun trash(): Flow<List<Clip>>

    /** All active clips, oldest first, for export. */
    @Query("SELECT * FROM clips WHERE trashedAt IS NULL ORDER BY createdAt ASC")
    suspend fun allActive(): List<Clip>

    /** How many active clips already have this exact content (import de-duplication). */
    @Query("SELECT COUNT(*) FROM clips WHERE trashedAt IS NULL AND content = :content")
    suspend fun countByContent(content: String): Int

    @Query("UPDATE clips SET trashedAt = :now WHERE id = :id")
    suspend fun moveToTrash(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE clips SET trashedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE clips SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE clips SET notes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE clips SET content = :content, notes = :notes, protected = :isProtected, updatedAt = :now WHERE id = :id")
    suspend fun setProtectionPayload(id: Long, content: String, notes: String, isProtected: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE clips SET protected = :isProtected, updatedAt = :now WHERE id = :id")
    suspend fun setProtected(id: Long, isProtected: Boolean, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM clips WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun expiredTrash(cutoff: Long): List<Clip>

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Clip?

    /** Permanently delete trashed clips older than [cutoff] (the 30-day retention). */
    @Query("DELETE FROM clips WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun purgeExpired(cutoff: Long): Int

    @Query("DELETE FROM clips")
    suspend fun clear()
}
