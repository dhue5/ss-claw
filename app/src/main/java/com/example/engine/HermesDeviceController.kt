package com.example.engine

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.HermesApplication
import com.example.data.remote.HttpExecutor
import com.example.data.remote.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class DeviceStats(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val networkType: String,
    val freeStorageMb: Long,
    val totalStorageMb: Long,
    val isTorchOn: Boolean,
    val androidVersion: String,
    val deviceModel: String
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

class HermesDeviceController(
    private val context: Context
) {
    private val httpExecutor = HttpExecutor()
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isTorchEnabled = false

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    isTtsReady = true
                }
            }
        } catch (e: Exception) {
            isTtsReady = false
        }
    }

    fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun vibrate(durationMs: Long = 200) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String): Boolean {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hermes_speech_${System.currentTimeMillis()}")
            return true
        }
        return false
    }

    fun readClipboard(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString() ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun setClipboard(text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Hermes Output", text)
            clipboard.setPrimaryClip(clip)
            showToast("Copied to clipboard")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendNotification(title: String, message: String): Boolean {
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, HermesApplication.CHANNEL_AUTOMATION_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            manager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun showNotification(title: String, message: String): Boolean = sendNotification(title, message)

    fun launchApp(packageOrName: String): ActionResult {
        return try {
            val pm = context.packageManager
            // 1. Try direct package launch
            var launchIntent = pm.getLaunchIntentForPackage(packageOrName)

            // 2. If not found, search installed apps by label
            if (launchIntent == null) {
                val packages = pm.getInstalledApplications(0)
                for (app in packages) {
                    val appLabel = pm.getApplicationLabel(app).toString()
                    if (appLabel.contains(packageOrName, ignoreCase = true) ||
                        app.packageName.contains(packageOrName, ignoreCase = true)) {
                        launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) break
                    }
                }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ActionResult(true, "App launched: $packageOrName")
            } else {
                ActionResult(false, "Could not find installed app matching '$packageOrName'")
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to launch app: ${e.message}")
        }
    }

    fun openUrl(url: String): ActionResult {
        return try {
            var formattedUrl = url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                formattedUrl = "https://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opened URL: $formattedUrl")
        } catch (e: Exception) {
            ActionResult(false, "Failed to open URL: ${e.message}")
        }
    }

    fun openSettings(settingType: String = "general"): ActionResult {
        return try {
            val action = when (settingType.lowercase()) {
                "battery", "power" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "wifi", "wireless" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "date", "time" -> Settings.ACTION_DATE_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opened $settingType settings")
        } catch (e: Exception) {
            ActionResult(false, "Failed to open settings: ${e.message}")
        }
    }

    fun toggleFlashlight(enabled: Boolean): ActionResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return ActionResult(false, "No camera flash found")
                cameraManager.setTorchMode(cameraId, enabled)
                isTorchEnabled = enabled
                ActionResult(true, if (enabled) "Torch enabled" else "Torch disabled")
            } else {
                ActionResult(false, "Flashlight API not supported on this OS version")
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to toggle flashlight: ${e.message}")
        }
    }

    fun getDeviceStats(): DeviceStats {
        // Battery
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 50
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Network
        var netType = "Offline"
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        if (capabilities != null) {
            netType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular 4G/5G"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        }

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        val totalMb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024)

        return DeviceStats(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            networkType = netType,
            freeStorageMb = freeMb,
            totalStorageMb = totalMb,
            isTorchOn = isTorchEnabled,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    fun getDeviceStatsJson(): String {
        val stats = getDeviceStats()
        val json = JSONObject().apply {
            put("batteryLevel", stats.batteryLevel)
            put("isCharging", stats.isCharging)
            put("networkType", stats.networkType)
            put("freeStorageMb", stats.freeStorageMb)
            put("totalStorageMb", stats.totalStorageMb)
            put("torchActive", stats.isTorchOn)
            put("androidVersion", stats.androidVersion)
            put("deviceModel", stats.deviceModel)
        }
        return json.toString()
    }

    suspend fun httpFetch(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): HttpResponse {
        return httpExecutor.execute(method, url, headers, body)
    }

    suspend fun saveLocalFile(fileName: String, content: String): ActionResult = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "hermes_storage")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(content)
            ActionResult(true, "File saved to ${file.absolutePath}", file.absolutePath)
        } catch (e: Exception) {
            ActionResult(false, "Failed to save file: ${e.message}")
        }
    }

    suspend fun readLocalFile(fileName: String): ActionResult = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "hermes_storage")
            val file = File(dir, fileName)
            if (file.exists()) {
                ActionResult(true, "File read successfully", file.readText())
            } else {
                ActionResult(false, "File does not exist: $fileName")
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to read file: ${e.message}")
        }
    }

    // --- ACCESSIBILITY RPA SCREEN AUTOMATION ---

    fun clickScreenText(text: String, exact: Boolean = false): ActionResult {
        val service = HermesAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is disabled. Please enable it in Settings.")
        val success = service.clickByText(text, exact)
        return ActionResult(success, if (success) "Clicked on '$text'" else "Text '$text' not found on screen")
    }

    fun clickScreenId(viewId: String): ActionResult {
        val service = HermesAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is disabled. Please enable it in Settings.")
        val success = service.clickById(viewId)
        return ActionResult(success, if (success) "Clicked ID '$viewId'" else "View ID '$viewId' not found")
    }

    fun tapCoordinate(x: Float, y: Float): ActionResult {
        val service = HermesAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is disabled.")
        val success = service.performTapCoordinate(x, y)
        return ActionResult(success, if (success) "Tapped at ($x, $y)" else "Tap gesture failed")
    }

    fun swipeScreen(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): ActionResult {
        val service = HermesAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is disabled.")
        val success = service.performSwipeGesture(startX, startY, endX, endY, durationMs)
        return ActionResult(success, if (success) "Swiped from ($startX, $startY) to ($endX, $endY)" else "Swipe gesture failed")
    }

    fun inputText(text: String): ActionResult {
        val service = HermesAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is disabled.")
        val success = service.inputByTextOrFocus(text)
        return ActionResult(success, if (success) "Input text: '$text'" else "No focused editable input on screen")
    }

    fun inspectScreenNodes(): ActionResult {
        val service = HermesAccessibilityService.instance
            ?: return ActionResult(false, "Accessibility Service is disabled.")
        val nodes = service.dumpScreenHierarchy()
        return ActionResult(true, "Inspected ${nodes.size} UI elements", nodes.joinToString("\n"))
    }

    fun toggleFloatingBubble(enable: Boolean): ActionResult {
        if (enable) {
            if (!FloatingOverlayHelper.canDrawOverlays(context)) {
                return ActionResult(false, "Overlay permission not granted. Request in Settings.")
            }
            HermesFloatingService.start(context)
            return ActionResult(true, "Floating Hermes bubble displayed on screen")
        } else {
            HermesFloatingService.stop(context)
            return ActionResult(true, "Floating Hermes bubble hidden")
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
