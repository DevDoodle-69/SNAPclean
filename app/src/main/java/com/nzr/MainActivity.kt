package com.nzr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.nzr.service.ScreenshotDetectionService
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nzr.ui.DashboardScreen
import com.nzr.ui.OnboardingScreen
import com.nzr.ui.SettingsScreen
import com.nzr.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }

    private fun isForegroundServicePermitted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val hasStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            return hasNotif && hasStorage
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    @Composable
    fun MainApp() {
        var isOnboardingComplete by remember { mutableStateOf(isForegroundServicePermitted() && hasOverlayPermission()) }

        LaunchedEffect(isOnboardingComplete) {
            if (isOnboardingComplete) {
                // Permissions granted, start the service
                val serviceIntent = Intent(this@MainActivity, ScreenshotDetectionService::class.java).apply {
                    action = ScreenshotDetectionService.ACTION_BOOT_COMPLETED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }

        if (isOnboardingComplete) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "dashboard") {
                composable("dashboard") {
                    DashboardScreen(onNavigateToSettings = { navController.navigate("settings") })
                }
                composable("settings") {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        } else {
            OnboardingScreen(onComplete = { isOnboardingComplete = true })
        }
    }
}

