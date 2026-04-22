package com.android.autopay

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.android.autopay.data.datasource.services.PushNotificationHandlerService
import com.android.autopay.presentation.screens.MainScreen
import com.android.autopay.presentation.theme.AutoPayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!isSmsPermissionGranted()) {
            requestReceiveSmsPermission()
        }
        if (!isReadSmsPermissionGranted()) {
            requestReadSmsPermission()
        }
        if (!isNotificationServiceEnabled()) {
            requestNotificationListenerPermission()
        }
        if (!isBatteryOptimizationDisabled()) {
            openBatteryOptimizationSettings()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPostNotificationsPermission()
        }
        startForegroundService(Intent(this, PushNotificationHandlerService::class.java))
        setContent {
            AutoPayTheme {
                MainScreen()
            }
        }
    }

    private fun isSmsPermissionGranted(): Boolean {
        val permission = android.Manifest.permission.RECEIVE_SMS
        val grant = ContextCompat.checkSelfPermission(this, permission)
        return grant == PackageManager.PERMISSION_GRANTED
    }

    private fun requestReceiveSmsPermission() {
        val permission = android.Manifest.permission.RECEIVE_SMS
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        requestPermissionLauncher.launch(permission)
    }

    private fun isReadSmsPermissionGranted(): Boolean {
        val permission = android.Manifest.permission.READ_SMS
        val grant = ContextCompat.checkSelfPermission(this, permission)
        return grant == PackageManager.PERMISSION_GRANTED
    }

    private fun requestReadSmsPermission() {
        val permission = android.Manifest.permission.READ_SMS
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        requestPermissionLauncher.launch(permission)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.split(":")
            ?.map { ComponentName.unflattenFromString(it) }
            ?.any { it?.packageName == pkgName } == true
    }

    private fun requestNotificationListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
        notificationPermissionLauncher.launch(intent)
    }

    private fun requestPostNotificationsPermission() {
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val grant = ContextCompat.checkSelfPermission(this, permission)
        if (grant == PackageManager.PERMISSION_GRANTED) return
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        requestPermissionLauncher.launch(permission)
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }
        val batteryOptimizationLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
        batteryOptimizationLauncher.launch(intent)
    }
}
