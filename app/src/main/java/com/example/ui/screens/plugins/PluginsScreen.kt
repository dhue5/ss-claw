package com.example.ui.screens.plugins

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
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.PluginExtension
import com.example.engine.MarketplacePlugin
import com.example.ui.HermesViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PluginsScreen(
    viewModel: HermesViewModel,
    modifier: Modifier = Modifier
) {
    val plugins by viewModel.plugins.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Installed, 1: Marketplace Hub

    val marketplaceCatalog = remember { viewModel.pluginManager.getMarketplaceCatalog() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "插件扩展中心",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "通过模块化工具与第三方 API 增强 Hermes 手机能力",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { viewModel.openPluginDetail(null) },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleSecondary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("create_custom_plugin_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建插件", tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("新建插件", color = Color.White)
            }
        }

        // Tab Selector (Installed vs Marketplace)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianSurface,
            contentColor = CyanPrimary,
            divider = { HorizontalDivider(color = ObsidianCardBorder) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("已安装 (${plugins.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("官方插件市场 (${marketplaceCatalog.size})") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content
        if (selectedTab == 0) {
            // Installed Plugins
            if (plugins.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "No plugins",
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无已安装插件",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "前往官方插件市场一键安装所需扩展能力。",
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
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(plugins, key = { it.id }) { plugin ->
                        // Check if an upgrade exists in marketplace
                        val marketMatch = marketplaceCatalog.firstOrNull { it.packageId == plugin.packageId }
                        val hasUpdate = marketMatch != null && marketMatch.version != plugin.version

                        InstalledPluginCard(
                            plugin = plugin,
                            hasUpdate = hasUpdate,
                            onToggle = { viewModel.togglePluginEnabled(plugin) },
                            onEdit = { viewModel.openPluginDetail(plugin) },
                            onUpgrade = {
                                if (marketMatch != null) {
                                    viewModel.installMarketplacePlugin(marketMatch)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Marketplace Hub
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(marketplaceCatalog) { marketPlugin ->
                    val installedVersion = plugins.firstOrNull { it.packageId == marketPlugin.packageId }?.version
                    val isInstalled = installedVersion != null
                    val hasUpdate = isInstalled && installedVersion != marketPlugin.version

                    MarketplacePluginCard(
                        plugin = marketPlugin,
                        isInstalled = isInstalled,
                        installedVersion = installedVersion,
                        hasUpdate = hasUpdate,
                        onInstallOrUpgrade = {
                            viewModel.installMarketplacePlugin(marketPlugin)
                        }
                    )
                }
            }
        }
    }

    // Plugin Config / Detail Modal
    if (uiState.showPluginDetail && uiState.editingPlugin != null) {
        PluginConfigDialog(
            plugin = uiState.editingPlugin!!,
            onSave = { updated -> viewModel.savePlugin(updated) },
            onDelete = { id -> viewModel.deletePlugin(id) },
            onDismiss = { viewModel.closePluginDetail() }
        )
    }
}

@Composable
fun InstalledPluginCard(
    plugin: PluginExtension,
    hasUpdate: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onUpgrade: () -> Unit
) {
    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (hasUpdate) AmberWarning else ObsidianCardBorder
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
                    color = PurpleSecondary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (plugin.iconName) {
                                "send" -> Icons.Default.Send
                                "home" -> Icons.Default.Home
                                "campaign" -> Icons.Default.Campaign
                                "code" -> Icons.Default.Code
                                else -> Icons.Default.Extension
                            },
                            contentDescription = plugin.name,
                            tint = PurpleSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusBadge(text = "v${plugin.version}", color = CyanPrimary)
                    }
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
            }

            Switch(
                checked = plugin.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PurpleSecondary,
                    checkedTrackColor = PurpleSecondaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tools declared
        val toolCount = try {
            JSONArray(plugin.actionsJson).length()
        } catch (e: Exception) {
            0
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(text = "$toolCount 个 AI 工具", color = EmeraldTertiary)
                StatusBadge(text = plugin.permissions, color = TextMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasUpdate) {
                    Button(
                        onClick = onUpgrade,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Upgrade, contentDescription = "升级", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("升级", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "配置", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("配置", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun MarketplacePluginCard(
    plugin: MarketplacePlugin,
    isInstalled: Boolean,
    installedVersion: String?,
    hasUpdate: Boolean,
    onInstallOrUpgrade: () -> Unit
) {
    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (hasUpdate) AmberWarning else ObsidianCardBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f)) {
                Surface(
                    color = CyanPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (plugin.iconName) {
                                "send" -> Icons.Default.Send
                                "home" -> Icons.Default.Home
                                "campaign" -> Icons.Default.Campaign
                                "code" -> Icons.Default.Code
                                "language" -> Icons.Default.Language
                                else -> Icons.Default.Extension
                            },
                            contentDescription = plugin.name,
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusBadge(text = "v${plugin.version}", color = CyanPrimary)
                    }

                    Text(
                        text = "作者: ${plugin.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "更新日志: ${plugin.changelog}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldTertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusBadge(text = "权限: ${plugin.permissions}", color = TextSecondary)

            if (!isInstalled) {
                Button(
                    onClick = onInstallOrUpgrade,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "安装", tint = CyanOnPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("一键安装", color = CyanOnPrimary, style = MaterialTheme.typography.labelSmall)
                }
            } else if (hasUpdate) {
                Button(
                    onClick = onInstallOrUpgrade,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Upgrade, contentDescription = "更新", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("升级至 v${plugin.version}", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Surface(
                    color = EmeraldTertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldTertiary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "已安装", tint = EmeraldTertiary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("已安装", color = EmeraldTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PluginConfigDialog(
    plugin: PluginExtension,
    onSave: (PluginExtension) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(plugin.name) }
    var packageId by remember { mutableStateOf(plugin.packageId) }
    var version by remember { mutableStateOf(plugin.version) }
    var description by remember { mutableStateOf(plugin.description) }
    var configJson by remember { mutableStateOf(plugin.configJson) }
    var actionsJson by remember { mutableStateOf(plugin.actionsJson) }
    var sourceCode by remember { mutableStateOf(plugin.sourceCode) }

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SettingsSuggest, contentDescription = "Plugin Config", tint = PurpleSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (plugin.id == 0L) "新建插件扩展" else "配置插件：${plugin.name}",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }

                    Row {
                        if (plugin.id != 0L) {
                            IconButton(onClick = { onDelete(plugin.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = RedError)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("插件名称") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PurpleSecondary,
                            unfocusedBorderColor = ObsidianCardBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = version,
                        onValueChange = { version = it },
                        label = { Text("版本号") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = ObsidianCardBorder
                        ),
                        modifier = Modifier.width(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("功能描述") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PurpleSecondary,
                        unfocusedBorderColor = ObsidianCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Config JSON
                Text("插件专属配置 / API 密钥凭证 (JSON 格式):", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                OutlinedTextField(
                    value = configJson,
                    onValueChange = { configJson = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
                        .height(90.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Plugin Execution Logic / Source Code
                Text("插件动作逻辑 (JavaScript / DSL 脚本):", style = MaterialTheme.typography.labelSmall, color = PurpleSecondary)
                OutlinedTextField(
                    value = sourceCode,
                    onValueChange = { sourceCode = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
                        .weight(1f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                plugin.copy(
                                    name = name.ifBlank { "自定义插件" },
                                    packageId = packageId.ifBlank { "com.custom.plugin" },
                                    version = version.ifBlank { "1.0.0" },
                                    description = description,
                                    configJson = configJson,
                                    actionsJson = actionsJson,
                                    sourceCode = sourceCode
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleSecondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存插件", color = Color.White)
                    }
                }
            }
        }
    }
}
