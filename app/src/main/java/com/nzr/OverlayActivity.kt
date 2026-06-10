package com.nzr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzr.service.ScreenshotDetectionService
import kotlinx.coroutines.delay

class OverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val path = intent.getStringExtra(ScreenshotDetectionService.EXTRA_PATH) ?: return finish()

        setContent {
            OverlayScreen(
                path = path,
                onSchedule = { delayMs ->
                    val scheduleIntent = Intent(this, ScreenshotDetectionService::class.java).apply {
                        action = ScreenshotDetectionService.ACTION_SCHEDULE
                        putExtra(ScreenshotDetectionService.EXTRA_PATH, path)
                        putExtra(ScreenshotDetectionService.EXTRA_DELAY_MS, delayMs)
                    }
                    startService(scheduleIntent)
                    finish()
                },
                onCancel = {
                    finish()
                }
            )
        }
    }
    
    // Disable back button just in case, but usually we want to allow cancel
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}

@Composable
fun OverlayScreen(path: String, onSchedule: (Long) -> Unit, onCancel: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
        delay(10000) // Auto-dismiss if no action
        if (visible) {
            visible = false
            delay(300)
            onCancel()
        }
    }

    // Infinite Color Animation for borders/accents
    val infiniteTransition = rememberInfiniteTransition(label = "color")
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
            .fillMaxSize()
            .clickable { 
                visible = false
                onCancel() 
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .clickable(enabled = false) {} // consume clicks
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xE6141428)) // Dark glass feel
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(color1, Color(0xFFFF007F))),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Screenshot Detected",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Delete this screenshot in:",
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TimeOption("5m") { onSchedule(5 * 60 * 1000L); visible = false }
                        TimeOption("10m") { onSchedule(10 * 60 * 1000L); visible = false }
                        TimeOption("15m") { onSchedule(15 * 60 * 1000L); visible = false }
                    }
                    
                    Button(
                        onClick = { onSchedule(45 * 60 * 1000L); visible = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, color1, RoundedCornerShape(16.dp))
                    ) {
                        Text("45 minutes", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeOption(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2A2A4A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
