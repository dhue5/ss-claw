package com.example.engine

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.HermesApplication
import com.example.data.local.ExecutionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class HermesNotificationListener : NotificationListenerService() {

    private val listenerScope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isNotificationListenerConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isNotificationListenerConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return // Skip self

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName
        }

        val eventSummary = "[$appName] $title: ${text.take(60)}"
        _lastInterceptedNotification.value = eventSummary
        _interceptedNotificationCount.value += 1

        val app = application as? HermesApplication ?: return
        listenerScope.launch {
            val scripts = app.repository.getScriptsByTrigger("NOTIFICATION_POSTED")
            if (scripts.isNotEmpty()) {
                val deviceController = HermesDeviceController(this@HermesNotificationListener)
                val scriptEngine = HermesScriptEngine(deviceController)
                for (script in scripts.filter { it.isEnabled }) {
                    val res = scriptEngine.executeScript(script.scriptCode)
                    app.repository.recordScriptExecution(script.id)
                    app.repository.insertLog(
                        ExecutionLog(
                            sessionId = UUID.randomUUID().toString().take(8),
                            prompt = "Notification Trigger [$appName]: $title",
                            planSummary = "Dispatched by NotificationListenerService",
                            status = if (res.success) "SUCCESS" else "FAILED",
                            outputResult = res.output,
                            durationMs = res.durationMs,
                            isAiDriven = false,
                            sourceName = "Notification Sentinel"
                        )
                    )
                }
            }
        }
    }

    companion object {
        private val _isNotificationListenerConnected = MutableStateFlow(false)
        val isNotificationListenerConnected = _isNotificationListenerConnected.asStateFlow()

        private val _lastInterceptedNotification = MutableStateFlow<String?>(null)
        val lastInterceptedNotification = _lastInterceptedNotification.asStateFlow()

        private val _interceptedNotificationCount = MutableStateFlow(0)
        val interceptedNotificationCount = _interceptedNotificationCount.asStateFlow()

        fun isNotificationAccessGranted(context: Context): Boolean {
            val cn = ComponentName(context, HermesNotificationListener::class.java)
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(cn.flattenToString())
        }

        fun openNotificationAccessSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback
            }
        }
    }
}
