package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.HermesApplication
import com.example.data.local.ApiConfig
import com.example.data.local.AutomationScript
import com.example.data.local.ExecutionLog
import com.example.data.local.PluginExtension
import com.example.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val thought: String? = null,
    val toolCall: String? = null,
    val steps: List<ExecutionStep> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class UiState(
    val isProcessing: Boolean = false,
    val agentState: AgentState = AgentState.Idle,
    val currentSteps: List<ExecutionStep> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val deviceStats: DeviceStats? = null,
    val isTtsActive: Boolean = false,
    val selectedTab: Int = 0, // 0: Console, 1: Scripts, 2: Plugins, 3: Logs, 4: Settings
    val searchQuery: String = "",
    val editingScript: AutomationScript? = null,
    val editingPlugin: PluginExtension? = null,
    val selectedLogForDetail: ExecutionLog? = null,
    val showScriptEditor: Boolean = false,
    val showPluginDetail: Boolean = false,
    val showLogDetail: Boolean = false,
    val scriptEditorLogs: List<String> = emptyList(),
    val scriptEditorOutput: String = "",
    val isRunningEditorScript: Boolean = false,
    val editingModel: com.example.data.local.AiModelEndpoint? = null,
    val showModelEditor: Boolean = false,
    val showHotUpdateDialog: Boolean = false,
    val isCheckingHotUpdate: Boolean = false,
    val hotUpdateStatusMessage: String? = null,
    val testingModelId: Long? = null
)

class HermesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HermesApplication
    val repository = app.repository

    val deviceController = HermesDeviceController(application)
    val scriptEngine = HermesScriptEngine(deviceController)
    val pluginManager = HermesPluginManager(repository, scriptEngine, deviceController)
    val agent = HermesAgent(repository, deviceController, scriptEngine, pluginManager)
    val hotUpdateManager = HermesHotUpdateManager(repository, scriptEngine, pluginManager)
    private val geminiApi = com.example.data.remote.GeminiApi()

    private val _uiState = MutableStateFlow(
        UiState(
            chatMessages = listOf(
                ChatMessage(
                    isUser = false,
                    text = "Hermes 自动化智能体操作系统已就绪。已接入多大模型驱动与动态热更新引擎，支持执行手机系统动作、调度自定义自动化流水线及运行扩展插件。请问有什么目标需要达成？",
                    thought = "智能神经中枢已初始化，支持自由接入与热切换外部 API 及大模型。"
                )
            )
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Room DB Streams
    val models: StateFlow<List<com.example.data.local.AiModelEndpoint>> = repository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeModelEndpoint: StateFlow<com.example.data.local.AiModelEndpoint?> = repository.activeModelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val scripts: StateFlow<List<AutomationScript>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plugins: StateFlow<List<PluginExtension>> = repository.allPlugins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<ExecutionLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<ApiConfig> = repository.configFlow
        .map { it ?: ApiConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig())

    // Live Background Daemon Streams
    val isDaemonRunning: StateFlow<Boolean> = HermesBackgroundService.isServiceRunning
    val daemonStartTime: StateFlow<Long> = HermesBackgroundService.serviceStartTime
    val daemonEventCount: StateFlow<Int> = HermesBackgroundService.daemonEventCount
    val lastDaemonEvent: StateFlow<String?> = HermesBackgroundService.lastDaemonEvent

    // Live Accessibility & Notification Streams
    val isAccessibilityConnected: StateFlow<Boolean> = HermesAccessibilityService.isAccessibilityConnected
    val currentActiveApp: StateFlow<String> = HermesAccessibilityService.currentActiveApp
    val isNotificationListenerConnected: StateFlow<Boolean> = HermesNotificationListener.isNotificationListenerConnected
    val lastInterceptedNotification: StateFlow<String?> = HermesNotificationListener.lastInterceptedNotification
    val interceptedNotificationCount: StateFlow<Int> = HermesNotificationListener.interceptedNotificationCount
    val isBubbleVisible: StateFlow<Boolean> = HermesFloatingService.isBubbleVisible

    // Voice Manager
    val voiceManager = HermesVoiceManager(app)
    val isListeningVoice: StateFlow<Boolean> = voiceManager.isListening
    val recognizedVoiceText: StateFlow<String?> = voiceManager.recognizedText

    init {
        refreshDeviceStats()
        // Periodic telemetry update
        viewModelScope.launch {
            while (true) {
                delay(10000)
                refreshDeviceStats()
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun refreshDeviceStats() {
        val stats = deviceController.getDeviceStats()
        _uiState.update { it.copy(deviceStats = stats) }
    }

    // --- AGENT GOAL EXECUTION ---
    fun submitPrompt(prompt: String) {
        if (prompt.isBlank() || _uiState.value.isProcessing) return

        val userMessage = ChatMessage(isUser = true, text = prompt)
        _uiState.update {
            it.copy(
                isProcessing = true,
                currentSteps = emptyList(),
                chatMessages = it.chatMessages + userMessage
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            val liveSteps = mutableListOf<ExecutionStep>()
            agent.executeGoal(
                prompt = prompt,
                onStateChanged = { state ->
                    _uiState.update { it.copy(agentState = state) }
                },
                onStepAdded = { step ->
                    liveSteps.add(step)
                    _uiState.update { it.copy(currentSteps = liveSteps.toList()) }
                }
            )

            // When finished, convert to Assistant Message in chat
            val finalState = _uiState.value.agentState
            val assistantMsg = when (finalState) {
                is AgentState.Completed -> ChatMessage(
                    isUser = false,
                    text = finalState.resultMessage,
                    thought = finalState.thought,
                    toolCall = finalState.toolCalled,
                    steps = liveSteps.toList()
                )
                is AgentState.Error -> ChatMessage(
                    isUser = false,
                    text = "Execution Error: ${finalState.errorMessage}",
                    thought = "Agent caught an exception during execution.",
                    steps = liveSteps.toList()
                )
                else -> ChatMessage(
                    isUser = false,
                    text = "Task cycle concluded.",
                    steps = liveSteps.toList()
                )
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    chatMessages = it.chatMessages + assistantMsg
                )
            }
            refreshDeviceStats()
        }
    }

    // --- SCRIPT OPERATIONS ---
    fun runScript(script: AutomationScript) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val res = scriptEngine.executeScript(script.scriptCode)
            repository.recordScriptExecution(script.id)

            val log = ExecutionLog(
                sessionId = java.util.UUID.randomUUID().toString().take(8),
                prompt = "Execute Script: ${script.name}",
                planSummary = "Direct manual script execution",
                status = if (res.success) "SUCCESS" else "FAILED",
                outputResult = res.output,
                durationMs = res.durationMs,
                isAiDriven = false,
                sourceName = "Script Engine"
            )
            repository.insertLog(log)

            deviceController.showToast("Script [${script.name}] finished")
            _uiState.update { it.copy(isProcessing = false) }
            refreshDeviceStats()
        }
    }

    fun toggleScriptEnabled(script: AutomationScript) {
        viewModelScope.launch {
            repository.toggleScript(script.id, !script.isEnabled)
        }
    }

    fun saveScript(script: AutomationScript) {
        viewModelScope.launch {
            if (script.id == 0L) {
                repository.insertScript(script)
            } else {
                repository.updateScript(script.copy(updatedAt = System.currentTimeMillis()))
            }
            _uiState.update { it.copy(showScriptEditor = false, editingScript = null) }
        }
    }

    fun deleteScript(scriptId: Long) {
        viewModelScope.launch {
            repository.deleteScript(scriptId)
            _uiState.update { it.copy(showScriptEditor = false, editingScript = null) }
        }
    }

    fun openScriptEditor(script: AutomationScript?) {
        _uiState.update {
            it.copy(
                editingScript = script ?: AutomationScript(
                    name = "New Automation Script",
                    description = "Custom automated action pipeline",
                    triggerType = "MANUAL",
                    scriptCode = """
                        // Write custom Hermes automation code here
                        const stats = device.getDeviceStats();
                        device.toast("Running script... Battery: " + stats.batteryLevel + "%");
                        device.vibrate(200);
                        return { status: "OK", battery: stats.batteryLevel };
                    """.trimIndent()
                ),
                showScriptEditor = true,
                scriptEditorLogs = emptyList(),
                scriptEditorOutput = ""
            )
        }
    }

    fun closeScriptEditor() {
        _uiState.update { it.copy(showScriptEditor = false, editingScript = null) }
    }

    fun testRunEditorScript(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningEditorScript = true) }
            val result = scriptEngine.executeScript(code)
            _uiState.update {
                it.copy(
                    isRunningEditorScript = false,
                    scriptEditorLogs = result.logs,
                    scriptEditorOutput = result.output
                )
            }
        }
    }

    // --- PLUGIN OPERATIONS ---
    fun togglePluginEnabled(plugin: PluginExtension) {
        viewModelScope.launch {
            repository.togglePlugin(plugin.id, !plugin.isEnabled)
        }
    }

    fun installMarketplacePlugin(marketPlugin: MarketplacePlugin) {
        viewModelScope.launch {
            pluginManager.installMarketplacePlugin(marketPlugin)
            deviceController.showToast("Installed / Updated plugin: ${marketPlugin.name}")
        }
    }

    fun savePlugin(plugin: PluginExtension) {
        viewModelScope.launch {
            if (plugin.id == 0L) {
                repository.insertPlugin(plugin)
            } else {
                repository.updatePlugin(plugin.copy(lastUpdatedAt = System.currentTimeMillis()))
            }
            _uiState.update { it.copy(showPluginDetail = false, editingPlugin = null) }
        }
    }

    fun deletePlugin(pluginId: Long) {
        viewModelScope.launch {
            repository.deletePlugin(pluginId)
            _uiState.update { it.copy(showPluginDetail = false, editingPlugin = null) }
        }
    }

    fun openPluginDetail(plugin: PluginExtension?) {
        _uiState.update {
            it.copy(
                editingPlugin = plugin ?: PluginExtension(
                    packageId = "com.custom.plugin.${System.currentTimeMillis() % 10000}",
                    name = "Custom Tool Plugin",
                    description = "Custom extension providing tools to Hermes",
                    author = "User",
                    configJson = """{"apiKey": ""}""",
                    actionsJson = """[{"name":"custom_tool","description":"My custom action","params":["arg1"]}]""",
                    sourceCode = """
                        // Custom Plugin Logic
                        device.toast("Custom plugin invoked: " + params.arg1);
                        return { success: true };
                    """.trimIndent()
                ),
                showPluginDetail = true
            )
        }
    }

    fun closePluginDetail() {
        _uiState.update { it.copy(showPluginDetail = false, editingPlugin = null) }
    }

    // --- LOG OPERATIONS ---
    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    fun openLogDetail(log: ExecutionLog) {
        _uiState.update { it.copy(selectedLogForDetail = log, showLogDetail = true) }
    }

    fun closeLogDetail() {
        _uiState.update { it.copy(showLogDetail = false, selectedLogForDetail = null) }
    }

    // --- SETTINGS & DAEMON CONFIG ---
    fun saveApiConfig(
        customApiKey: String,
        activeModel: String,
        systemPrompt: String,
        temperature: Float,
        enableTtsVoice: Boolean,
        enableVibrationFeedback: Boolean,
        enableBackgroundDaemon: Boolean = true,
        autoStartOnBoot: Boolean = true,
        enableClipboardMonitoring: Boolean = true,
        enableBatteryGuardMonitoring: Boolean = true,
        periodicCheckIntervalMinutes: Int = 15
    ) {
        viewModelScope.launch {
            val current = repository.getConfig()
            val updated = current.copy(
                customApiKey = customApiKey.trim(),
                activeModel = activeModel,
                systemPrompt = systemPrompt,
                temperature = temperature,
                enableTtsVoice = enableTtsVoice,
                enableVibrationFeedback = enableVibrationFeedback,
                enableBackgroundDaemon = enableBackgroundDaemon,
                autoStartOnBoot = autoStartOnBoot,
                enableClipboardMonitoring = enableClipboardMonitoring,
                enableBatteryGuardMonitoring = enableBatteryGuardMonitoring,
                periodicCheckIntervalMinutes = periodicCheckIntervalMinutes
            )
            repository.saveConfig(updated)

            // Adjust Background Service according to toggle
            if (enableBackgroundDaemon) {
                HermesBackgroundService.start(app)
            } else {
                HermesBackgroundService.stop(app)
            }

            deviceController.showToast("Settings & background configuration saved")
        }
    }

    fun toggleBackgroundDaemon() {
        viewModelScope.launch {
            val current = repository.getConfig()
            val newState = !current.enableBackgroundDaemon
            repository.saveConfig(current.copy(enableBackgroundDaemon = newState))
            if (newState) {
                HermesBackgroundService.start(app)
                deviceController.showToast("Hermes 24/7 Daemon Started")
            } else {
                HermesBackgroundService.stop(app)
                deviceController.showToast("Hermes Daemon Stopped")
            }
        }
    }

    fun isBatteryOptimizationWhitelisted(): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(app)
    }

    fun requestBatteryOptimizationExemption(context: android.content.Context) {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
    }

    fun isAccessibilityEnabled(): Boolean {
        return HermesAccessibilityService.isAccessibilityEnabled(app)
    }

    fun openAccessibilitySettings(context: android.content.Context) {
        HermesAccessibilityService.openAccessibilitySettings(context)
    }

    fun isNotificationAccessGranted(): Boolean {
        return HermesNotificationListener.isNotificationAccessGranted(app)
    }

    fun openNotificationAccessSettings(context: android.content.Context) {
        HermesNotificationListener.openNotificationAccessSettings(context)
    }

    fun canDrawOverlays(): Boolean {
        return FloatingOverlayHelper.canDrawOverlays(app)
    }

    fun requestOverlayPermission(context: android.content.Context) {
        FloatingOverlayHelper.requestOverlayPermission(context)
    }

    fun toggleFloatingBubble() {
        val currentlyVisible = isBubbleVisible.value
        deviceController.toggleFloatingBubble(!currentlyVisible)
    }

    fun startVoiceInput() {
        voiceManager.startListening { voiceText ->
            if (voiceText.isNotBlank()) {
                submitPrompt(voiceText)
            }
        }
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    fun triggerBackgroundBriefing() {
        viewModelScope.launch {
            val stats = deviceController.getDeviceStats()
            val text = "Hermes Daemon Status: Battery at ${stats.batteryLevel}%, Network is ${stats.networkType}. Background automation active."
            deviceController.showNotification("Hermes Status Briefing", text)
            deviceController.speak(text)
            deviceController.vibrate(200)
        }
    }

    // --- AI MODEL ENDPOINTS & HOT-SWITCHING ---
    fun openModelEditor(model: com.example.data.local.AiModelEndpoint? = null) {
        _uiState.update {
            it.copy(
                editingModel = model,
                showModelEditor = true
            )
        }
    }

    fun dismissModelEditor() {
        _uiState.update {
            it.copy(
                editingModel = null,
                showModelEditor = false
            )
        }
    }

    fun saveModelEndpoint(model: com.example.data.local.AiModelEndpoint) {
        viewModelScope.launch {
            if (model.id == 0L) {
                val newId = repository.insertModel(model)
                if (model.isActive) {
                    repository.setActiveModel(newId)
                }
            } else {
                repository.updateModel(model)
                if (model.isActive) {
                    repository.setActiveModel(model.id)
                }
            }
            dismissModelEditor()
            deviceController.showToast("已保存模型「${model.name}」配置")
        }
    }

    fun deleteModelEndpoint(id: Long) {
        viewModelScope.launch {
            repository.deleteModel(id)
            deviceController.showToast("已删除模型配置")
        }
    }

    fun setActiveModel(id: Long) {
        viewModelScope.launch {
            repository.setActiveModel(id)
            val model = repository.getModelById(id)
            if (model != null) {
                // Also sync config activeModel
                val curConfig = repository.getConfig()
                repository.saveConfig(curConfig.copy(activeModel = model.modelName))
                deviceController.showToast("已热切换至「${model.name}」")
            }
        }
    }

    fun testModelEndpoint(model: com.example.data.local.AiModelEndpoint) {
        _uiState.update { it.copy(testingModelId = model.id) }
        viewModelScope.launch {
            val (success, latency) = geminiApi.testEndpointConnectivity(model)
            val status = if (success) "SUCCESS" else "FAILED"
            repository.updateModelLatency(model.id, latency, status)
            _uiState.update { it.copy(testingModelId = null) }
            val toastMsg = if (success) {
                "「${model.name}」连接成功！响应延迟: ${latency}ms"
            } else {
                "「${model.name}」连接失败，请检查 Base URL 与 API 密钥"
            }
            deviceController.showToast(toastMsg)
        }
    }

    // --- HOT UPDATE & UPGRADE ENGINE ---
    fun openHotUpdateDialog() {
        _uiState.update {
            it.copy(
                showHotUpdateDialog = true,
                hotUpdateStatusMessage = null
            )
        }
    }

    fun dismissHotUpdateDialog() {
        _uiState.update {
            it.copy(
                showHotUpdateDialog = false,
                isCheckingHotUpdate = false
            )
        }
    }

    fun checkForHotUpdates() {
        _uiState.update { it.copy(isCheckingHotUpdate = true, hotUpdateStatusMessage = "正在检查并同步最新模型、脚本与引擎热补丁...") }
        viewModelScope.launch {
            val result = hotUpdateManager.checkForHotUpdates()
            _uiState.update {
                it.copy(
                    isCheckingHotUpdate = false,
                    hotUpdateStatusMessage = result.message + "\n新增模型: ${result.updatedModelsCount} 个 | 自动化脚本: ${result.updatedScriptsCount} 个"
                )
            }
            deviceController.showToast(result.message)
        }
    }

    fun applyHotUpdatePatch(jsonPatch: String) {
        _uiState.update { it.copy(isCheckingHotUpdate = true, hotUpdateStatusMessage = "正在安装自定义热更新补丁...") }
        viewModelScope.launch {
            val result = hotUpdateManager.applyCustomHotUpdateBundle(jsonPatch)
            _uiState.update {
                it.copy(
                    isCheckingHotUpdate = false,
                    hotUpdateStatusMessage = result.message + "\n生效模型: ${result.updatedModelsCount} | 脚本: ${result.updatedScriptsCount} | 插件: ${result.updatedPluginsCount}"
                )
            }
            deviceController.showToast(result.message)
        }
    }

    fun exportConfigurationJson(): String {
        val root = JSONObject()
        val scriptList = scripts.value
        val pluginList = plugins.value

        val scriptArr = JSONArray()
        scriptList.forEach { s ->
            scriptArr.put(JSONObject().apply {
                put("name", s.name)
                put("description", s.description)
                put("triggerType", s.triggerType)
                put("scriptCode", s.scriptCode)
                put("tags", s.tags)
            })
        }

        val pluginArr = JSONArray()
        pluginList.forEach { p ->
            pluginArr.put(JSONObject().apply {
                put("packageId", p.packageId)
                put("name", p.name)
                put("version", p.version)
                put("description", p.description)
                put("permissions", p.permissions)
                put("configJson", p.configJson)
                put("actionsJson", p.actionsJson)
                put("sourceCode", p.sourceCode)
            })
        }

        root.put("hermes_version", "1.0")
        root.put("export_timestamp", System.currentTimeMillis())
        root.put("scripts", scriptArr)
        root.put("plugins", pluginArr)
        return root.toString(2)
    }

    override fun onCleared() {
        super.onCleared()
        deviceController.release()
    }
}
