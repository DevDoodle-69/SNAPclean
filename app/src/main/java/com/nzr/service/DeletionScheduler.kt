package com.nzr.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nzr.data.ScreenshotLog

class DeletionScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleDeletion(log: ScreenshotLog) {
        val intent = Intent(context, ScreenshotDetectionService::class.java).apply {
            action = ScreenshotDetectionService.ACTION_DELETE
            putExtra(ScreenshotDetectionService.EXTRA_LOG_ID, log.id)
            putExtra(ScreenshotDetectionService.EXTRA_PATH, log.path)
        }
        val pendingIntent = PendingIntent.getService(
            context,
            log.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Exact alarms need permission starting Android 12 it's declared in manifest
        // Can fallback to andAllowWhileIdle if exact alarm permission not granted
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                log.scheduledDeleteAt,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Fallback for Android 14+ if exact alarm permission is missing
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                log.scheduledDeleteAt,
                pendingIntent
            )
        }
    }

    fun cancelDeletion(logId: Int) {
        val intent = Intent(context, ScreenshotDetectionService::class.java).apply {
            action = ScreenshotDetectionService.ACTION_DELETE
        }
        val pendingIntent = PendingIntent.getService(
            context,
            logId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
