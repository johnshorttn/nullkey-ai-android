package com.nullverse.nullkeyai.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Clip::class, Tag::class, ClipTagCrossRef::class],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN contentType TEXT NOT NULL DEFAULT 'TEXT'")
                db.execSQL("ALTER TABLE clips ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN protected INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clips ADD COLUMN localAssetPath TEXT")
                db.execSQL("ALTER TABLE clips ADD COLUMN sourcePackage TEXT")
                db.execSQL("ALTER TABLE clips ADD COLUMN sourceAppLabel TEXT")
                db.execSQL("ALTER TABLE clips ADD COLUMN sourceUri TEXT")
                db.execSQL("ALTER TABLE clips ADD COLUMN captureMethod TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE clips ADD COLUMN sourceConfidence TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE clips ADD COLUMN ocrText TEXT")
                db.execSQL("ALTER TABLE clips ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE clips SET updatedAt = createdAt WHERE updatedAt = 0")
                db.execSQL("""UPDATE clips SET contentType =
                    CASE
                        WHEN mimeType LIKE 'image/%' THEN 'IMAGE'
                        WHEN isFile = 1 THEN 'FILE'
                        WHEN content LIKE 'http://%' OR content LIKE 'https://%' THEN 'URI'
                        ELSE 'TEXT'
                    END""")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clips ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE clips ADD COLUMN originDeviceId TEXT")
                db.execSQL("ALTER TABLE clips ADD COLUMN modifiedByDeviceId TEXT")
                db.execSQL("ALTER TABLE clips ADD COLUMN syncDeletedAt INTEGER")
                db.execSQL("ALTER TABLE clips ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE clips ADD COLUMN syncExcluded INTEGER NOT NULL DEFAULT 0")
                // Existing rows get deterministic migration IDs without relying on local IDs after sync begins.
                db.execSQL("UPDATE clips SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
                // Unique index is not declared on the v4 entity; creating it here would
                // fail Room's post-migration schema validation on upgrade.
            }
        }

        val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                DefaultTags.seed(db)
            }
        }

        fun get(context: Context): NullKeyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: builder(context).build().also { INSTANCE = it }
            }

        fun builder(context: Context, name: String = "nullkey.db") =
            Room.databaseBuilder(
                context.applicationContext,
                NullKeyDatabase::class.java,
                name
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(SEED_CALLBACK)

        internal fun resetInstanceForTests() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
