package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val prompt: String,
    val planSummary: String,
    val status: String, // SUCCESS, RUNNING, FAILED, PAUSED, CANCELED
    val stepsJson: String = "[]", // Details of each action taken
    val outputResult: String = "",
    val durationMs: Long = 0,
    val isAiDriven: Boolean = true,
    val sourceName: String = "Hermes Agent",
    val timestamp: Long = System.currentTimeMillis()
)
