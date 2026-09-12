package com.nullverse.nullkeyai.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Clip::class], version = 1, exportSchema = false)
abstract class NullKeyDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile
        private var INSTANCE: NullKeyDatabase? = null

        fun get(context: Context): NullKeyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NullKeyDatabase::class.java,
                    "nullkey.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
