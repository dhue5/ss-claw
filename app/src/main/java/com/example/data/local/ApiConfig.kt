package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_configs")
data class ApiConfig(
    @PrimaryKey
    val id: String = "default_config",
    val customApiKey: String = "",
    val customBaseUrl: String = "https://generativelanguage.googleapis.com/",
    val activeModel: String = "gemini-3.5-flash",
    val systemPrompt: String = "You are Hermes, an advanced autonomous Android local agent. You analyze user instructions and plan precise phone automation actions.",
    val temperature: Float = 0.4f,
    val enableTtsVoice: Boolean = true,
    val enableVibrationFeedback: Boolean = true,
    val enableAutoExecuteSafeActions: Boolean = true,
    val webhookPort: Int = 8088,
    val enableBackgroundDaemon: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    val enableClipboardMonitoring: Boolean = true,
    val enableBatteryGuardMonitoring: Boolean = true,
    val periodicCheckIntervalMinutes: Int = 15
)

