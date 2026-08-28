package com.example.engine

import com.example.data.local.ExecutionLog
import com.example.data.remote.AgentToolCall
import com.example.data.remote.GeminiApi
import com.example.data.repository.HermesRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed interface AgentState {
    data object Idle : AgentState
    data class Thinking(val prompt: String) : AgentState
    data class Executing(val prompt: String, val thought: String, val toolName: String) : AgentState
    data class Completed(
        val prompt: String,
        val thought: String,
        val toolCalled: String?,
        val resultMessage: String,
        val durationMs: Long
    ) : AgentState
    data class Error(val prompt: String, val errorMessage: String) : AgentState
}

data class ExecutionStep(
    val title: String,
    val detail: String,
    val status: String, // SUCCESS, RUNNING, ERROR
    val timestamp: Long = System.currentTimeMillis()
)

class HermesAgent(
    private val repository: HermesRepository,
    private val deviceController: HermesDeviceController,
    private val scriptEngine: HermesScriptEngine,
    private val pluginManager: HermesPluginManager
) {
    private val geminiApi = GeminiApi()

    suspend fun executeGoal(
        prompt: String,
        onStateChanged: (AgentState) -> Unit = {},
        onStepAdded: (ExecutionStep) -> Unit = {}
    ): ExecutionLog {
        val startTime = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString().take(8)
        val steps = mutableListOf<ExecutionStep>()

        onStateChanged(AgentState.Thinking(prompt))
        val step1 = ExecutionStep("Agent Initialized", "Analyzing goal: '$prompt'", "SUCCESS")
        steps.add(step1)
        onStepAdded(step1)

        try {
            // 1. Gather Context, Active Models & Active Plugins
            val config = repository.getConfig()
            val activeModelEndpoint = repository.getActiveModel()
            val deviceStatsJson = deviceController.getDeviceStatsJson()
            val availablePluginTools = pluginManager.getAvailablePluginTools()

            val step2 = ExecutionStep(
                "Telemetry & Toolset Gathered",
                "Device: ${deviceController.getDeviceStats().networkType} | Active Model: ${activeModelEndpoint?.name ?: config.activeModel} | Tools: ${availablePluginTools.size}",
                "SUCCESS"
            )
            steps.add(step2)
            onStepAdded(step2)

            // 2. Query Active Model / Brain
            val agentResponse = geminiApi.generateAgentPlan(
                userPrompt = prompt,
                systemInstruction = config.systemPrompt,
                activeEndpoint = activeModelEndpoint,
                customApiKey = config.customApiKey,
                modelName = activeModelEndpoint?.modelName ?: config.activeModel,
                deviceContextJson = deviceStatsJson
            )

            val thought = agentResponse.thought ?: "Formulated execution path."
            val toolCall = agentResponse.toolCall

            val step3 = ExecutionStep(
                "Reasoning & Strategy",
                thought,
                "SUCCESS"
            )
            steps.add(step3)
            onStepAdded(step3)

            var outputMsg = agentResponse.finalResponse ?: "Task finished."
            var actionStatus = "SUCCESS"

            // 3. Execute Tool if specified
            if (toolCall != null) {
                onStateChanged(AgentState.Executing(prompt, thought, toolCall.name))
                val step4 = ExecutionStep("Dispatching Tool", "Invoking [${toolCall.name}] with arguments: ${toolCall.arguments}", "RUNNING")
                steps.add(step4)
                onStepAdded(step4)

                val toolResult = dispatchToolCall(toolCall)
                outputMsg = "${agentResponse.finalResponse ?: ""}\nExecution Result: ${toolResult.message}".trim()
                if (!toolResult.success) {
                    actionStatus = "FAILED"
                }

                val step5 = ExecutionStep(
                    if (toolResult.success) "Tool Action Completed" else "Tool Action Warning",
                    toolResult.message + if (toolResult.data != null) " -> Data: ${toolResult.data}" else "",
                    if (toolResult.success) "SUCCESS" else "ERROR"
                )
                steps.add(step5)
                onStepAdded(step5)
            } else {
                // Conversational feedback
                if (config.enableTtsVoice) {
                    deviceController.speak(outputMsg)
                }
            }

            val totalDuration = System.currentTimeMillis() - startTime
            onStateChanged(
                AgentState.Completed(
                    prompt = prompt,
                    thought = thought,
                    toolCalled = toolCall?.name,
                    resultMessage = outputMsg,
                    durationMs = totalDuration
                )
            )

            // Build JSON steps array for log record
            val stepsJsonArray = JSONArray().apply {
                steps.forEach { s ->
                    put(JSONObject().apply {
                        put("title", s.title)
                        put("detail", s.detail)
                        put("status", s.status)
                        put("timestamp", s.timestamp)
                    })
                }
            }

            val log = ExecutionLog(
                sessionId = sessionId,
                prompt = prompt,
                planSummary = thought,
                status = actionStatus,
                stepsJson = stepsJsonArray.toString(),
                outputResult = outputMsg,
                durationMs = totalDuration,
                isAiDriven = true,
                sourceName = "Hermes Agent"
            )

            repository.insertLog(log)
            return log

        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            val errMsg = "Execution failed: ${e.localizedMessage ?: e.message}"
            onStateChanged(AgentState.Error(prompt, errMsg))

            val errStep = ExecutionStep("Agent Exception", errMsg, "ERROR")
            steps.add(errStep)
            onStepAdded(errStep)

            val log = ExecutionLog(
                sessionId = sessionId,
                prompt = prompt,
                planSummary = "Error during execution cycle",
                status = "FAILED",
                stepsJson = JSONArray().apply {
                    steps.forEach { s ->
                        put(JSONObject().apply {
                            put("title", s.title)
                            put("detail", s.detail)
                            put("status", s.status)
                        })
                    }
                }.toString(),
                outputResult = errMsg,
                durationMs = totalDuration,
                isAiDriven = true,
                sourceName = "Hermes Agent"
            )
            repository.insertLog(log)
            return log
        }
    }

    private suspend fun dispatchToolCall(tool: AgentToolCall): ActionResult {
        return when (tool.name) {
            "get_device_stats" -> {
                val stats = deviceController.getDeviceStats()
                ActionResult(
                    true,
                    "Battery: ${stats.batteryLevel}% (Charging: ${stats.isCharging}), Storage Free: ${stats.freeStorageMb}MB, Network: ${stats.networkType}",
                    stats
                )
            }
            "set_clipboard" -> {
                val text = tool.arguments["text"]?.toString() ?: ""
                val ok = deviceController.setClipboard(text)
                ActionResult(ok, if (ok) "Copied text to clipboard" else "Failed to set clipboard")
            }
            "read_clipboard" -> {
                val clip = deviceController.readClipboard()
                ActionResult(true, "Read clipboard (${clip.length} chars)", clip)
            }
            "speak" -> {
                val text = tool.arguments["text"]?.toString() ?: "Hermes active"
                deviceController.speak(text)
                ActionResult(true, "Speech synthesized: $text")
            }
            "vibrate" -> {
                val duration = (tool.arguments["duration_ms"] as? Number)?.toLong() ?: 300L
                deviceController.vibrate(duration)
                ActionResult(true, "Vibrated for ${duration}ms")
            }
            "send_notification" -> {
                val title = tool.arguments["title"]?.toString() ?: "Hermes Notification"
                val message = tool.arguments["message"]?.toString() ?: "Automation completed"
                val ok = deviceController.sendNotification(title, message)
                ActionResult(ok, if (ok) "Notification posted: $title" else "Notification failed")
            }
            "launch_app" -> {
                val app = tool.arguments["package_or_app_name"]?.toString() ?: ""
                deviceController.launchApp(app)
            }
            "open_url" -> {
                val url = tool.arguments["url"]?.toString() ?: ""
                deviceController.openUrl(url)
            }
            "toggle_flashlight" -> {
                val enabled = (tool.arguments["enabled"] as? Boolean) ?: true
                deviceController.toggleFlashlight(enabled)
            }
            "run_script" -> {
                val scriptName = tool.arguments["script_name"]?.toString() ?: ""
                val scripts = repository.allScripts.first()
                val targetScript = scripts.firstOrNull { it.name.contains(scriptName, ignoreCase = true) }
                    ?: scripts.firstOrNull()

                if (targetScript != null) {
                    val result = scriptEngine.executeScript(targetScript.scriptCode)
                    repository.recordScriptExecution(targetScript.id)
                    ActionResult(result.success, "Script [${targetScript.name}] finished: ${result.output}", result.output)
                } else {
                    ActionResult(false, "No matching script found for '$scriptName'")
                }
            }
            "plugin_action" -> {
                val pkg = tool.arguments["package_id"]?.toString() ?: ""
                val act = tool.arguments["action_name"]?.toString() ?: ""
                @Suppress("UNCHECKED_CAST")
                val params = (tool.arguments["params"] as? Map<String, Any?>) ?: emptyMap()
                pluginManager.executePluginAction(pkg, act, params)
            }
            else -> {
                ActionResult(false, "Unknown tool operation: ${tool.name}")
            }
        }
    }
}
