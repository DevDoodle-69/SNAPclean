package com.nzr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM screenshot_logs ORDER BY detectedAt DESC")
    fun getAllLogs(): Flow<List<ScreenshotLog>>

    @Query("SELECT * FROM screenshot_logs WHERE isDeleted = 0")
    fun getPendingScreenshots(): Flow<List<ScreenshotLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ScreenshotLog): Long

    @Query("UPDATE screenshot_logs SET isDeleted = 1, deleteTimestamp = :timestamp WHERE id = :id")
    suspend fun markAsDeleted(id: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE screenshot_logs SET isDeleted = 1, deleteTimestamp = :timestamp WHERE path = :path")
    suspend fun markAsDeletedByPath(path: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM screenshot_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)
    
    @Query("SELECT * FROM screenshot_logs WHERE isDeleted = 0")
    suspend fun getPendingScreenshotsSync(): List<ScreenshotLog>
}
