package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AiModelEndpoint
import com.example.ui.HermesViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: HermesViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val modelsList by viewModel.models.collectAsState()
    val activeEndpoint by viewModel.activeModelEndpoint.collectAsState()

    val isDaemonRunning by viewModel.isDaemonRunning.collectAsState()
    val daemonStartTime by viewModel.daemonStartTime.collectAsState()
    val daemonEventCount by viewModel.daemonEventCount.collectAsState()
    val lastDaemonEvent by viewModel.lastDaemonEvent.collectAsState()

    var systemPrompt by remember(config) { mutableStateOf(config.systemPrompt) }
    var enableTts by remember(config) { mutableStateOf(config.enableTtsVoice) }
    var enableVibrate by remember(config) { mutableStateOf(config.enableVibrationFeedback) }

    var enableBackgroundDaemon by remember(config) { mutableStateOf(config.enableBackgroundDaemon) }
    var autoStartOnBoot by remember(config) { mutableStateOf(config.autoStartOnBoot) }
    var enableClipboardMonitoring by remember(config) { mutableStateOf(config.enableClipboardMonitoring) }
    var enableBatteryGuardMonitoring by remember(config) { mutableStateOf(config.enableBatteryGuardMonitoring) }
    var periodicInterval by remember(config) { mutableStateOf(config.periodicCheckIntervalMinutes) }

    val intervals = listOf(1, 5, 15, 30, 60)

    val isWhitelisted = remember { viewModel.isBatteryOptimizationWhitelisted() }
    val isAccessibilityOn = remember { viewModel.isAccessibilityEnabled() }
    val isNotifListenerOn = remember { viewModel.isNotificationAccessGranted() }
    val canDrawOverlay = remember { viewModel.canDrawOverlays() }
    val currentApp by viewModel.currentActiveApp.collectAsState()
    val interceptedCount by viewModel.interceptedNotificationCount.collectAsState()
    val lastNotif by viewModel.lastInterceptedNotification.collectAsState()
    val isBubbleOn by viewModel.isBubbleVisible.collectAsState()

    // Dialogs
    if (uiState.showModelEditor) {
        AiModelDialog(
            model = uiState.editingModel,
            onDismiss = { viewModel.dismissModelEditor() },
            onSave = { updatedModel -> viewModel.saveModelEndpoint(updatedModel) },
            onTest = { tempModel -> viewModel.testModelEndpoint(tempModel) },
            isTesting = uiState.testingModelId != null
        )
    }

    if (uiState.showHotUpdateDialog) {
        HotUpdateDialog(
            isChecking = uiState.isCheckingHotUpdate,
            statusMessage = uiState.hotUpdateStatusMessage,
            onDismiss = { viewModel.dismissHotUpdateDialog() },
            onCheckUpdates = { viewModel.checkForHotUpdates() },
            onApplyPatch = { patchJson -> viewModel.applyHotUpdatePatch(patchJson) },
            onReadClipboard = { viewModel.deviceController.readClipboard() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "系统与模型设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "多大模型接入、24/7 守护哨兵、热更新与设备桥接",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Button(
                onClick = {
                    viewModel.saveApiConfig(
                        customApiKey = config.customApiKey,
                        activeModel = activeEndpoint?.modelName ?: config.activeModel,
                        systemPrompt = systemPrompt,
                        temperature = config.temperature,
                        enableTtsVoice = enableTts,
                        enableVibrationFeedback = enableVibrate,
                        enableBackgroundDaemon = enableBackgroundDaemon,
                        autoStartOnBoot = autoStartOnBoot,
                        enableClipboardMonitoring = enableClipboardMonitoring,
                        enableBatteryGuardMonitoring = enableBatteryGuardMonitoring,
                        periodicCheckIntervalMinutes = periodicInterval
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = "保存设置", tint = CyanOnPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存设置", color = CyanOnPrimary)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. MULTI-MODEL ENDPOINTS & API CONFIGURATION
            item {
                CyberCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Hub, contentDescription = "多模型", tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "外部大模型与 API 端点管理",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        StatusBadge(
                            text = "${modelsList.size} 个已配置",
                            color = CyanPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "支持同时接入并保存多个外部 API 接口与大模型（Google Gemini、DeepSeek、阿里通义千问、OpenAI、本地 Ollama、Claude 等）。随时一键热切换为主思考大脑或测试网络延迟。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // List of Model Endpoints
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        modelsList.forEach { endpoint ->
                            val isCurrentActive = endpoint.isActive || (activeEndpoint?.id == endpoint.id)
                            val isTestingThis = uiState.testingModelId == endpoint.id

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrentActive) CyanPrimary.copy(alpha = 0.08f) else ObsidianSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isCurrentActive) CyanPrimary.copy(alpha = 0.8f) else ObsidianCardBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = "选择",
                                                tint = if (isCurrentActive) EmeraldTertiary else TextMuted,
                                                modifier = Modifier.clickable { viewModel.setActiveModel(endpoint.id) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = endpoint.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (isCurrentActive) CyanPrimary else TextPrimary
                                                )
                                                Text(
                                                    text = "${endpoint.provider} • ${endpoint.modelName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                            }
                                        }

                                        // Status tags
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (endpoint.latencyMs != null) {
                                                val isGood = endpoint.lastTestStatus == "SUCCESS"
                                                StatusBadge(
                                                    text = if (isGood) "${endpoint.latencyMs}ms" else "失败",
                                                    color = if (isGood) EmeraldTertiary else RedError
                                                )
                                            }
                                            if (isCurrentActive) {
                                                StatusBadge(text = "主模型", color = CyanPrimary)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (!isCurrentActive) {
                                            Button(
                                                onClick = { viewModel.setActiveModel(endpoint.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimaryContainer),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("设为主模型", color = CyanOnPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.testModelEndpoint(endpoint) },
                                            enabled = !isTestingThis,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isTestingThis) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = AmberWarning, strokeWidth = 1.5.dp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("测试中", style = MaterialTheme.typography.labelSmall)
                                            } else {
                                                Icon(Icons.Default.Speed, contentDescription = "测试", tint = AmberWarning, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("测速", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.openModelEditor(endpoint) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = CyanPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("编辑", style = MaterialTheme.typography.labelSmall)
                                        }

                                        if (!endpoint.isPreset) {
                                            IconButton(
                                                onClick = { viewModel.deleteModelEndpoint(endpoint.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = RedError, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.openModelEditor(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加", tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("接入新外部 AI 模型 / 自定义 API 接口", color = CyanPrimary)
                    }
                }
            }

            // 2. HOT UPDATE & UPGRADE CENTER
            item {
                CyberCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = "热升级", tint = PurpleSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "热更新与动态升级中心",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        StatusBadge(text = "HOT-RELOAD", color = PurpleSecondary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "支持无需重启应用即可对大模型配置、推理提示词、自动化 DSL 脚本以及扩展插件进行热更新与动态重载升级。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.openHotUpdateDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleSecondary.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = "热更新", tint = PurpleSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("检查热更新与补丁", color = PurpleSecondary)
                        }
                    }
                }
            }

            // 3. 24/7 Background Sentinel Daemon
            item {
                CyberCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "24/7 守护进程",
                                tint = if (isDaemonRunning) EmeraldTertiary else AmberWarning
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "24/7 后台守护哨兵",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        StatusBadge(
                            text = if (isDaemonRunning) "运行中" else "已停止",
                            color = if (isDaemonRunning) EmeraldTertiary else RedError
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "通过 Android 前台常驻服务让 Hermes 在手机后台持续运转。自动监听剪贴板变更、低电量预警，并定时执行自动化任务，无需保持应用在前台打开。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (isDaemonRunning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                val uptimeMin = if (daemonStartTime > 0) {
                                    (System.currentTimeMillis() - daemonStartTime) / 60000
                                } else 0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("哨兵已运行时间:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text("${uptimeMin} 分钟", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("后台已处理事件总数:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text("$daemonEventCount 个事件", style = MaterialTheme.typography.labelSmall, color = EmeraldTertiary)
                                }
                                if (!lastDaemonEvent.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("最近触发事件:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Text(lastDaemonEvent ?: "", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("启用 24/7 后台常驻守护", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("启动常驻前台服务并在状态栏保留守护图标", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = enableBackgroundDaemon,
                            onCheckedChange = { enableBackgroundDaemon = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldTertiary,
                                checkedTrackColor = EmeraldTertiary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    HorizontalDivider(color = ObsidianCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("开机自启守护", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("手机开机启动完成时自动拉起 Hermes 守护服务", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = autoStartOnBoot,
                            onCheckedChange = { autoStartOnBoot = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldTertiary,
                                checkedTrackColor = EmeraldTertiary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    HorizontalDivider(color = ObsidianCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("后台剪贴板监听哨兵", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("复制文本时自动触发 CLIPBOARD_CHANGE 自动化脚本", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = enableClipboardMonitoring,
                            onCheckedChange = { enableClipboardMonitoring = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldTertiary,
                                checkedTrackColor = EmeraldTertiary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    HorizontalDivider(color = ObsidianCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("后台电量与电源状态哨兵", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("电量过低或电源变动时自动触发预警任务", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = enableBatteryGuardMonitoring,
                            onCheckedChange = { enableBatteryGuardMonitoring = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldTertiary,
                                checkedTrackColor = EmeraldTertiary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("后台周期性巡检间隔:", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        intervals.forEach { min ->
                            val isSelected = periodicInterval == min
                            Button(
                                onClick = { periodicInterval = min },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) CyanPrimaryContainer else ObsidianSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${min}分钟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) CyanOnPrimaryContainer else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isWhitelisted) EmeraldTertiary.copy(alpha = 0.1f) else AmberWarning.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "系统电池优化白名单 (忽略电池优化)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (isWhitelisted) "✓ 已加入白名单（系统不会休眠或终止 Hermes）" else "⚠️ 未加入白名单（系统可能会冻结后台任务）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isWhitelisted) EmeraldTertiary else AmberWarning
                                    )
                                }
                            }

                            if (!isWhitelisted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.requestBatteryOptimizationExemption(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.BatterySaver, contentDescription = "申请电池白名单", tint = CyanPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("申请忽略系统电池优化", color = CyanPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Autonomous Persona Prompt
            item {
                CyberCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = "人设", tint = PurpleSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "智能体人设与系统提示词",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("系统级指令提示词 (System Prompt)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PurpleSecondary,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedContainerColor = ObsidianBg,
                            unfocusedContainerColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            }

            // 5. RPA Accessibility & Notification Sentinels
            item {
                CyberCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessibilityNew, contentDescription = "RPA 无障碍", tint = PurpleSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("屏幕 RPA 与无障碍自动化", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        StatusBadge(
                            text = if (isAccessibilityOn) "已连接" else "未开启",
                            color = if (isAccessibilityOn) EmeraldTertiary else AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "允许 Hermes 在手机的任意应用中自动点击屏幕、输入文本、滑动界面并深度读取屏幕节点树。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (isAccessibilityOn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("当前前台活跃应用:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(currentApp, style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.openAccessibilitySettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SettingsAccessibility, contentDescription = "打开设置", tint = PurpleSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAccessibilityOn) "管理无障碍服务设置" else "在系统设置中开启无障碍服务", color = PurpleSecondary)
                    }
                }
            }

            // 6. Global Floating Assistant Bubble
            item {
                CyberCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Layers, contentDescription = "悬浮气泡", tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("全局悬浮交互气泡", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        StatusBadge(
                            text = if (isBubbleOn) "已显示" else "已隐藏",
                            color = if (isBubbleOn) CyanPrimary else TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "在任何第三方应用之上常驻显示 Hermes 赛博风悬浮球，随时点击快速唤醒并下达手机指令。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!canDrawOverlay) {
                            Button(
                                onClick = { viewModel.requestOverlayPermission(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("授予悬浮窗权限", color = AmberWarning)
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleFloatingBubble() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isBubbleOn) RedError.copy(alpha = 0.2f) else CyanPrimaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isBubbleOn) "关闭悬浮气泡" else "启动全局悬浮气泡",
                                color = if (isBubbleOn) RedError else CyanOnPrimaryContainer
                            )
                        }
                    }
                }
            }

            // 7. Hardware & Sensory Feedback
            item {
                CyberCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = "反馈", tint = EmeraldTertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "硬件触觉与语音反馈",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TTS 语音播报输出", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("任务完成时自动语音播报结果", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = enableTts,
                            onCheckedChange = { enableTts = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldTertiary,
                                checkedTrackColor = EmeraldTertiary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    HorizontalDivider(
                        color = ObsidianCardBorder,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("触觉震动马达反馈", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("执行动作指令时触发物理轻微震动", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = enableVibrate,
                            onCheckedChange = { enableVibrate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldTertiary,
                                checkedTrackColor = EmeraldTertiary.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }

            // 8. Backup & Export
            item {
                CyberCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = "备份", tint = BlueInfo)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "配置备份与全量导出",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "将所有自动化脚本、插件配置及系统参数一键打包为 JSON 格式备份到剪贴板，方便跨设备迁移。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val jsonExport = viewModel.exportConfigurationJson()
                            viewModel.deviceController.setClipboard(jsonExport)
                            viewModel.deviceController.showToast("已将完整 JSON 配置备份复制到剪贴板")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("一键复制完整 JSON 备份到剪贴板", color = CyanPrimary)
                    }
                }
            }

            // 9. Version Info
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Hermes Android 智能体操作系统", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("v1.2.0-RELEASE • 多模型接入 • 流式输出 • 深度思考折叠 • 24/7 守护", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}
