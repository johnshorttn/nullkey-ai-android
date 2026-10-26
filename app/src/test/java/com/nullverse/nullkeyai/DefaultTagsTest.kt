package com.nullverse.nullkeyai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.db.DefaultTags
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultTagsTest {

    private lateinit var db: NullKeyDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NullKeyDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seed_insertsPersonalWorkCodingOnce() = runBlocking {
        DefaultTags.seed(db.tagDao())
        DefaultTags.seed(db.tagDao())
        val names = db.tagDao().all().first().map { it.name }
        assertEquals(DefaultTags.NAMES.sorted(), names.sorted())
    }

    @Test
    fun productionOpen_seedsDefaultTags() = runBlocking {
        NullKeyDatabase.resetInstanceForTests()
        val opened = NullKeyDatabase.builder(
            ApplicationProvider.getApplicationContext(),
            "seed-open.db"
        ).allowMainThreadQueries().build()
        try {
            val names = opened.tagDao().all().first().map { it.name }.toSet()
            assertTrue(names.containsAll(DefaultTags.NAMES))
        } finally {
            opened.close()
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase("seed-open.db")
            NullKeyDatabase.resetInstanceForTests()
        }
    }
}
