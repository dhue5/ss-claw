package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.HermesDatabase
import com.example.data.repository.HermesRepository
import com.example.engine.HermesBackgroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HermesApplication : Application() {

    val database by lazy { HermesDatabase.getDatabase(this) }
    val repository by lazy {
        HermesRepository(
            database.scriptDao(),
            database.pluginDao(),
            database.logDao(),
            database.configDao(),
            database.modelEndpointDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startDaemonIfConfigured()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // High Priority Channel for executed automations & alerts
            val autoChannel = NotificationChannel(
                CHANNEL_AUTOMATION_ID,
                "Hermes Automation & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications and triggers dispatched by Hermes Automation Engine"
                enableVibration(true)
            }
            manager.createNotificationChannel(autoChannel)

            // Low Priority Channel for 24/7 Ongoing Foreground Service
            val daemonChannel = NotificationChannel(
                CHANNEL_DAEMON_ID,
                "Hermes 24/7 Agent Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Hermes autonomous agent running in phone background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(daemonChannel)
        }
    }

    private fun startDaemonIfConfigured() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = repository.getConfig()
                if (config.enableBackgroundDaemon) {
                    HermesBackgroundService.start(this@HermesApplication)
                }
            } catch (e: Exception) {
                // Ignore during initial setup
            }
        }
    }

    companion object {
        const val CHANNEL_AUTOMATION_ID = "hermes_automation_channel"
        const val CHANNEL_DAEMON_ID = "hermes_daemon_channel"
    }
}

