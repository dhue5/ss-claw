package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.theme.*

@Composable
fun HotUpdateDialog(
    isChecking: Boolean,
    statusMessage: String?,
    onDismiss: () -> Unit,
    onCheckUpdates: () -> Unit,
    onApplyPatch: (String) -> Unit,
    onReadClipboard: () -> String
) {
    var patchJson by remember { mutableStateOf("") }
    var selectedMode by remember { mutableIntStateOf(0) } // 0: Online OTA, 1: Manual JSON Patch

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PurpleSecondary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = "升级", tint = PurpleSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "热更新与升级中心",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurfaceVariant)
                        .padding(4.dp)
                ) {
                    Button(
                        onClick = { selectedMode = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMode == 0) PurpleSecondary.copy(alpha = 0.25f) else ObsidianSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "在线热升级",
                            color = if (selectedMode == 0) PurpleSecondary else TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Button(
                        onClick = { selectedMode = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMode == 1) CyanPrimary.copy(alpha = 0.25f) else ObsidianSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "导入补丁包",
                            color = if (selectedMode == 1) CyanPrimary else TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedMode == 0) {
                    // Online OTA Update
                    Text(
                        text = "Hermes 支持免重启热更新。无需重新编译或安装应用，即可动态同步最新大模型配置、推理参数、自动化脚本库及扩展插件热修复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("当前架构引擎版本:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("v1.1.0-PRO (Live Engine)", style = MaterialTheme.typography.labelSmall, color = EmeraldTertiary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("热重载与执行器状态:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("常驻热就绪 (Hot-Ready)", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!statusMessage.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = onCheckUpdates,
                        enabled = !isChecking,
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleSecondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ObsidianBg, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在同步最新模型与脚本...", color = ObsidianBg)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = "同步", tint = ObsidianBg)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即检查并同步最新热更新", color = ObsidianBg)
                        }
                    }
                } else {
                    // Manual JSON Patch Import
                    Text(
                        text = "粘贴社区或自建的热更新 JSON 补丁包（包含 models、scripts、plugins 定义），一键动态合入本地知识库：",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = patchJson,
                        onValueChange = { patchJson = it },
                        label = { Text("补丁 JSON 内容") },
                        placeholder = { Text("{\n  \"models\": [...],\n  \"scripts\": [...]\n}") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedContainerColor = ObsidianBg,
                            unfocusedContainerColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = onReadClipboard()
                                if (clip.isNotBlank()) patchJson = clip
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴", tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("从剪贴板粘贴")
                        }

                        Button(
                            onClick = {
                                if (patchJson.isNotBlank()) {
                                    onApplyPatch(patchJson)
                                }
                            },
                            enabled = patchJson.isNotBlank() && !isChecking,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = "安装", tint = CyanOnPrimaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("安装补丁", color = CyanOnPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}
