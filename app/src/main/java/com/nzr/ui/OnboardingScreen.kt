package com.nzr.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(1) }

    // Required permissions depending on Android version
    val permissionsToRequest = mutableListOf<String>()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissionsToRequest)
    
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0D0D1A), Color(0xFF1A1A3A)),
                    start = Offset(0f, 0f),
                    end = Offset(gradientOffset, gradientOffset)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
            }, label = "onboarding_anim"
        ) { targetStep ->
            when (targetStep) {
                1 -> IntroStep(onNext = { step = 2 })
                2 -> PermissionStep(multiplePermissionsState, onNext = { step = 3 })
                3 -> SetupCompleteStep(onComplete)
            }
        }
    }
}

@Composable
fun IntroStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color.Cyan,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "SnapEraser",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Take screenshots. Set a timer. We'll automatically delete them for you to keep your gallery clean.",
            color = Color.LightGray,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EA)),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Get Started", fontSize = 18.sp, color = Color.White)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionStep(permissionsState: MultiplePermissionsState, onNext: () -> Unit) {
    val context = LocalContext.current
    var hasSystemAlertWindow by remember { 
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }
    var hasManageStorage by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true)
    }

    LaunchedEffect(permissionsState.allPermissionsGranted, hasSystemAlertWindow, hasManageStorage) {
        if (permissionsState.allPermissionsGranted && hasSystemAlertWindow && hasManageStorage) {
            onNext()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text("We need a little help", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        
        PermissionCard(
            title = "Notifications & Storage",
            description = "To detect screenshots and show countdowns.",
            isGranted = permissionsState.allPermissionsGranted,
            onRequest = { permissionsState.launchMultiplePermissionRequest() }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PermissionCard(
            title = "Display over other apps",
            description = "To show the timer popup instantly anywhere.",
            isGranted = hasSystemAlertWindow,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        PermissionCard(
            title = "All Files Access",
            description = "Required to permanently delete screenshots from storage.",
            isGranted = hasManageStorage,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                hasSystemAlertWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager() else true
                if (permissionsState.allPermissionsGranted && hasSystemAlertWindow && hasManageStorage) onNext()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EA)),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Proceed", fontSize = 18.sp, color = Color.White)
        }
    }
}

@Composable
fun PermissionCard(title: String, description: String, isGranted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2A2A4A))
            .clickable(enabled = !isGranted, onClick = onRequest)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(description, color = Color.LightGray, fontSize = 14.sp)
        }
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) Color.Green else Color.Yellow,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun SetupCompleteStep(onComplete: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text("All Set!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("SnapEraser is running in the background. Take a screenshot to test it!", color = Color.LightGray, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Open Dashboard", fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
