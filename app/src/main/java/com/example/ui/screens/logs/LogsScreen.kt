package com.example.ui.screens.logs

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.ExecutionLog
import com.example.engine.ExecutionStep
import com.example.ui.HermesViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StepTimelineItem
import com.example.ui.theme.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    viewModel: HermesViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var filterStatus by remember { mutableStateOf("ALL") }

    val filteredLogs = logs.filter { log ->
        when (filterStatus) {
            "成功" -> log.status == "SUCCESS"
            "失败" -> log.status != "SUCCESS"
            else -> true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp)
    ) {
        // Header & Clear Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "运行审计与执行日志",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "已完整记录 ${logs.size} 条操作流水",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (logs.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearLogs() },
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空日志",
                        tint = RedError
                    )
                }
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("全部", "成功", "失败").forEach { status ->
                val isSelected = (filterStatus == "ALL" && status == "全部") || filterStatus == status
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { filterStatus = if (status == "全部") "ALL" else status },
                    color = if (isSelected) CyanPrimaryContainer else ObsidianSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) CyanPrimary else ObsidianCardBorder
                    )
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) CyanOnPrimaryContainer else TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Logs List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "暂无日志",
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无执行日志",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "当 Hermes 执行自动化任务或 AI 指令时会自动在此留痕。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    LogCard(
                        log = log,
                        onClick = { viewModel.openLogDetail(log) }
                    )
                }
            }
        }
    }

    // Detail Inspection Modal
    if (uiState.showLogDetail && uiState.selectedLogForDetail != null) {
        LogDetailDialog(
            log = uiState.selectedLogForDetail!!,
            onDismiss = { viewModel.closeLogDetail() }
        )
    }
}

@Composable
fun LogCard(
    log: ExecutionLog,
    onClick: () -> Unit
) {
    val dateStr = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    val isSuccess = log.status == "SUCCESS"

    CyberCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        borderColor = if (isSuccess) ObsidianCardBorder else RedError.copy(alpha = 0.5f)
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
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = log.status,
                    tint = if (isSuccess) EmeraldTertiary else RedError,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.prompt,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
            }

            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = log.outputResult,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(
                    text = if (log.isAiDriven) "AI Agent" else log.sourceName,
                    color = if (log.isAiDriven) CyanPrimary else PurpleSecondary
                )
                StatusBadge(
                    text = "${log.durationMs}ms",
                    color = TextMuted
                )
            }

            StatusBadge(
                text = log.status,
                color = if (isSuccess) EmeraldTertiary else RedError
            )
        }
    }
}

@Composable
fun LogDetailDialog(
    log: ExecutionLog,
    onDismiss: () -> Unit
) {
    val dateStr = remember(log.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    val parsedSteps = remember(log.stepsJson) {
        val list = mutableListOf<ExecutionStep>()
        try {
            val arr = JSONArray(log.stepsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ExecutionStep(
                        title = obj.getString("title"),
                        detail = obj.getString("detail"),
                        status = obj.optString("status", "SUCCESS")
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        list
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "执行审计与链路详情",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "会话 ID: ${log.sessionId} • $dateStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(text = "目标指令 / 用户提示词:", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                        Text(text = log.prompt, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }

                    item {
                        Text(text = "智能体规划策略 / Plan:", style = MaterialTheme.typography.labelSmall, color = PurpleSecondary)
                        Text(text = log.planSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }

                    if (parsedSteps.isNotEmpty()) {
                        item {
                            Text(text = "分步动作执行追踪链路:", style = MaterialTheme.typography.labelSmall, color = EmeraldTertiary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                parsedSteps.forEachIndexed { index, step ->
                                    StepTimelineItem(step = step, isLast = index == parsedSteps.size - 1)
                                }
                            }
                        }
                    }

                    item {
                        Text(text = "最终执行结果输出:", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                        Surface(
                            color = ObsidianBg,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = log.outputResult,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭", color = CyanOnPrimary)
                }
            }
        }
    }
}
