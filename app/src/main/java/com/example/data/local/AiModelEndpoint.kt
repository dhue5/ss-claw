package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_model_endpoints")
data class AiModelEndpoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val provider: String, // GEMINI, OPENAI_COMPATIBLE, DEEPSEEK, QWEN, OLLAMA, CLAUDE, CUSTOM
    val baseUrl: String,
    val modelName: String,
    val apiKey: String = "",
    val customHeadersJson: String = "{}",
    val temperature: Float = 0.3f,
    val maxTokens: Int = 4096,
    val isActive: Boolean = false,
    val isPreset: Boolean = false,
    val latencyMs: Long? = null,
    val lastTestStatus: String? = null, // SUCCESS, FAILED, null
    val lastTestTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
