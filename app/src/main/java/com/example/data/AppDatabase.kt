package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedVideo::class, SearchHistory::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedVideoDao(): SavedVideoDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sleek_video_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
