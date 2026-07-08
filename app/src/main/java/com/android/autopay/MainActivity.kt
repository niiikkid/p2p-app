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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.android.autopay.data.datasource.services.PushNotificationHandlerService
import com.android.autopay.presentation.screens.MainScreen
import com.android.autopay.presentation.theme.AutoPayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Лаунчеры регистрируем безусловно как поля активити (рекомендованный порядок,
    // до перехода в состояние STARTED), чтобы избежать краша при повторной регистрации.
    private val receiveSmsPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val readSmsPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val postNotificationsPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val notificationListenerSettingsLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private val batteryOptimizationLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!isSmsPermissionGranted()) {
            safeLaunch { receiveSmsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS) }
        }
        if (!isReadSmsPermissionGranted()) {
            safeLaunch { readSmsPermissionLauncher.launch(android.Manifest.permission.READ_SMS) }
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
        startNotificationService()
        setContent {
            AutoPayTheme {
                MainScreen()
            }
        }
    }

    // На части устройств (кастомные прошивки, урезанные образы) целевого системного экрана
    // может не быть — launch бросит ActivityNotFoundException. Не даём приложению упасть.
    private inline fun safeLaunch(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.d(TAG, "Unable to launch system intent: ${e.message}", e)
        }
    }

    private fun startNotificationService() {
        try {
            startForegroundService(Intent(this, PushNotificationHandlerService::class.java))
        } catch (e: Exception) {
            Log.d(TAG, "Unable to start notification service: ${e.message}", e)
        }
    }

    private fun isSmsPermissionGranted(): Boolean {
        val permission = android.Manifest.permission.RECEIVE_SMS
        val grant = ContextCompat.checkSelfPermission(this, permission)
        return grant == PackageManager.PERMISSION_GRANTED
    }

    private fun isReadSmsPermissionGranted(): Boolean {
        val permission = android.Manifest.permission.READ_SMS
        val grant = ContextCompat.checkSelfPermission(this, permission)
        return grant == PackageManager.PERMISSION_GRANTED
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
        safeLaunch { notificationListenerSettingsLauncher.launch(intent) }
    }

    private fun requestPostNotificationsPermission() {
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val grant = ContextCompat.checkSelfPermission(this, permission)
        if (grant == PackageManager.PERMISSION_GRANTED) return
        safeLaunch { postNotificationsPermissionLauncher.launch(permission) }
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
        safeLaunch { batteryOptimizationLauncher.launch(intent) }
    }

    private companion object {
        const val TAG: String = "MainActivity"
    }
}
