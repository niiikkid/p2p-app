package com.android.autopay

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.android.autopay.data.PingWorker
import com.android.autopay.data.PushNotificationHandlerService
import com.android.autopay.data.utils.PERIODIC_WORK_NAME
import com.android.autopay.data.utils.PING_WORK_NAME
import com.android.autopay.data.utils.PING_INTERVAL_SECONDS
import com.android.autopay.presentation.MainScreen
import com.android.autopay.presentation.ui.theme.AutoPayTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!isSmsPermissionGranted()) {
            requestReceiveSmsPermission()
        }

        if (!isNotificationServiceEnabled()) {
            requestNotificationListenerPermission()
        }

        if (!isBatteryOptimizationDisabled()) {
            openBatteryOptimizationSettings()
        }

        startForegroundService(
            Intent(this, PushNotificationHandlerService::class.java)
        )

        // Ретраи теперь выполняются в foreground-сервисе каждые RETRY_INTERVAL_SECONDS
        // Пинг теперь выполняется в foreground-сервисе каждые PING_INTERVAL_SECONDS

        setContent {
            AutoPayTheme {
                MainScreen()
            }
        }
    }

    // Удалено планирование периодического NotificationRetryWorker

    private fun setupPeriodicPingWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val repeatIntervalMinutes: Long = TimeUnit.SECONDS.toMinutes(PING_INTERVAL_SECONDS)
        val periodicWorkRequest = PeriodicWorkRequest.Builder(
            PingWorker::class.java, repeatIntervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                PING_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
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