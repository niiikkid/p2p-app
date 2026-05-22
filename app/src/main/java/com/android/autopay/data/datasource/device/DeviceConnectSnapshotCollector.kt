package com.android.autopay.data.datasource.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.android.autopay.utils.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class DeviceConnectSnapshotCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDispatchers: AppDispatchers
) {

    suspend fun collect(): String? = withContext(appDispatchers.default) {
        val snapshot: JSONObject = JSONObject()
        snapshot.put("schema_version", SCHEMA_VERSION)
        snapshot.put("collected_at", System.currentTimeMillis())
        putSection(snapshot, "app") { collectAppSection() }
        putSection(snapshot, "android") { collectAndroidSection() }
        putSection(snapshot, "device") { collectDeviceSection() }
        putSection(snapshot, "runtime") { collectRuntimeSection() }
        putSection(snapshot, "capabilities") { collectCapabilitiesSection() }
        runCatching { snapshot.toString() }
            .getOrNull()
            ?.takeIf { value -> value.isNotBlank() && value.length <= DEVICE_CONNECT_SNAPSHOT_LIMIT }
    }

    private fun putSection(target: JSONObject, name: String, sectionBuilder: () -> JSONObject) {
        runCatching { sectionBuilder() }
            .getOrNull()
            ?.takeIf { section -> section.length() > 0 }
            ?.let { section -> target.put(name, section) }
    }

    private fun collectAppSection(): JSONObject {
        val packageInfo: PackageInfo? = getPackageInfo()
        return JSONObject().apply {
            put("package_name", context.packageName)
            packageInfo?.versionName?.takeIf { value -> value.isNotBlank() }?.let { value -> put("version_name", value) }
            packageInfo?.let { value -> put("version_code", getVersionCode(value)) }
        }
    }

    private fun collectAndroidSection(): JSONObject {
        return JSONObject().apply {
            put("sdk_int", Build.VERSION.SDK_INT)
            putNonBlank("release", Build.VERSION.RELEASE)
            putNonBlank("security_patch", getSecurityPatch())
            putNonBlank("incremental", Build.VERSION.INCREMENTAL)
            putNonBlank("codename", Build.VERSION.CODENAME)
        }
    }

    private fun collectDeviceSection(): JSONObject {
        return JSONObject().apply {
            putNonBlank("manufacturer", Build.MANUFACTURER)
            putNonBlank("brand", Build.BRAND)
            putNonBlank("model", Build.MODEL)
            putNonBlank("device", Build.DEVICE)
            putNonBlank("product", Build.PRODUCT)
            putNonBlank("board", Build.BOARD)
            putNonBlank("hardware", Build.HARDWARE)
            putNonBlank("display", Build.DISPLAY)
            putNonBlank("fingerprint", Build.FINGERPRINT)
        }
    }

    private fun collectRuntimeSection(): JSONObject {
        return JSONObject().apply {
            put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            put("available_processors", Runtime.getRuntime().availableProcessors())
            putNonBlank("locale", Locale.getDefault().toString())
            putNonBlank("timezone", TimeZone.getDefault().id)
        }
    }

    private fun collectCapabilitiesSection(): JSONObject {
        return JSONObject().apply {
            put("notification_listener_enabled", isNotificationListenerEnabled())
            put("sms_receive_permission_granted", isPermissionGranted(Manifest.permission.RECEIVE_SMS))
            put("sms_read_permission_granted", isPermissionGranted(Manifest.permission.READ_SMS))
            put("post_notifications_permission_granted", isPostNotificationsPermissionGranted())
            put("battery_optimization_ignored", isBatteryOptimizationIgnored())
        }
    }

    private fun getPackageInfo(): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
    }

    private fun getVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun getSecurityPatch(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            null
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isPostNotificationsPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners: String = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        return enabledListeners.contains(context.packageName, ignoreCase = true)
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager: PowerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun JSONObject.putNonBlank(name: String, value: String?) {
        value?.takeIf { candidate -> candidate.isNotBlank() }?.let { candidate -> put(name, candidate) }
    }

    private companion object {
        const val SCHEMA_VERSION: Int = 1
        const val DEVICE_CONNECT_SNAPSHOT_LIMIT: Int = 1_048_576
    }
}
