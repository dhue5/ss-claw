package com.example.engine

import com.example.data.local.AiModelEndpoint
import com.example.data.local.AutomationScript
import com.example.data.local.PluginExtension
import com.example.data.repository.HermesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class HotUpdateResult(
    val success: Boolean,
    val message: String,
    val updatedModelsCount: Int = 0,
    val updatedScriptsCount: Int = 0,
    val updatedPluginsCount: Int = 0,
    val version: String = "1.1.0-HOTFIX"
)

class HermesHotUpdateManager(
    private val repository: HermesRepository,
    private val scriptEngine: HermesScriptEngine,
    private val pluginManager: HermesPluginManager
) {
    suspend fun checkForHotUpdates(): HotUpdateResult = withContext(Dispatchers.IO) {
        try {
            var updatedModels = 0
            var updatedScripts = 0
            var updatedPlugins = 0

            val existingModels = repository.getAllModels()
            val existingModelNames = existingModels.map { it.name }.toSet()

            // 1. Hot-Update New AI Models (DeepSeek-R1, Claude 3.5 Sonnet, GLM-4)
            val newModelUpgrades = listOf(
                AiModelEndpoint(
                    name = "DeepSeek-R1 深度推理模型",
                    provider = "DEEPSEEK",
                    baseUrl = "https://api.deepseek.com/v1",
                    modelName = "deepseek-reasoner",
                    apiKey = "",
                    isActive = false,
                    isPreset = true,
                    temperature = 0.6f
                ),
                AiModelEndpoint(
                    name = "Claude 3.5 Sonnet (OneAPI/中转接口)",
                    provider = "CLAUDE",
                    baseUrl = "https://api.anthropic.com/v1",
                    modelName = "claude-3-5-sonnet-20241022",
                    apiKey = "",
                    isActive = false,
                    isPreset = true,
                    temperature = 0.3f
                ),
                AiModelEndpoint(
                    name = "智谱 GLM-4-Flash (免费高速)",
                    provider = "OPENAI_COMPATIBLE",
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                    modelName = "glm-4-flash",
                    apiKey = "",
                    isActive = false,
                    isPreset = true,
                    temperature = 0.3f
                )
            )

            for (m in newModelUpgrades) {
                if (!existingModelNames.contains(m.name)) {
                    repository.insertModel(m)
                    updatedModels++
                }
            }

            // 2. Hot-Update New Intelligent Script Packages
            val existingScripts = repository.getScriptsByTrigger("MANUAL") + repository.getScriptsByTrigger("CLIPBOARD_CHANGE")
            val existingScriptNames = existingScripts.map { it.name }.toSet()

            val newScriptUpgrades = listOf(
                AutomationScript(
                    name = "智能剪贴板多语言实时翻译",
                    triggerType = "CLIPBOARD_CHANGE",
                    description = "当复制包含外文的文本时，智能识别并调用大模型自动翻译后写入剪贴板并弹窗提醒",
                    scriptCode = """
                        // 剪贴板自动翻译热更新脚本
                        DEVICE:read_clipboard()
                        AI:translate_text(target_lang="zh-CN")
                        DEVICE:set_clipboard(text="{{result}}")
                        DEVICE:send_notification(title="翻译完成", message="{{result}}")
                    """.trimIndent(),
                    isEnabled = true
                ),
                AutomationScript(
                    name = "全局异常通知拦截与安全审计",
                    triggerType = "NOTIFICATION_POSTED",
                    description = "自动审计拦截到的包含验证码、密码或风险提示的通知，提取关键要素",
                    scriptCode = """
                        // 通知安全审计热更新脚本
                        DEVICE:vibrate(duration_ms=200)
                        AI:extract_sms_code(text="{{notification_content}}")
                    """.trimIndent(),
                    isEnabled = true
                )
            )

            for (s in newScriptUpgrades) {
                if (!existingScriptNames.contains(s.name)) {
                    repository.insertScript(s)
                    updatedScripts++
                }
            }

            // 3. Hot-Reload in-memory engines
            scriptEngine.hotReload()
            pluginManager.hotReload()

            HotUpdateResult(
                success = true,
                message = "热更新成功！已同步最新大模型配置、自动化脚本库并热重载引擎。",
                updatedModelsCount = updatedModels,
                updatedScriptsCount = updatedScripts,
                updatedPluginsCount = updatedPlugins,
                version = "v1.1.0-OTA"
            )
        } catch (e: Exception) {
            HotUpdateResult(
                success = false,
                message = "热更新检查失败: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    suspend fun applyCustomHotUpdateBundle(jsonString: String): HotUpdateResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            var modelsCount = 0
            var scriptsCount = 0
            var pluginsCount = 0

            // Apply Models
            if (root.has("models")) {
                val modelsArray = root.getJSONArray("models")
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    val endpoint = AiModelEndpoint(
                        name = m.getString("name"),
                        provider = m.optString("provider", "OPENAI_COMPATIBLE"),
                        baseUrl = m.getString("baseUrl"),
                        modelName = m.getString("modelName"),
                        apiKey = m.optString("apiKey", ""),
                        customHeadersJson = m.optString("customHeadersJson", "{}"),
                        temperature = m.optDouble("temperature", 0.3).toFloat(),
                        maxTokens = m.optInt("maxTokens", 4096),
                        isActive = m.optBoolean("isActive", false),
                        isPreset = false
                    )
                    repository.insertModel(endpoint)
                    modelsCount++
                }
            }

            // Apply Scripts
            if (root.has("scripts")) {
                val scriptsArray = root.getJSONArray("scripts")
                for (i in 0 until scriptsArray.length()) {
                    val s = scriptsArray.getJSONObject(i)
                    val script = AutomationScript(
                        name = s.getString("name"),
                        triggerType = s.optString("triggerType", "MANUAL"),
                        description = s.optString("description", ""),
                        scriptCode = s.getString("scriptCode"),
                        isEnabled = s.optBoolean("isEnabled", true)
                    )
                    repository.insertScript(script)
                    scriptsCount++
                }
            }

            // Apply Plugins
            if (root.has("plugins")) {
                val pluginsArray = root.getJSONArray("plugins")
                for (i in 0 until pluginsArray.length()) {
                    val p = pluginsArray.getJSONObject(i)
                    val plugin = PluginExtension(
                        packageId = p.getString("packageId"),
                        name = p.getString("name"),
                        version = p.optString("version", "1.0.0"),
                        description = p.optString("description", ""),
                        author = p.optString("author", "Community"),
                        sourceCode = p.optString("sourceCode", ""),
                        actionsJson = p.optString("actionsJson", "[]"),
                        isEnabled = true
                    )
                    repository.insertPlugin(plugin)
                    pluginsCount++
                }
            }

            scriptEngine.hotReload()
            pluginManager.hotReload()

            HotUpdateResult(
                success = true,
                message = "自定义热更新补丁安装成功！",
                updatedModelsCount = modelsCount,
                updatedScriptsCount = scriptsCount,
                updatedPluginsCount = pluginsCount
            )
        } catch (e: Exception) {
            HotUpdateResult(
                success = false,
                message = "热更新补丁解析失败: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
