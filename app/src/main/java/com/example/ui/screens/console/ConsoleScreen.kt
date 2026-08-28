package com.example.ui.screens.console

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.AgentState
import com.example.ui.ChatMessage
import com.example.ui.HermesViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StepTimelineItem
import com.example.ui.components.TelemetryHeaderBar
import com.example.ui.theme.*

@Composable
fun ConsoleScreen(
    viewModel: HermesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(uiState.chatMessages.size, uiState.currentSteps.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size)
        }
    }

    val isDaemonRunning by viewModel.isDaemonRunning.collectAsState()
    val isListeningVoice by viewModel.isListeningVoice.collectAsState()
    val recognizedVoiceText by viewModel.recognizedVoiceText.collectAsState()

    val quickActionSuggestions = listOf(
        "📊 查询设备电量与健康状态",
        "📋 读取并分析系统剪贴板",
        "👁️ 深度扫描屏幕 UI 节点树",
        "🔊 播报设备状态语音简报",
        "🛡️ 测试 24/7 后台哨兵守护",
        "⚡ 发送测试系统通知"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp)
    ) {
        // Device Telemetry Bar with Live 24/7 Daemon Status
        TelemetryHeaderBar(
            stats = uiState.deviceStats,
            isDaemonRunning = isDaemonRunning,
            onRefresh = { viewModel.refreshDeviceStats() },
            onToggleDaemon = { viewModel.toggleBackgroundDaemon() },
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        // Active Execution Monitor Card (Visible when processing or thinking)
        AnimatedVisibility(
            visible = uiState.isProcessing || uiState.currentSteps.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CyberCard(
                modifier = Modifier.padding(bottom = 8.dp),
                borderColor = CyanPrimary.copy(alpha = 0.5f),
                backgroundColor = ObsidianSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CyanPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (val state = uiState.agentState) {
                                is AgentState.Thinking -> "思考规划指令中..."
                                is AgentState.Executing -> "正在执行动作 [${state.toolName}]..."
                                else -> "正在处理自动化管道..."
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = CyanPrimary
                        )
                    }
                    StatusBadge(text = "运行中", color = EmeraldTertiary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Steps Trace
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    uiState.currentSteps.takeLast(4).forEachIndexed { index, step ->
                        StepTimelineItem(
                            step = step,
                            isLast = index == uiState.currentSteps.takeLast(4).size - 1
                        )
                    }
                }
            }
        }

        // Conversation / Command History
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(uiState.chatMessages, key = { it.id }) { msg ->
                ChatMessageItem(message = msg)
            }
        }

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickActionSuggestions) { suggestion ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            val cleanPrompt = suggestion.substringAfter(" ")
                            viewModel.submitPrompt(cleanPrompt)
                        },
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Prompt Input Field & Submit Action Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = ObsidianSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val clip = viewModel.deviceController.readClipboard()
                        if (clip.isNotBlank()) {
                            inputPrompt = "分析剪贴板内容: $clip"
                        } else {
                            viewModel.deviceController.showToast("剪贴板当前为空")
                        }
                    },
                    modifier = Modifier.testTag("paste_clipboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "读取剪贴板",
                        tint = PurpleSecondary
                    )
                }

                // Voice Mic Button
                IconButton(
                    onClick = {
                        if (isListeningVoice) {
                            viewModel.stopVoiceInput()
                        } else {
                            viewModel.startVoiceInput()
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isListeningVoice) RedError.copy(alpha = 0.2f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = if (isListeningVoice) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "语音指令",
                        tint = if (isListeningVoice) RedError else EmeraldTertiary
                    )
                }

                TextField(
                    value = if (isListeningVoice && !recognizedVoiceText.isNullOrBlank()) (recognizedVoiceText ?: "") else inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = if (isListeningVoice) "正在倾听语音指令..." else "下达指令 (例如: '检查电池', '扫描屏幕', '播报状态')...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isListeningVoice) RedError else TextMuted
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_input_field"),
                    singleLine = false,
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        if (inputPrompt.isNotBlank() && !uiState.isProcessing) {
                            val promptToSend = inputPrompt
                            inputPrompt = ""
                            viewModel.submitPrompt(promptToSend)
                        }
                    },
                    enabled = inputPrompt.isNotBlank() && !uiState.isProcessing,
                    modifier = Modifier
                        .testTag("submit_prompt_button")
                        .clip(CircleShape)
                        .background(if (inputPrompt.isNotBlank()) CyanPrimary else ObsidianSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Execute Command",
                        tint = if (inputPrompt.isNotBlank()) CyanOnPrimary else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    var expandedSteps by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        if (message.isUser) {
            Surface(
                color = CyanPrimaryContainer,
                shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanOnPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = ObsidianCardBorder,
                backgroundColor = ObsidianSurface
            ) {
                // Header badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Hermes",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hermes Agent",
                            style = MaterialTheme.typography.titleSmall,
                            color = CyanPrimary
                        )
                    }

                    if (message.toolCall != null) {
                        StatusBadge(
                            text = "TOOL: ${message.toolCall}",
                            color = PurpleSecondary
                        )
                    }
                }

                // Internal Thought / Reasoning
                if (!message.thought.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Thought",
                                tint = PurpleSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.thought,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Result / Response Output
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                // Optional Steps Toggle
                if (message.steps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { expandedSteps = !expandedSteps },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (expandedSteps) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle steps",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (expandedSteps) "收起执行动作链路 (${message.steps.size})" else "查看执行动作链路 (${message.steps.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    if (expandedSteps) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            message.steps.forEachIndexed { idx, s ->
                                StepTimelineItem(step = s, isLast = idx == message.steps.size - 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
