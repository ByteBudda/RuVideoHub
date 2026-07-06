package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedVideoDao {
    @Query("SELECT * FROM saved_videos ORDER BY savedAt DESC")
    fun getAllSavedVideos(): Flow<List<SavedVideo>>

    @Query("SELECT * FROM saved_videos WHERE lastProgress > 0 AND lastProgress < (lastDuration - 5000) ORDER BY savedAt DESC")
    fun getContinueWatchingVideos(): Flow<List<SavedVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(video: SavedVideo)

    @Delete
    suspend fun delete(video: SavedVideo)

    @Query("SELECT * FROM saved_videos WHERE id = :id")
    suspend fun getVideoById(id: String): SavedVideo?

    @Query("DELETE FROM saved_videos WHERE id = :id")
    suspend fun deleteById(id: String)
}
