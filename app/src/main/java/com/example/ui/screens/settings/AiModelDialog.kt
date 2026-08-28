package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.AiModelEndpoint
import com.example.ui.theme.*

@Composable
fun AiModelDialog(
    model: AiModelEndpoint?,
    onDismiss: () -> Unit,
    onSave: (AiModelEndpoint) -> Unit,
    onTest: (AiModelEndpoint) -> Unit,
    isTesting: Boolean = false
) {
    var name by remember { mutableStateOf(model?.name ?: "") }
    var provider by remember { mutableStateOf(model?.provider ?: "OPENAI_COMPATIBLE") }
    var baseUrl by remember { mutableStateOf(model?.baseUrl ?: "https://api.openai.com/v1") }
    var modelName by remember { mutableStateOf(model?.modelName ?: "gpt-4o-mini") }
    var apiKey by remember { mutableStateOf(model?.apiKey ?: "") }
    var customHeadersJson by remember { mutableStateOf(model?.customHeadersJson ?: "{}") }
    var temperature by remember { mutableFloatStateOf(model?.temperature ?: 0.3f) }
    var isActive by remember { mutableStateOf(model?.isActive ?: false) }

    val providers = listOf(
        "GEMINI" to "Google Gemini",
        "DEEPSEEK" to "DeepSeek (OpenAI兼容)",
        "QWEN" to "通义千问 (阿里云百炼)",
        "OPENAI_COMPATIBLE" to "OpenAI / 中转接口",
        "OLLAMA" to "本地 Ollama (局域网)",
        "CLAUDE" to "Claude (兼容格式)",
        "CUSTOM" to "自定义 API 端点"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (model == null) "接入新 AI 模型 / API" else "编辑模型端点配置",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模型显示名称") },
                    placeholder = { Text("例: DeepSeek 官方极速版") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Provider selection
                Text("API 协议与服务商:", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    providers.chunked(2).forEach { rowProviders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowProviders.forEach { (pKey, pLabel) ->
                                val isSelected = provider == pKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) CyanPrimary.copy(alpha = 0.2f) else ObsidianSurfaceVariant)
                                        .border(1.dp, if (isSelected) CyanPrimary else ObsidianCardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            provider = pKey
                                            // Preset URL defaults
                                            when (pKey) {
                                                "GEMINI" -> {
                                                    baseUrl = "https://generativelanguage.googleapis.com"
                                                    if (modelName.isBlank() || modelName == "gpt-4o-mini") modelName = "gemini-2.5-flash"
                                                }
                                                "DEEPSEEK" -> {
                                                    baseUrl = "https://api.deepseek.com/v1"
                                                    modelName = "deepseek-chat"
                                                }
                                                "QWEN" -> {
                                                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                                                    modelName = "qwen-max"
                                                }
                                                "OLLAMA" -> {
                                                    baseUrl = "http://192.168.1.100:11434/v1"
                                                    modelName = "llama3:latest"
                                                }
                                                "CLAUDE" -> {
                                                    baseUrl = "https://api.anthropic.com/v1"
                                                    modelName = "claude-3-5-sonnet-20241022"
                                                }
                                                "OPENAI_COMPATIBLE" -> {
                                                    baseUrl = "https://api.openai.com/v1"
                                                    modelName = "gpt-4o-mini"
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) CyanPrimary else TextSecondary
                                    )
                                }
                            }
                            if (rowProviders.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 基础接口地址 (Base URL)") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Model ID
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型标识符 (Model ID)") },
                    placeholder = { Text("例: deepseek-chat, gpt-4o, qwen-max") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API 密钥 (API Key / Token)") },
                    placeholder = { Text("sk-...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("采样温度 (Temperature):", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text(String.format("%.2f", temperature), style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..1.0f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Set Active switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("设为主思考模型", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("保存后立即生效并用于意图规划", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanPrimary,
                            checkedTrackColor = CyanPrimary.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons: Test Ping & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val tempModel = AiModelEndpoint(
                                id = model?.id ?: 0L,
                                name = name.ifBlank { modelName },
                                provider = provider,
                                baseUrl = baseUrl,
                                modelName = modelName,
                                apiKey = apiKey,
                                customHeadersJson = customHeadersJson,
                                temperature = temperature,
                                isActive = isActive,
                                isPreset = model?.isPreset ?: false
                            )
                            onTest(tempModel)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AmberWarning, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("测速中...")
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = "测试延迟", tint = AmberWarning)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("测试延迟")
                        }
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) name = modelName
                            val saved = AiModelEndpoint(
                                id = model?.id ?: 0L,
                                name = name,
                                provider = provider,
                                baseUrl = baseUrl,
                                modelName = modelName,
                                apiKey = apiKey,
                                customHeadersJson = customHeadersJson,
                                temperature = temperature,
                                isActive = isActive,
                                isPreset = model?.isPreset ?: false
                            )
                            onSave(saved)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimaryContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存", tint = CyanOnPrimaryContainer)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存配置", color = CyanOnPrimaryContainer)
                    }
                }
            }
        }
    }
}
