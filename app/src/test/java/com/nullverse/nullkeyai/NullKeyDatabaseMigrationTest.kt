package com.nullverse.nullkeyai

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.db.DefaultTags
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NullKeyDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-1-to-5.db"
    private var roomDb: NullKeyDatabase? = null

    @After
    fun tearDown() {
        roomDb?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1to5_preservesClipsTagsAndInfersVaultMetadata() = runBlocking {
        createV1Database(
            textWithTag = Triple("meeting notes", "Work", 1_000L),
            image = Triple("content://media/1", "image/png", 2_000L),
            file = Triple("content://downloads/a", "application/pdf", 3_000L),
            url = Triple("https://nullverse.example/clip", null, 4_000L),
            trashed = Triple("old secret", 5_000L, 6_000L)
        )

        val db = NullKeyDatabase.builder(context, dbName)
            .allowMainThreadQueries()
            .build()
            .also { roomDb = it }

        val active = db.clipDao().searchOnce("", false)
        assertEquals(4, active.size)

        val byContent = active.associateBy { it.content }
        val text = byContent.getValue("meeting notes")
        assertEquals("TEXT", text.contentType)
        assertEquals("Work", text.tag)
        assertEquals(1_000L, text.updatedAt)
        assertTrue(text.syncId.isNotBlank())
        assertEquals("LOCAL", text.syncState)
        assertEquals(1L, text.revision)

        val image = byContent.getValue("content://media/1")
        assertEquals("IMAGE", image.contentType)
        assertTrue(image.isFile)

        val file = byContent.getValue("content://downloads/a")
        assertEquals("FILE", file.contentType)

        val url = byContent.getValue("https://nullverse.example/clip")
        assertEquals("URI", url.contentType)

        val trash = db.clipDao().trash().first()
        assertEquals(1, trash.size)
        assertEquals("old secret", trash[0].content)
        assertEquals(6_000L, trash[0].trashedAt)
        assertNotEquals("", trash[0].syncId)

        val tagsForText = db.tagDao().forClip(text.id).map { it.name }
        assertEquals(listOf("Work"), tagsForText)

        val catalog = db.tagDao().all().first().map { it.name }.toSet()
        assertTrue(catalog.containsAll(DefaultTags.NAMES))
        assertTrue(catalog.contains("Work"))
    }

    @Test
    fun migrate1to5_assignsDistinctSyncIds() = runBlocking {
        createV1Database(
            textWithTag = Triple("one", null, 10L),
            image = Triple("two", null, 20L),
            file = Triple("three", null, 30L),
            url = Triple("four", null, 40L),
            trashed = Triple("five", 50L, 60L)
        )
        val db = NullKeyDatabase.builder(context, dbName)
            .allowMainThreadQueries()
            .build()
            .also { roomDb = it }

        val ids = (db.clipDao().searchOnce("", false) + db.clipDao().trash().first())
            .map { it.syncId }
        assertEquals(5, ids.size)
        assertEquals(5, ids.toSet().size)
    }

    @Test
    fun migrate4to5_collapsesCaseOnlyTagsAndPreservesAttachments() = runBlocking {
        createV4DatabaseWithDuplicateTags()
        val db = NullKeyDatabase.builder(context, dbName)
            .allowMainThreadQueries()
            .build()
            .also { roomDb = it }

        val tags = db.tagDao().all().first().filter { it.name.equals("Work", ignoreCase = true) }
        assertEquals(1, tags.size)
        val clip = db.clipDao().searchOnce("case duplicate", false).single()
        assertEquals(1, db.tagDao().forClip(clip.id).count { it.name.equals("Work", ignoreCase = true) })
        assertEquals(-1L, db.tagDao().insert(com.nullverse.nullkeyai.db.Tag(name = "WORK")))
    }

    private fun createV4DatabaseWithDuplicateTags() {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE clips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, content TEXT NOT NULL, isFile INTEGER NOT NULL, mimeType TEXT, tag TEXT, pinned INTEGER NOT NULL, createdAt INTEGER NOT NULL, trashedAt INTEGER, contentType TEXT NOT NULL DEFAULT 'TEXT', notes TEXT NOT NULL DEFAULT '', protected INTEGER NOT NULL DEFAULT 0, localAssetPath TEXT, sourcePackage TEXT, sourceAppLabel TEXT, sourceUri TEXT, captureMethod TEXT NOT NULL DEFAULT 'UNKNOWN', sourceConfidence TEXT NOT NULL DEFAULT 'UNKNOWN', ocrText TEXT, updatedAt INTEGER NOT NULL DEFAULT 0, syncId TEXT NOT NULL DEFAULT '', revision INTEGER NOT NULL DEFAULT 1, originDeviceId TEXT, modifiedByDeviceId TEXT, syncDeletedAt INTEGER, syncState TEXT NOT NULL DEFAULT 'LOCAL', syncExcluded INTEGER NOT NULL DEFAULT 0)""")
                        db.execSQL("CREATE UNIQUE INDEX index_clips_syncId ON clips(syncId)")
                        db.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags(name)")
                        db.execSQL("CREATE TABLE clip_tags (clipId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(clipId, tagId), FOREIGN KEY(clipId) REFERENCES clips(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_clip_tags_clipId ON clip_tags(clipId)")
                        db.execSQL("CREATE INDEX index_clip_tags_tagId ON clip_tags(tagId)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO clips(content,isFile,pinned,createdAt,contentType,notes,protected,captureMethod,sourceConfidence,updatedAt,syncId,revision,syncState,syncExcluded) VALUES ('case duplicate',0,0,1,'TEXT','',0,'UNKNOWN','UNKNOWN',1,'case-sync-id',1,'LOCAL',0)")
        db.execSQL("INSERT INTO tags(name,createdAt) VALUES ('Work',1),('work',2)")
        db.execSQL("INSERT INTO clip_tags(clipId,tagId) VALUES (1,1),(1,2)")
        helper.close()
    }

    private fun createV1Database(
        textWithTag: Triple<String, String?, Long>,
        image: Triple<String, String?, Long>,
        file: Triple<String, String?, Long>,
        url: Triple<String, String?, Long>,
        trashed: Triple<String, Long, Long>
    ) {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS clips (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                content TEXT NOT NULL,
                                isFile INTEGER NOT NULL,
                                mimeType TEXT,
                                tag TEXT,
                                pinned INTEGER NOT NULL,
                                createdAt INTEGER NOT NULL,
                                trashedAt INTEGER
                            )"""
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        insertV1(db, textWithTag.first, isFile = 0, mime = null, tag = textWithTag.second, created = textWithTag.third, trashed = null)
        insertV1(db, image.first, isFile = 1, mime = image.second, tag = null, created = image.third, trashed = null)
        insertV1(db, file.first, isFile = 1, mime = file.second, tag = null, created = file.third, trashed = null)
        insertV1(db, url.first, isFile = 0, mime = null, tag = null, created = url.third, trashed = null)
        insertV1(db, trashed.first, isFile = 0, mime = null, tag = null, created = trashed.second, trashed = trashed.third)
        helper.close()
    }

    private fun insertV1(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        content: String,
        isFile: Int,
        mime: String?,
        tag: String?,
        created: Long,
        trashed: Long?
    ) {
        db.execSQL(
            "INSERT INTO clips(content, isFile, mimeType, tag, pinned, createdAt, trashedAt) VALUES (?, ?, ?, ?, 0, ?, ?)",
            arrayOf(content, isFile, mime, tag, created, trashed)
        )
    }
}
