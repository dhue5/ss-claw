package com.example.engine

import com.example.data.local.PluginExtension
import com.example.data.repository.HermesRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class PluginToolDefinition(
    val pluginPackageId: String,
    val pluginName: String,
    val actionName: String,
    val description: String,
    val parameters: List<String>
)

data class MarketplacePlugin(
    val packageId: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val iconName: String,
    val permissions: String,
    val configTemplateJson: String,
    val actionsJson: String,
    val sourceCode: String,
    val updateUrl: String,
    val changelog: String,
    val isInstalled: Boolean = false,
    val hasUpdate: Boolean = false
)

class HermesPluginManager(
    private val repository: HermesRepository,
    private val scriptEngine: HermesScriptEngine,
    private val deviceController: HermesDeviceController
) {
    /**
     * Extracts all callable tools from actively enabled plugins
     */
    suspend fun getAvailablePluginTools(): List<PluginToolDefinition> {
        val plugins = repository.activePlugins.first()
        val tools = mutableListOf<PluginToolDefinition>()

        for (plugin in plugins) {
            try {
                val actions = JSONArray(plugin.actionsJson)
                for (i in 0 until actions.length()) {
                    val actionObj = actions.getJSONObject(i)
                    val actName = actionObj.getString("name")
                    val desc = actionObj.optString("description", "")
                    val paramsArr = actionObj.optJSONArray("params")
                    val params = mutableListOf<String>()
                    if (paramsArr != null) {
                        for (p in 0 until paramsArr.length()) {
                            params.add(paramsArr.getString(p))
                        }
                    }
                    tools.add(
                        PluginToolDefinition(
                            pluginPackageId = plugin.packageId,
                            pluginName = plugin.name,
                            actionName = actName,
                            description = desc,
                            parameters = params
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return tools
    }

    /**
     * Executes a specific tool provided by a plugin
     */
    suspend fun executePluginAction(
        packageId: String,
        actionName: String,
        params: Map<String, Any?>
    ): ActionResult {
        val plugin = repository.getPluginByPackageId(packageId)
            ?: return ActionResult(false, "Plugin $packageId not installed")

        if (!plugin.isEnabled) {
            return ActionResult(false, "Plugin ${plugin.name} is currently disabled")
        }

        // Run plugin source code via Script Engine with injected context
        return try {
            val configMap = mutableMapOf<String, Any>()
            if (plugin.configJson.isNotBlank()) {
                val cfgObj = JSONObject(plugin.configJson)
                val keys = cfgObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    configMap[k] = cfgObj.get(k)
                }
            }

            val inputContext = mapOf(
                "action" to actionName,
                "params" to params,
                "config" to configMap
            )

            val execResult = scriptEngine.executeScript(plugin.sourceCode, inputContext)
            ActionResult(
                success = execResult.success,
                message = "Plugin [${plugin.name}] action '$actionName' executed",
                data = execResult.output
            )
        } catch (e: Exception) {
            ActionResult(false, "Plugin execution error: ${e.message}")
        }
    }

    /**
     * Community / Hub plugins available for download & upgrade
     */
    fun getMarketplaceCatalog(): List<MarketplacePlugin> {
        return listOf(
            MarketplacePlugin(
                packageId = "com.hermes.plugin.telegram",
                name = "Telegram Bot Automator",
                version = "1.3.0", // Has update compared to 1.2.0
                author = "Hermes Core",
                description = "Send automated alerts, logs, and device snapshots to your private Telegram channel or chat.",
                iconName = "send",
                permissions = "INTERNET,NOTIFICATION",
                configTemplateJson = """{"botToken": "", "chatId": "", "parseMode": "HTML"}""",
                actionsJson = """[{"name":"telegram_send","description":"Send automated message via Telegram Bot","params":["message","chatId"]}]""",
                sourceCode = """
                    // Telegram Bot Plugin
                    const token = config.botToken || "DEMO_TOKEN";
                    const chat = params.chatId || config.chatId || "DEMO_CHAT";
                    const url = "https://api.telegram.org/bot" + token + "/sendMessage";
                    const res = device.httpPost(url, JSON.stringify({chat_id: chat, text: params.message}), {"Content-Type": "application/json"});
                    device.toast("Telegram message sent");
                    return res;
                """.trimIndent(),
                updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/telegram.json",
                changelog = "v1.3.0: High-speed retry logic and Markdown formatting"
            ),
            MarketplacePlugin(
                packageId = "com.hermes.plugin.github",
                name = "GitHub Issue & Repo Sync",
                version = "1.1.0",
                author = "DevOps Labs",
                description = "Create GitHub issues, star repositories, and sync automation logs to your private GitHub repository.",
                iconName = "code",
                permissions = "INTERNET",
                configTemplateJson = """{"githubToken": "", "repoOwner": "", "repoName": ""}""",
                actionsJson = """[{"name":"github_create_issue","description":"Create a new issue in target repository","params":["title","body"]}]""",
                sourceCode = """
                    // GitHub Plugin
                    const owner = config.repoOwner;
                    const repo = config.repoName;
                    const token = config.githubToken;
                    const url = "https://api.github.com/repos/" + owner + "/" + repo + "/issues";
                    const res = device.httpPost(url, JSON.stringify({title: params.title, body: params.body}), {"Authorization": "token " + token, "User-Agent": "Hermes-Android"});
                    device.toast("GitHub Issue Created");
                    return res;
                """.trimIndent(),
                updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/github.json",
                changelog = "v1.1.0: Added label tags and milestone support"
            ),
            MarketplacePlugin(
                packageId = "com.hermes.plugin.homeassistant",
                name = "Home Assistant IoT Hub",
                version = "2.1.0",
                author = "IoT Community",
                description = "Control smart lights, switches, scenes, and trigger home automations directly from Hermes agent.",
                iconName = "home",
                permissions = "INTERNET,NETWORK",
                configTemplateJson = """{"baseUrl": "http://homeassistant.local:8123", "bearerToken": ""}""",
                actionsJson = """[{"name":"ha_call_service","description":"Call Home Assistant service (e.g. light/turn_on)","params":["domain","service","entityId"]},{"name":"ha_get_state","description":"Get entity state from Home Assistant","params":["entityId"]}]""",
                sourceCode = """
                    // Home Assistant Hub
                    const baseUrl = config.baseUrl;
                    const endpoint = baseUrl + "/api/services/" + params.domain + "/" + params.service;
                    const res = device.httpPost(endpoint, JSON.stringify({ entity_id: params.entityId }), {"Authorization": "Bearer " + config.bearerToken, "Content-Type": "application/json"});
                    device.toast("HA Service Triggered: " + params.service);
                    return res;
                """.trimIndent(),
                updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/homeassistant.json",
                changelog = "v2.1.0: Real-time sensor state retrieval"
            ),
            MarketplacePlugin(
                packageId = "com.hermes.plugin.dailybrief",
                name = "Daily AI Briefing & Audio Digest",
                version = "1.1.0",
                author = "Hermes Studio",
                description = "Collects system stats, clipboard insights, daily goals and synthesizes a morning audio briefing.",
                iconName = "campaign",
                permissions = "VIBRATE,AUDIO,NOTIFICATION",
                configTemplateJson = """{"greetingName": "Commander", "includeWeather": true, "enableSpeech": true}""",
                actionsJson = """[{"name":"generate_daily_brief","description":"Synthesize and speak daily morning briefing with device health and agenda","params":["notes"]}]""",
                sourceCode = """
                    // Daily Briefing Plugin
                    const stats = device.getDeviceStats();
                    const greeting = "Good morning, " + (config.greetingName || "User") + "!";
                    const msg = greeting + " Battery level is " + stats.batteryLevel + "%, Network is " + stats.networkType + ". Hermes engine ready.";
                    if (config.enableSpeech) {
                        device.speak(msg);
                    }
                    device.notify("Daily Briefing", msg);
                    return { summary: msg };
                """.trimIndent(),
                updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/dailybrief.json",
                changelog = "v1.1.0: Added multi-lingual speech output options"
            ),
            MarketplacePlugin(
                packageId = "com.hermes.plugin.webscraper",
                name = "Webpage Content Extractor",
                version = "1.0.0",
                author = "Web Automators",
                description = "Fetches web page HTML/text, extracts titles and links, and copies formatted results to clipboard.",
                iconName = "language",
                permissions = "INTERNET",
                configTemplateJson = """{"maxResults": 10}""",
                actionsJson = """[{"name":"fetch_and_summarize","description":"Fetch raw web page and extract summary","params":["url"]}]""",
                sourceCode = """
                    // Web Extractor Plugin
                    const res = device.httpGet(params.url);
                    device.toast("Fetched " + res.durationMs + "ms");
                    return { statusCode: res.statusCode, length: (res.body || "").length };
                """.trimIndent(),
                updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/webscraper.json",
                changelog = "v1.0.0: Initial release of Web Content Extractor"
            )
        )
    }

    suspend fun installMarketplacePlugin(marketPlugin: MarketplacePlugin) {
        val existing = repository.getPluginByPackageId(marketPlugin.packageId)
        if (existing == null) {
            repository.insertPlugin(
                PluginExtension(
                    packageId = marketPlugin.packageId,
                    name = marketPlugin.name,
                    version = marketPlugin.version,
                    author = marketPlugin.author,
                    description = marketPlugin.description,
                    iconName = marketPlugin.iconName,
                    isEnabled = true,
                    permissions = marketPlugin.permissions,
                    configJson = marketPlugin.configTemplateJson,
                    actionsJson = marketPlugin.actionsJson,
                    sourceCode = marketPlugin.sourceCode,
                    updateUrl = marketPlugin.updateUrl,
                    changelog = marketPlugin.changelog
                )
            )
        } else {
            // Upgrade existing
            repository.upgradePlugin(
                id = existing.id,
                version = marketPlugin.version,
                changelog = marketPlugin.changelog,
                actionsJson = marketPlugin.actionsJson,
                sourceCode = marketPlugin.sourceCode
            )
        }
    }

    fun hotReload() {
        // Hot-reload dynamic JavaScript context & plugin registry
    }
}
