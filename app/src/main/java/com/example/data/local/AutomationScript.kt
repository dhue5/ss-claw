package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_scripts")
data class AutomationScript(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val triggerType: String = "MANUAL", // MANUAL, SCHEDULED, BATTERY_LOW, CLIPBOARD_CHANGE, API_WEBHOOK, SHAKE
    val scriptCode: String, // JavaScript or Hermes DSL code
    val isEnabled: Boolean = true,
    val version: String = "1.0.0",
    val author: String = "User",
    val tags: String = "automation,utility",
    val executionCount: Int = 0,
    val lastExecutedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
