package com.nullverse.nullkeyai.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Clip::class, Tag::class, ClipTagCrossRef::class],
    version = 2,
    exportSchema = true
)
abstract class NullKeyDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: NullKeyDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS clip_tags (
                        clipId INTEGER NOT NULL,
                        tagId INTEGER NOT NULL,
                        PRIMARY KEY(clipId, tagId),
                        FOREIGN KEY(clipId) REFERENCES clips(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clip_tags_clipId ON clip_tags(clipId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clip_tags_tagId ON clip_tags(tagId)")

                // Preserve legacy single-tag data by migrating it into the new relation.
                db.execSQL(
                    """INSERT OR IGNORE INTO tags(name, createdAt)
                       SELECT DISTINCT TRIM(tag), createdAt FROM clips
                       WHERE tag IS NOT NULL AND TRIM(tag) != ''"""
                )
                db.execSQL(
                    """INSERT OR IGNORE INTO clip_tags(clipId, tagId)
                       SELECT clips.id, tags.id FROM clips
                       INNER JOIN tags ON tags.name = TRIM(clips.tag)
                       WHERE clips.tag IS NOT NULL AND TRIM(clips.tag) != ''"""
                )
            }
        }

        fun get(context: Context): NullKeyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NullKeyDatabase::class.java,
                    "nullkey.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
