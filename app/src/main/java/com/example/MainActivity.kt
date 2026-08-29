package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.HermesViewModel
import com.example.ui.screens.console.ConsoleScreen
import com.example.ui.screens.logs.LogsScreen
import com.example.ui.screens.plugins.PluginsScreen
import com.example.ui.screens.scripts.ScriptsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: HermesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermesTheme {
                HermesApp(viewModel = viewModel)
            }
        }
    }
}

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesApp(viewModel: HermesViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val navItems = listOf(
        NavItem("控制台", Icons.Default.Terminal, "tab_console"),
        NavItem("脚本引擎", Icons.Default.Code, "tab_scripts"),
        NavItem("插件扩展", Icons.Default.Extension, "tab_plugins"),
        NavItem("审计日志", Icons.Default.History, "tab_logs"),
        NavItem("系统设置", Icons.Default.Settings, "tab_settings")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CyanPrimary.copy(alpha = 0.2f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Hermes Logo",
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "HERMES",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "AUTONOMOUS LOCAL AGENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshDeviceStats() },
                        modifier = Modifier.testTag("refresh_status_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                contentColor = CyanPrimary,
                tonalElevation = 0.dp
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = uiState.selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(index) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanOnPrimaryContainer,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimaryContainer,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .background(ObsidianBg)
        ) {
            when (uiState.selectedTab) {
                0 -> ConsoleScreen(viewModel = viewModel)
                1 -> ScriptsScreen(viewModel = viewModel)
                2 -> PluginsScreen(viewModel = viewModel)
                3 -> LogsScreen(viewModel = viewModel)
                4 -> SettingsScreen(viewModel = viewModel)
                else -> ConsoleScreen(viewModel = viewModel)
            }
        }
    }
}

