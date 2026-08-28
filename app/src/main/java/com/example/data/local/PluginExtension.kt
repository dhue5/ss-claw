package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_extensions")
data class PluginExtension(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageId: String, // e.g. "com.hermes.plugin.telegram"
    val name: String,
    val version: String = "1.0.0",
    val author: String = "Hermes Community",
    val description: String,
    val iconName: String = "extension", // icon token
    val isEnabled: Boolean = true,
    val permissions: String = "INTERNET,NOTIFICATION", // comma separated
    val configJson: String = "{}", // Config values (API keys, webhook URLs, preferences)
    val actionsJson: String = "[]", // JSON array of action definitions/tools provided
    val sourceCode: String = "", // Dynamic plugin script/handler
    val updateUrl: String = "", // Remote manifest/update endpoint
    val changelog: String = "Initial release",
    val installedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
