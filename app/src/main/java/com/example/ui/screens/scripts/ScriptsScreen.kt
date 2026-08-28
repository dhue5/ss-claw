package com.example.ui.screens.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.AutomationScript
import com.example.ui.HermesViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun ScriptsScreen(
    viewModel: HermesViewModel,
    modifier: Modifier = Modifier
) {
    val scripts by viewModel.scripts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTrigger by remember { mutableStateOf("ALL") }

    val triggers = listOf("全部", "MANUAL", "CLIPBOARD_CHANGE", "BATTERY_LOW", "API_WEBHOOK")

    val filteredScripts = scripts.filter { script ->
        (selectedFilterTrigger == "全部" || script.triggerType.equals(selectedFilterTrigger, ignoreCase = true)) &&
        (searchQuery.isBlank() || script.name.contains(searchQuery, ignoreCase = true) || script.description.contains(searchQuery, ignoreCase = true) || script.tags.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp)
    ) {
        // Header & Create Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "自定义自动化脚本",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "已注册 ${scripts.size} 个自动化任务管道",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { viewModel.openScriptEditor(null) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("create_script_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建脚本",
                    tint = CyanOnPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("新建脚本", color = CyanOnPrimary)
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("按名称、标签或触发器搜索脚本...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索", tint = TextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除", tint = TextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianSurface,
                unfocusedContainerColor = ObsidianSurface,
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = ObsidianCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("script_search_field")
        )

        // Filter trigger chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(triggers) { trigger ->
                val isSelected = selectedFilterTrigger == trigger
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedFilterTrigger = trigger },
                    color = if (isSelected) CyanPrimaryContainer else ObsidianSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) CyanPrimary else ObsidianCardBorder
                    )
                ) {
                    Text(
                        text = trigger,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) CyanOnPrimaryContainer else TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Scripts List
        if (filteredScripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "No scripts",
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isEmpty()) "未找到任何脚本" else "未匹配到相关脚本",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "编写自定义 JavaScript / DSL 脚本或点击右上角 '+ 新建脚本' 开始。",
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredScripts, key = { it.id }) { script ->
                    ScriptCard(
                        script = script,
                        onRun = { viewModel.runScript(script) },
                        onEdit = { viewModel.openScriptEditor(script) },
                        onToggle = { viewModel.toggleScriptEnabled(script) }
                    )
                }
            }
        }
    }

    // Full-screen / Modal Script Editor
    if (uiState.showScriptEditor && uiState.editingScript != null) {
        ScriptEditorDialog(
            script = uiState.editingScript!!,
            logs = uiState.scriptEditorLogs,
            output = uiState.scriptEditorOutput,
            isRunning = uiState.isRunningEditorScript,
            onSave = { updated -> viewModel.saveScript(updated) },
            onTestRun = { code -> viewModel.testRunEditorScript(code) },
            onDelete = { id -> viewModel.deleteScript(id) },
            onDismiss = { viewModel.closeScriptEditor() }
        )
    }
}

@Composable
fun ScriptCard(
    script: AutomationScript,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (script.isEnabled) ObsidianCardBorder else Color(0xFF1E2638)
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
                Surface(
                    color = when (script.triggerType) {
                        "BATTERY_LOW" -> AmberWarning.copy(alpha = 0.2f)
                        "CLIPBOARD_CHANGE" -> PurpleSecondary.copy(alpha = 0.2f)
                        "API_WEBHOOK" -> BlueInfo.copy(alpha = 0.2f)
                        else -> CyanPrimary.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (script.triggerType) {
                                "BATTERY_LOW" -> Icons.Default.BatteryAlert
                                "CLIPBOARD_CHANGE" -> Icons.Default.ContentPaste
                                "API_WEBHOOK" -> Icons.Default.Http
                                else -> Icons.Default.Terminal
                            },
                            contentDescription = script.triggerType,
                            tint = when (script.triggerType) {
                                "BATTERY_LOW" -> AmberWarning
                                "CLIPBOARD_CHANGE" -> PurpleSecondary
                                "API_WEBHOOK" -> BlueInfo
                                else -> CyanPrimary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = script.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
            }

            Switch(
                checked = script.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyanPrimary,
                    checkedTrackColor = CyanPrimaryContainer
                ),
                modifier = Modifier.testTag("toggle_script_${script.id}")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Info pills + Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(text = script.triggerType, color = CyanPrimary)
                StatusBadge(text = "已执行 ${script.executionCount} 次", color = TextMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("edit_script_${script.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("run_script_${script.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "运行", tint = CyanOnPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("运行", color = CyanOnPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ScriptEditorDialog(
    script: AutomationScript,
    logs: List<String>,
    output: String,
    isRunning: Boolean,
    onSave: (AutomationScript) -> Unit,
    onTestRun: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(script.name) }
    var description by remember { mutableStateOf(script.description) }
    var triggerType by remember { mutableStateOf(script.triggerType) }
    var scriptCode by remember { mutableStateOf(script.scriptCode) }
    var tags by remember { mutableStateOf(script.tags) }

    val snippets = listOf(
        "device.toast(\"...\")" to "device.toast(\"Hello from Hermes!\");\n",
        "device.vibrate(300)" to "device.vibrate(300);\n",
        "device.speak(\"...\")" to "device.speak(\"Automation complete\");\n",
        "device.getDeviceStats()" to "const stats = device.getDeviceStats();\n",
        "device.notify(\"Title\",\"Msg\")" to "device.notify(\"Task Complete\", \"Details here\");\n",
        "device.httpGet(url)" to "const res = device.httpGet(\"https://httpbin.org/get\");\n"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = "Editor", tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (script.id == 0L) "新建自动化脚本" else "编辑脚本配置",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }

                    Row {
                        if (script.id != 0L) {
                            IconButton(onClick = { onDelete(script.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = RedError)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Name & Trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("脚本名称") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = ObsidianCardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_script_name")
                    )

                    OutlinedTextField(
                        value = triggerType,
                        onValueChange = { triggerType = it },
                        label = { Text("触发器类型") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PurpleSecondary,
                            unfocusedBorderColor = ObsidianCardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_script_trigger")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述说明") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = ObsidianCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Preset Snippets Insert Row
                Text(
                    text = "快速插入 DSL 语法片段:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(snippets) { (label, codeSnippet) ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { scriptCode += "\n$codeSnippet" },
                            color = ObsidianSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
                        ) {
                            Text(
                                text = "+ $label",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Code Editor Area
                Text(
                    text = "Hermes Script Engine Code (JavaScript / DSL):",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary
                )
                OutlinedTextField(
                    value = scriptCode,
                    onValueChange = { scriptCode = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianBg,
                        unfocusedContainerColor = ObsidianBg,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedTextColor = CyanOnPrimaryContainer,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("editor_code_field")
                )

                // Debug Output & Log Terminal Box
                if (logs.isNotEmpty() || output.isNotEmpty() || isRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            if (isRunning) {
                                item {
                                    Text(
                                        text = "> Running script pipeline...",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = AmberWarning
                                    )
                                }
                            }
                            items(logs) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (line.contains("ERROR")) RedError else EmeraldTertiary
                                )
                            }
                            if (output.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "=> Result: $output",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyanPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onTestRun(scriptCode) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("test_run_script_button")
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = "测试运行", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("测试运行")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("取消", color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                onSave(
                                    script.copy(
                                        name = name.ifBlank { "未命名脚本" },
                                        description = description,
                                        triggerType = triggerType.ifBlank { "MANUAL" },
                                        scriptCode = scriptCode,
                                        tags = tags
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_script_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "保存", tint = CyanOnPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("保存脚本", color = CyanOnPrimary)
                        }
                    }
                }
            }
        }
    }
}
