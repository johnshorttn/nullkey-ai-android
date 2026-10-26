package com.nullverse.nullkeyai.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Suggested starter tags for a new Vault. These are seeds, not a closed set;
 * users can still create any other tag name.
 */
object DefaultTags {
    val NAMES = listOf("Personal", "Work", "Coding")

    fun seed(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        for (name in NAMES) {
            db.execSQL(
                "INSERT OR IGNORE INTO tags(name, createdAt) VALUES (?, ?)",
                arrayOf(name, now)
            )
        }
    }

    suspend fun seed(tagDao: TagDao, now: Long = System.currentTimeMillis()) {
        for (name in NAMES) {
            if (tagDao.findByName(name) == null) {
                tagDao.insert(Tag(name = name, createdAt = now))
            }
        }
    }
}
