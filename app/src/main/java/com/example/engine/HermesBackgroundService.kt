package com.example.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.HermesApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.ExecutionLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class HermesBackgroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastObservedClip: String? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var periodicJob: Job? = null

    private lateinit var deviceController: HermesDeviceController
    private lateinit var scriptEngine: HermesScriptEngine

    override fun onCreate() {
        super.onCreate()
        val app = application as HermesApplication
        deviceController = HermesDeviceController(this)
        scriptEngine = HermesScriptEngine(deviceController)

        _isServiceRunning.value = true
        _serviceStartTime.value = System.currentTimeMillis()
        _lastDaemonEvent.value = "Hermes Daemon Started"
        _daemonEventCount.value = 0

        val initialNotification = buildNotification("Hermes 24/7 Agent Daemon Active", "Monitoring device events & automations")
        startForeground(NOTIFICATION_ID, initialNotification)

        setupListeners(app)
        startPeriodicLoop(app)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RUN_BRIEFING -> {
                triggerQuickBriefing()
            }
            ACTION_REFRESH_STATUS -> {
                updateNotificationStatus()
            }
        }
        return START_STICKY
    }

    private fun setupListeners(app: HermesApplication) {
        // 1. Clipboard Listener
        try {
            val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                serviceScope.launch {
                    val config = app.repository.getConfig()
                    if (!config.enableClipboardMonitoring) return@launch

                    val text = deviceController.readClipboard()
                    if (!text.isNullOrBlank() && text != lastObservedClip) {
                        lastObservedClip = text
                        _daemonEventCount.value += 1
                        _lastDaemonEvent.value = "Clipboard modified: ${text.take(20)}..."

                        // Find matching scripts
                        val scripts = app.repository.getScriptsByTrigger("CLIPBOARD_CHANGE")
                        for (script in scripts.filter { it.isEnabled }) {
                            val res = scriptEngine.executeScript(script.scriptCode)
                            app.repository.recordScriptExecution(script.id)
                            app.repository.insertLog(
                                ExecutionLog(
                                    sessionId = UUID.randomUUID().toString().take(8),
                                    prompt = "Background Trigger: Clipboard [${script.name}]",
                                    planSummary = "Triggered by system clipboard event in background",
                                    status = if (res.success) "SUCCESS" else "FAILED",
                                    outputResult = res.output,
                                    durationMs = res.durationMs,
                                    isAiDriven = false,
                                    sourceName = "Background Daemon"
                                )
                            )
                        }
                        updateNotificationStatus()
                    }
                }
            }
            clipManager?.addPrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            // Ignore if clipboard access restricted in some OEM background states
        }

        // 2. Battery & Power Receiver
        try {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    serviceScope.launch {
                        val config = app.repository.getConfig()
                        if (!config.enableBatteryGuardMonitoring) return@launch

                        if (action == Intent.ACTION_BATTERY_LOW || action == Intent.ACTION_POWER_CONNECTED) {
                            _daemonEventCount.value += 1
                            val eventName = if (action == Intent.ACTION_BATTERY_LOW) "Battery Low Warning" else "Power Charger Connected"
                            _lastDaemonEvent.value = "Event: $eventName"

                            val scripts = app.repository.getScriptsByTrigger("BATTERY_LOW")
                            for (script in scripts.filter { it.isEnabled }) {
                                val res = scriptEngine.executeScript(script.scriptCode)
                                app.repository.recordScriptExecution(script.id)
                                app.repository.insertLog(
                                    ExecutionLog(
                                        sessionId = UUID.randomUUID().toString().take(8),
                                        prompt = "Background Trigger: $eventName [${script.name}]",
                                        planSummary = "Executed battery guard automation in background",
                                        status = if (res.success) "SUCCESS" else "FAILED",
                                        outputResult = res.output,
                                        durationMs = res.durationMs,
                                        isAiDriven = false,
                                        sourceName = "Background Daemon"
                                    )
                                )
                            }
                            updateNotificationStatus()
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            // Log fallback
        }
    }

    private fun startPeriodicLoop(app: HermesApplication) {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                val config = app.repository.getConfig()
                val intervalMinutes = config.periodicCheckIntervalMinutes.coerceAtLeast(1)
                delay(intervalMinutes * 60 * 1000L)

                // Periodic tick
                _daemonEventCount.value += 1
                _lastDaemonEvent.value = "Periodic background check at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

                // Execute periodic scripts if any configured
                val periodicScripts = app.repository.getScriptsByTrigger("PERIODIC")
                for (script in periodicScripts.filter { it.isEnabled }) {
                    val res = scriptEngine.executeScript(script.scriptCode)
                    app.repository.recordScriptExecution(script.id)
                    app.repository.insertLog(
                        ExecutionLog(
                            sessionId = UUID.randomUUID().toString().take(8),
                            prompt = "Periodic Auto-Check: ${script.name}",
                            planSummary = "Dispatched on ${intervalMinutes}m interval cycle",
                            status = if (res.success) "SUCCESS" else "FAILED",
                            outputResult = res.output,
                            durationMs = res.durationMs,
                            isAiDriven = false,
                            sourceName = "Background Daemon"
                        )
                    )
                }
                updateNotificationStatus()
            }
        }
    }

    private fun triggerQuickBriefing() {
        serviceScope.launch {
            val stats = deviceController.getDeviceStats()
            val text = "Hermes Daemon Status: Battery at ${stats.batteryLevel}%, Network is ${stats.networkType}. Background automation active."
            deviceController.showNotification("Hermes Status Briefing", text)
            deviceController.speak(text)
            deviceController.vibrate(200)
            _daemonEventCount.value += 1
            _lastDaemonEvent.value = "Dispatched Audio Briefing"
            updateNotificationStatus()
        }
    }

    private fun updateNotificationStatus() {
        val stats = deviceController.getDeviceStats()
        val uptimeMinutes = (System.currentTimeMillis() - _serviceStartTime.value) / 60000
        val content = "Uptime: ${uptimeMinutes}m | Batt: ${stats.batteryLevel}% | Events: ${_daemonEventCount.value}"
        val notif = buildNotification("Hermes 24/7 Daemon [Active]", content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Quick Briefing
        val briefIntent = Intent(this, HermesBackgroundService::class.java).apply {
            action = ACTION_RUN_BRIEFING
        }
        val briefPendingIntent = PendingIntent.getService(
            this,
            1,
            briefIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop Service
        val stopIntent = Intent(this, HermesBackgroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, HermesApplication.CHANNEL_DAEMON_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Status Brief", briefPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Daemon", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        periodicJob?.cancel()
        serviceScope.cancel()

        try {
            val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardListener?.let { clipManager?.removePrimaryClipChangedListener(it) }
        } catch (e: Exception) {}

        try {
            batteryReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {}

        deviceController.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP_SERVICE = "com.example.hermes.ACTION_STOP_SERVICE"
        const val ACTION_RUN_BRIEFING = "com.example.hermes.ACTION_RUN_BRIEFING"
        const val ACTION_REFRESH_STATUS = "com.example.hermes.ACTION_REFRESH_STATUS"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _serviceStartTime = MutableStateFlow(0L)
        val serviceStartTime = _serviceStartTime.asStateFlow()

        private val _daemonEventCount = MutableStateFlow(0)
        val daemonEventCount = _daemonEventCount.asStateFlow()

        private val _lastDaemonEvent = MutableStateFlow<String?>(null)
        val lastDaemonEvent = _lastDaemonEvent.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, HermesBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HermesBackgroundService::class.java)
            context.stopService(intent)
            _isServiceRunning.value = false
        }
    }
}
