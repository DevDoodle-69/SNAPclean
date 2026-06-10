package com.nzr.service

import android.app.*
import android.content.*
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.content.ContentUris
import androidx.core.app.NotificationCompat
import com.nzr.R
import com.nzr.data.AppDatabase
import com.nzr.data.ScreenshotLog
import com.nzr.OverlayActivity
import kotlinx.coroutines.*
import java.io.File

class ScreenshotDetectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var scheduler: DeletionScheduler
    private var lastObservedId: Long = -1

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            checkForNewScreenshots()
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        scheduler = DeletionScheduler(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_BOOT_COMPLETED -> {
                rescheduleAllPendingDeletions()
            }
            ACTION_DELETE -> {
                val logId = intent.getIntExtra(EXTRA_LOG_ID, -1)
                val path = intent.getStringExtra(EXTRA_PATH)
                if (logId != -1 && path != null) {
                    executeDeletion(logId, path)
                }
            }
            ACTION_SCHEDULE -> {
                val path = intent.getStringExtra(EXTRA_PATH)
                val delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 0)
                if (path != null && delayMs > 0) {
                    scheduleNewDeletion(path, delayMs)
                }
            }
            ACTION_CANCEL -> {
                val logId = intent.getIntExtra(EXTRA_LOG_ID, -1)
                if (logId != -1) {
                    cancelDeletion(logId)
                }
            }
            ACTION_UPDATE_NOTIFICATION -> {
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun checkForNewScreenshots() {
        serviceScope.launch {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            
            // Allow some time for file to physically exist before query
            delay(500)
            
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val id = cursor.getLong(idColumn)
                    val path = cursor.getString(dataColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    if (id != lastObservedId && path.contains("screenshot", ignoreCase = true)) {
                        lastObservedId = id
                        // DATE_ADDED is in seconds. Only trigger if added within the last 15 seconds.
                        val nowInSeconds = System.currentTimeMillis() / 1000
                        if (nowInSeconds - dateAdded <= 15) {
                            Log.d("ScreenshotService", "New screenshot detected: $path")
                            showOverlayPopup(path)
                        }
                    }
                }
            }
        }
    }

    private fun showOverlayPopup(path: String) {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_PATH, path)
        }
        startActivity(intent)
    }

    private fun scheduleNewDeletion(path: String, delayMs: Long) {
        serviceScope.launch {
            val file = File(path)
            val log = ScreenshotLog(
                path = path,
                filename = file.name,
                detectedAt = System.currentTimeMillis(),
                scheduledDeleteAt = System.currentTimeMillis() + delayMs
            )
            val id = db.logDao().insertLog(log).toInt()
            val scheduledLog = log.copy(id = id)
            scheduler.scheduleDeletion(scheduledLog)
            updateNotification()
        }
    }

    private fun cancelDeletion(logId: Int) {
        serviceScope.launch {
            scheduler.cancelDeletion(logId)
            db.logDao().deleteLogById(logId)
            updateNotification()
        }
    }

    private fun executeDeletion(logId: Int, path: String) {
        serviceScope.launch {
            val file = File(path)
            var isDeleted = false
            try {
                // 1. Try to query the exact MediaStore URI to delete it properly
                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val selection = "${MediaStore.Images.Media.DATA} = ?"
                val selectionArgs = arrayOf(path)
                
                contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val id = cursor.getLong(idColumn)
                        val itemUri = ContentUris.withAppendedId(uri, id)
                        val deletedRows = contentResolver.delete(itemUri, null, null)
                        if (deletedRows > 0) {
                            isDeleted = true
                        }
                    }
                }

                // 2. Fallback to ContentResolver delete by DATA path
                if (!isDeleted) {
                    val deletedCount = contentResolver.delete(uri, "${MediaStore.Images.Media.DATA}=?", arrayOf(path))
                    if (deletedCount > 0) {
                        isDeleted = true
                    }
                }
                
                // 3. Fallback to direct File delete
                if (file.exists()) {
                    if (file.delete()) {
                        isDeleted = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
                
            if (isDeleted || !file.exists()) {
                // Vibrate and Play woosh sound
                try {
                    val mediaPlayer = android.media.MediaPlayer.create(this@ScreenshotDetectionService, R.raw.woosh)
                    mediaPlayer?.setOnCompletionListener { it.release() }
                    mediaPlayer?.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
                showToastNotification("Screenshot automatically deleted.")
            }
            db.logDao().markAsDeleted(logId)
            updateNotification()
        }
    }
    
    private fun showToastNotification(message: String) {
        // Here we could emit a broadcast to show a toast in app, but since app might be in background, 
        // we show a quick system notification that dismisses itself or just let standard behavior work.
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SnapEraser")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun rescheduleAllPendingDeletions() {
        serviceScope.launch {
            val pending = db.logDao().getPendingScreenshotsSync()
            val now = System.currentTimeMillis()
            pending.forEach { log ->
                if (log.scheduledDeleteAt <= now) {
                    // Overdue, delete immediately
                    executeDeletion(log.id, log.path)
                } else {
                    scheduler.scheduleDeletion(log)
                }
            }
            updateNotification()
        }
    }

    private fun updateNotification() {
        serviceScope.launch {
            val pendingCount = db.logDao().getPendingScreenshotsSync().size
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(pendingCount))
        }
    }

    private fun buildNotification(pendingCount: Int): Notification {
        val contentText = if (pendingCount > 0) "Tracking $pendingCount screenshots" else "Listening for screenshots..."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SnapEraser Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screenshot Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs in the background to detect new screenshots"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(observer)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "snaperaser_channel"
        
        const val ACTION_BOOT_COMPLETED = "action_boot_completed"
        const val ACTION_DELETE = "action_delete"
        const val ACTION_SCHEDULE = "action_schedule"
        const val ACTION_CANCEL = "action_cancel"
        const val ACTION_UPDATE_NOTIFICATION = "action_update_notification"
        
        const val EXTRA_LOG_ID = "extra_log_id"
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_DELAY_MS = "extra_delay_ms"
    }
}
