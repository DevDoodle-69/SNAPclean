package com.nzr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screenshot_logs")
data class ScreenshotLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val path: String,
    val filename: String,
    val detectedAt: Long,
    val scheduledDeleteAt: Long,
    val isDeleted: Boolean = false,
    val deleteTimestamp: Long? = null
)
