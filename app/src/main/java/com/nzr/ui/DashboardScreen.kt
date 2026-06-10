package com.nzr.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzr.data.AppDatabase
import com.nzr.service.DeletionScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

import androidx.compose.material.icons.filled.Settings

@Composable
fun DashboardScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val pendingLogs by db.logDao().getPendingScreenshots().collectAsStateWithLifecycle(initialValue = emptyList())
    val scheduler = remember { DeletionScheduler(context) }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg_offset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0D0D1A), Color(0xFF1A1A3A)),
                    start = Offset(0f, 0f),
                    end = Offset(gradientOffset, gradientOffset)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
            Text(
                "Pending Deletions",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )

            if (pendingLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFF333366),
                            modifier = Modifier.size(72.dp).padding(bottom = 16.dp)
                        )
                        Text("All clean.", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Waiting for new screenshots...", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(pendingLogs) { log ->
                        var remainingBy by remember { mutableStateOf(log.scheduledDeleteAt - System.currentTimeMillis()) }
                        
                        LaunchedEffect(log) {
                            while(remainingBy > 0) {
                                remainingBy = log.scheduledDeleteAt - System.currentTimeMillis()
                                kotlinx.coroutines.delay(1000)
                            }
                            // Proactively trigger deletion when timer hits 0 while UI is open
                            if (remainingBy <= 0) {
                                val intent = android.content.Intent(context, com.nzr.service.ScreenshotDetectionService::class.java).apply {
                                    action = com.nzr.service.ScreenshotDetectionService.ACTION_DELETE
                                    putExtra(com.nzr.service.ScreenshotDetectionService.EXTRA_LOG_ID, log.id)
                                    putExtra(com.nzr.service.ScreenshotDetectionService.EXTRA_PATH, log.path)
                                }
                                context.startService(intent)
                            }
                        }

                        val progress = (remainingBy.toFloat() / (log.scheduledDeleteAt - log.detectedAt).toFloat()).coerceIn(0f, 1f)
                        val color1 by infiniteTransition.animateColor(
                            initialValue = Color(0xFF6200EA),
                            targetValue = Color(0xFF00E5FF),
                            animationSpec = infiniteRepeatable(
                                animation = tween(4000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "color1"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xE6141428))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(listOf(color1, Color(0xFFFF007F))),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        color = Color(0xFFFF007F),
                                        trackColor = Color(0xFF333366),
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 4.dp
                                    )
                                    coil.compose.AsyncImage(
                                        model = java.io.File(log.path),
                                        contentDescription = "Screenshot preview",
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.filename, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                    val timeStr = if (remainingBy > 0) {
                                        val min = remainingBy / 1000 / 60
                                        val sec = (remainingBy / 1000) % 60
                                        "${min}m ${sec}s remaining"
                                    } else "Deleting now..."
                                    Text(timeStr, color = Color.Cyan, fontSize = 14.sp)
                                }
                                
                                IconButton(onClick = {
                                    scope.launch {
                                        scheduler.cancelDeletion(log.id)
                                        db.logDao().deleteLogById(log.id)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
