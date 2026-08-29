package com.example.ui.screens.console

import android.widget.Toast
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.engine.AgentState
import com.example.ui.ChatMessage
import com.example.ui.HermesViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    viewModel: HermesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Auto-scroll to bottom when new messages arrive, steps update, or when keyboard appears
    LaunchedEffect(uiState.chatMessages.size, uiState.currentSteps.size, isImeVisible) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size - 1)
        }
    }

    // Also auto-scroll on latest streaming text changes
    val lastMessageText = uiState.chatMessages.lastOrNull()?.text
    LaunchedEffect(lastMessageText) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.scrollToItem(uiState.chatMessages.size - 1)
        }
    }

    val isDaemonRunning by viewModel.isDaemonRunning.collectAsState()
    val isListeningVoice by viewModel.isListeningVoice.collectAsState()
    val recognizedVoiceText by viewModel.recognizedVoiceText.collectAsState()
    val voiceSoundLevel by viewModel.voiceSoundLevel.collectAsState()
    val isSpeakingTts by viewModel.isSpeakingTts.collectAsState()
    val speakingMessageId by viewModel.speakingMessageId.collectAsState()

    val dynamicSuggestions = remember(uiState.deviceStats, uiState.chatMessages.size) {
        viewModel.getDynamicSuggestions()
    }

    var isMonitorExpanded by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空对话历史", color = TextPrimary) },
            text = { Text("确定要重置当前对话与上下文记忆吗？已保存的自动化日志不受影响。", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "会话历史已清空", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确认清空", color = RedError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Minimal Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isProcessing) CyanPrimary else EmeraldTertiary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isProcessing) "智能体执行中..." else "神经中枢就绪",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.isProcessing) CyanPrimary else TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSpeakingTts) {
                    TextButton(
                        onClick = { viewModel.stopSpeaking() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Stop Speech",
                            tint = RedError,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止播报", style = MaterialTheme.typography.labelSmall, color = RedError)
                    }
                }

                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Chat",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Active Execution Monitor Card (Compact & Collapsible)
        AnimatedVisibility(
            visible = uiState.isProcessing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CyberCard(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 4.dp)
                    .clickable { isMonitorExpanded = !isMonitorExpanded },
                borderColor = CyanPrimary.copy(alpha = 0.4f),
                backgroundColor = ObsidianSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.5.dp,
                            color = CyanPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (val state = uiState.agentState) {
                                is AgentState.Thinking -> "思考规划中..."
                                is AgentState.Executing -> "执行 [${state.toolName}]"
                                else -> "处理任务流水线..."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanPrimary,
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(
                            text = if (uiState.currentSteps.isNotEmpty()) "${uiState.currentSteps.size} 步" else "运行中",
                            color = EmeraldTertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isMonitorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isMonitorExpanded) "收起" else "展开",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Collapsible Steps Trace (Only shown when user taps to expand)
                AnimatedVisibility(
                    visible = isMonitorExpanded && uiState.currentSteps.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uiState.currentSteps.takeLast(3).forEachIndexed { index, step ->
                            StepTimelineItem(
                                step = step,
                                isLast = index == uiState.currentSteps.takeLast(3).size - 1
                            )
                        }
                    }
                }
            }
        }

        // Conversation / Command History
        if (uiState.chatMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "No Chat",
                        tint = CyanPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Hermes 对话与任务执行流",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "在下方输入自然语言或点击情景感知快捷指令，即可调度智能体执行操作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
            ) {
                items(uiState.chatMessages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        isSpeaking = speakingMessageId == msg.id,
                        onToggleSpeak = { viewModel.toggleSpeakMessage(msg.id, msg.text) },
                        onRegenerate = { viewModel.regenerateLastPrompt() }
                    )
                }
            }
        }

        // Dynamic Context-Aware Suggestion Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dynamicSuggestions) { suggestion ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            keyboardController?.hide()
                            focusManager.clearFocus()
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

        // Voice Waveform & Partial Preview Overlay when listening
        AnimatedVisibility(
            visible = isListeningVoice,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldTertiary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AudioWaveformVisualizer(
                        soundLevel = voiceSoundLevel,
                        isListening = isListeningVoice
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (!recognizedVoiceText.isNullOrBlank()) recognizedVoiceText!! else "正在倾听语音指令...",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldTertiary
                    )
                }
            }
        }

        // Prompt Input Field & Submit Action Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            keyboardController?.hide()
                            focusManager.clearFocus()
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
fun ChatMessageItem(
    message: ChatMessage,
    isSpeaking: Boolean = false,
    onToggleSpeak: () -> Unit = {},
    onRegenerate: () -> Unit = {}
) {
    var expandedSteps by remember { mutableStateOf(false) }
    var expandedThought by remember { mutableStateOf(message.isStreaming) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

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
                borderColor = if (message.isStreaming) CyanPrimary.copy(alpha = 0.6f) else ObsidianCardBorder,
                backgroundColor = ObsidianSurface
            ) {
                // Header badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.isStreaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyanPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Hermes",
                                tint = CyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (message.isStreaming) "Hermes Agent (生成中...)" else "Hermes Agent",
                            style = MaterialTheme.typography.titleSmall,
                            color = CyanPrimary
                        )
                    }

                    if (message.toolCall != null) {
                        StatusBadge(
                            text = "TOOL: ${message.toolCall}",
                            color = PurpleSecondary
                        )
                    } else if (message.isStreaming) {
                        StatusBadge(
                            text = "STREAMING",
                            color = EmeraldTertiary
                        )
                    }
                }

                // Progressive Deep Thinking Collapsible (CoT)
                if (!message.thought.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedThought = !expandedThought }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "Thought",
                                        tint = PurpleSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "深度推理思考链",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PurpleSecondary
                                    )
                                }
                                Icon(
                                    imageVector = if (expandedThought) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle thought",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = expandedThought || message.isStreaming,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Text(
                                    text = message.thought,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Rich Tool Metrics Visualization (Battery/Storage etc if mentioned)
                if (message.text.contains("电量") || message.text.contains("Battery") || message.toolCall == "get_device_stats") {
                    val batteryLevelMatch = Regex("""(\d{1,3})%""").find(message.text)?.groupValues?.get(1)?.toIntOrNull() ?: 85
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            MiniMetricBar(
                                label = "实时电量健康度",
                                valueText = "$batteryLevelMatch%",
                                percentage = batteryLevelMatch / 100f,
                                color = if (batteryLevelMatch <= 20) AmberWarning else CyanPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Result / Response Output with typing cursor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (message.isStreaming) {
                        Text(
                            text = " ▍",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyanPrimary
                        )
                    }
                }

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

                    if (expandedSteps || message.isStreaming) {
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

                // Message Bottom Action Bar (Copy, TTS Speak, Regenerate)
                if (!message.isStreaming) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Copy Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(AnnotatedString(message.text))
                                Toast.makeText(context, "已复制回答内容", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制内容",
                                tint = TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Text-To-Speech (TTS) Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleSpeak()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "语音播报",
                                tint = if (isSpeaking) EmeraldTertiary else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Regenerate Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRegenerate()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重新生成",
                                tint = TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

