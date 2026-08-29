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
    val isStreaming: Boolean = false,
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
    val voiceSoundLevel: StateFlow<Float> = voiceManager.soundLevel
    val isSpeakingTts: StateFlow<Boolean> = voiceManager.isSpeaking
    val speakingMessageId: StateFlow<String?> = voiceManager.speakingMessageId

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

    fun toggleSpeakMessage(messageId: String, text: String) {
        voiceManager.speak(text, messageId)
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun clearChatHistory() {
        voiceManager.stopSpeaking()
        _uiState.update {
            it.copy(
                chatMessages = listOf(
                    ChatMessage(
                        isUser = false,
                        text = "会话历史已清空。Hermes 智能中枢准备就绪，随时响应您的自动化指令。",
                        thought = "上下文重置完成，内存缓存已刷新。"
                    )
                )
            )
        }
    }

    fun regenerateLastPrompt() {
        if (_uiState.value.isProcessing) return
        val lastUserMessage = _uiState.value.chatMessages.lastOrNull { it.isUser }
        if (lastUserMessage != null) {
            submitPrompt(lastUserMessage.text)
        }
    }

    fun getDynamicSuggestions(): List<String> {
        val list = mutableListOf<String>()
        val stats = _uiState.value.deviceStats

        // Context-aware dynamic suggestion based on battery
        if (stats != null && stats.batteryLevel <= 30 && !stats.isCharging) {
            list.add("🔋 电池仅剩 ${stats.batteryLevel}%，开启极限省电")
        }

        // Context-aware based on clipboard
        val clipboard = deviceController.readClipboard()
        if (!clipboard.isNullOrBlank() && clipboard.length < 80 && (clipboard.startsWith("http://") || clipboard.startsWith("https://"))) {
            list.add("🔗 分析并处理剪贴板链接")
        } else if (!clipboard.isNullOrBlank() && clipboard.length in 5..60) {
            list.add("📋 总结剪贴板文本")
        }

        // Time-aware suggestion
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour in 21..23 || hour in 0..5) {
            list.add("🌙 启动夜间模式与就寝简报")
        } else if (hour in 6..9) {
            list.add("☀️ 生成今日晨间早报与系统概况")
        }

        // Core system capabilities
        list.add("⚡ 全量诊断系统性能与资源")
        list.add("📱 扫描当前活跃前台应用")
        list.add("🔔 发送自动化测试通知")
        list.add("🛠️ 运行 Telegram 消息推送插件")

        return list.distinct().take(6)
    }

    // --- AGENT GOAL EXECUTION ---
    fun submitPrompt(prompt: String) {
        if (prompt.isBlank() || _uiState.value.isProcessing) return

        val userMessage = ChatMessage(isUser = true, text = prompt)
        val assistantMessageId = java.util.UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(
            id = assistantMessageId,
            isUser = false,
            text = "正在深度推理规划与调用工具中...",
            thought = "正在分析指令 '$prompt' 并检索设备状态与工具集...",
            steps = emptyList(),
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                isProcessing = true,
                currentSteps = emptyList(),
                chatMessages = it.chatMessages + userMessage + initialAssistantMsg
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            val liveSteps = mutableListOf<ExecutionStep>()
            agent.executeGoal(
                prompt = prompt,
                onStateChanged = { state ->
                    _uiState.update { current ->
                        val updatedList = current.chatMessages.map { msg ->
                            if (msg.id == assistantMessageId) {
                                when (state) {
                                    is AgentState.Thinking -> msg.copy(
                                        thought = "思考与策略规划中...",
                                        text = "正在分析指令目标并制定执行策略..."
                                    )
                                    is AgentState.Executing -> msg.copy(
                                        thought = state.thought,
                                        toolCall = state.toolName,
                                        text = "正在调度执行工具 [${state.toolName}]..."
                                    )
                                    else -> msg
                                }
                            } else msg
                        }
                        current.copy(agentState = state, chatMessages = updatedList)
                    }
                },
                onStepAdded = { step ->
                    liveSteps.add(step)
                    _uiState.update { current ->
                        val updatedList = current.chatMessages.map { msg ->
                            if (msg.id == assistantMessageId) {
                                msg.copy(steps = liveSteps.toList())
                            } else msg
                        }
                        current.copy(currentSteps = liveSteps.toList(), chatMessages = updatedList)
                    }
                }
            )

            // When finished, stream out the final output characters for a smooth typing animation
            val finalState = _uiState.value.agentState
            val (fullTargetText, finalThought, finalToolCall) = when (finalState) {
                is AgentState.Completed -> Triple(
                    finalState.resultMessage,
                    finalState.thought,
                    finalState.toolCalled
                )
                is AgentState.Error -> Triple(
                    "Execution Error: ${finalState.errorMessage}",
                    "Agent caught an exception during execution.",
                    null
                )
                else -> Triple(
                    "Task cycle concluded.",
                    null,
                    null
                )
            }

            // Stream typewriter effect into the assistant chat message
            val chunkSize = when {
                fullTargetText.length > 200 -> 8
                fullTargetText.length > 60 -> 4
                else -> 2
            }
            var displayedLength = 0
            while (displayedLength < fullTargetText.length) {
                displayedLength = (displayedLength + chunkSize).coerceAtMost(fullTargetText.length)
                val currentText = fullTargetText.substring(0, displayedLength)
                _uiState.update { current ->
                    val updatedList = current.chatMessages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(
                                text = currentText,
                                thought = finalThought,
                                toolCall = finalToolCall,
                                steps = liveSteps.toList(),
                                isStreaming = displayedLength < fullTargetText.length
                            )
                        } else msg
                    }
                    current.copy(chatMessages = updatedList)
                }
                delay(16)
            }

            // Finalize streaming
            _uiState.update { current ->
                val updatedList = current.chatMessages.map { msg ->
                    if (msg.id == assistantMessageId) {
                        msg.copy(
                            text = fullTargetText,
                            thought = finalThought,
                            toolCall = finalToolCall,
                            steps = liveSteps.toList(),
                            isStreaming = false
                        )
                    } else msg
                }
                current.copy(
                    isProcessing = false,
                    chatMessages = updatedList
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
