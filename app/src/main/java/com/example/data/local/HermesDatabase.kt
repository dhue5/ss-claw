package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AutomationScript::class,
        PluginExtension::class,
        ExecutionLog::class,
        ApiConfig::class,
        AiModelEndpoint::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HermesDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao
    abstract fun pluginDao(): PluginDao
    abstract fun logDao(): LogDao
    abstract fun configDao(): ConfigDao
    abstract fun modelEndpointDao(): ModelEndpointDao

    companion object {
        @Volatile
        private var INSTANCE: HermesDatabase? = null

        fun getDatabase(context: Context): HermesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HermesDatabase::class.java,
                    "hermes_engine_db"
                )
                .addCallback(DatabaseCallback(context))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = getDatabase(context)
                populateInitialData(database)
            }
        }

        private suspend fun populateInitialData(db: HermesDatabase) {
            // Seed default configuration
            db.configDao().saveConfig(
                ApiConfig(
                    id = "default_config",
                    customApiKey = "",
                    activeModel = "gemini-2.5-flash",
                    systemPrompt = "You are Hermes, an elite autonomous Android local agent. Break user commands into precise device operations, custom scripts, or plugin calls.",
                    temperature = 0.3f,
                    enableTtsVoice = true,
                    enableVibrationFeedback = true,
                    enableAutoExecuteSafeActions = true
                )
            )

            // Seed AI Model Endpoints (Multi-Model & Multi-API Support)
            db.modelEndpointDao().insertModel(
                AiModelEndpoint(
                    name = "Google Gemini 2.5 Flash (默认推荐)",
                    provider = "GEMINI",
                    baseUrl = "https://generativelanguage.googleapis.com",
                    modelName = "gemini-2.5-flash",
                    apiKey = "",
                    isActive = true,
                    isPreset = true,
                    temperature = 0.3f
                )
            )
            db.modelEndpointDao().insertModel(
                AiModelEndpoint(
                    name = "DeepSeek-V3 旗舰推理 (OpenAI兼容)",
                    provider = "DEEPSEEK",
                    baseUrl = "https://api.deepseek.com/v1",
                    modelName = "deepseek-chat",
                    apiKey = "",
                    isActive = false,
                    isPreset = true,
                    temperature = 0.2f
                )
            )
            db.modelEndpointDao().insertModel(
                AiModelEndpoint(
                    name = "通义千问 Qwen 2.5 (阿里云百炼)",
                    provider = "QWEN",
                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    modelName = "qwen-max",
                    apiKey = "",
                    isActive = false,
                    isPreset = true,
                    temperature = 0.3f
                )
            )
            db.modelEndpointDao().insertModel(
                AiModelEndpoint(
                    name = "OpenAI GPT-4o Mini",
                    provider = "OPENAI_COMPATIBLE",
                    baseUrl = "https://api.openai.com/v1",
                    modelName = "gpt-4o-mini",
                    apiKey = "",
                    isActive = false,
                    isPreset = true,
                    temperature = 0.3f
                )
            )
            db.modelEndpointDao().insertModel(
                AiModelEndpoint(
                    name = "本地 / 局域网 Ollama (Llama 3)",
                    provider = "OLLAMA",
                    baseUrl = "http://192.168.1.100:11434/v1",
                    modelName = "llama3:latest",
                    apiKey = "ollama",
                    isActive = false,
                    isPreset = true,
                    temperature = 0.4f
                )
            )

            // Seed Built-in Automation Scripts
            db.scriptDao().insertScript(
                AutomationScript(
                    name = "Quick Device Status Briefing",
                    description = "Reads battery status, available storage, network state, and speaks a voice summary.",
                    triggerType = "MANUAL",
                    scriptCode = """
                        // Hermes Script: Device Status Briefing
                        const stats = device.getDeviceStats();
                        const summary = `Device Status: Battery at ${'$'}{stats.batteryLevel}%, Storage Free: ${'$'}{stats.freeStorageMb}MB, Network: ${'$'}{stats.networkType}.`;
                        device.toast(summary);
                        device.speak(summary);
                        device.vibrate(200);
                        return { success: true, message: summary };
                    """.trimIndent(),
                    tags = "status,telemetry,voice,quick"
                )
            )

            db.scriptDao().insertScript(
                AutomationScript(
                    name = "Smart Clipboard Formatter",
                    description = "Extracts URLs or clean text from clipboard, transforms format and notifies the user.",
                    triggerType = "CLIPBOARD_CHANGE",
                    scriptCode = """
                        // Hermes Script: Clipboard Cleanser
                        const text = device.readClipboard();
                        if (!text || text.length === 0) {
                            device.toast("Clipboard is empty");
                            return { success: false };
                        }
                        const cleaned = text.trim();
                        device.setClipboard(cleaned);
                        device.notify("Hermes Clipboard", `Formatted ${'$'}{cleaned.length} characters`);
                        return { success: true, length: cleaned.length };
                    """.trimIndent(),
                    tags = "clipboard,utility"
                )
            )

            db.scriptDao().insertScript(
                AutomationScript(
                    name = "Emergency Battery Guard",
                    description = "Monitors battery level, alerts user and opens power saving settings if critical.",
                    triggerType = "BATTERY_LOW",
                    scriptCode = """
                        // Hermes Script: Battery Guard
                        const stats = device.getDeviceStats();
                        if (stats.batteryLevel <= 20 && !stats.isCharging) {
                            device.vibrate(500);
                            device.notify("Battery Alert", "Battery level is low (" + stats.batteryLevel + "%). Enabling energy conservation.");
                            device.openSettings("battery");
                        }
                        return { batteryLevel: stats.batteryLevel };
                    """.trimIndent(),
                    tags = "battery,guard,system"
                )
            )

            db.scriptDao().insertScript(
                AutomationScript(
                    name = "Webhook REST Dispatcher",
                    description = "Makes a sample HTTP POST payload to trigger external IoT or web service automation.",
                    triggerType = "API_WEBHOOK",
                    scriptCode = """
                        // Hermes Script: Webhook Trigger
                        const payload = {
                            agent: "Hermes-Android",
                            timestamp: Date.now(),
                            event: "automation_triggered"
                        };
                        const res = device.httpPost("https://httpbin.org/post", JSON.stringify(payload), { "Content-Type": "application/json" });
                        device.toast("Webhook dispatched: Status " + res.statusCode);
                        return res;
                    """.trimIndent(),
                    tags = "webhook,http,iot"
                )
            )

            // Seed Built-in Plugins
            db.pluginDao().insertPlugin(
                PluginExtension(
                    packageId = "com.hermes.plugin.telegram",
                    name = "Telegram Bot Automator",
                    version = "1.2.0",
                    author = "Hermes Core",
                    description = "Send automated alerts, logs, and device snapshots to your private Telegram channel or chat.",
                    iconName = "send",
                    permissions = "INTERNET,NOTIFICATION",
                    configJson = """{"botToken": "", "chatId": "", "parseMode": "HTML"}""",
                    actionsJson = """[{"name":"telegram_send","description":"Send automated message via Telegram Bot","params":["message","chatId"]}]""",
                    sourceCode = """
                        function execute(action, params, config) {
                            if (action === "telegram_send") {
                                const token = config.botToken || "DEMO_TOKEN";
                                const chat = params.chatId || config.chatId || "DEMO_CHAT";
                                const url = "https://api.telegram.org/bot" + token + "/sendMessage";
                                return device.httpPost(url, JSON.stringify({chat_id: chat, text: params.message}), {"Content-Type": "application/json"});
                            }
                            return { error: "Unknown action" };
                        }
                    """.trimIndent(),
                    updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/telegram.json",
                    changelog = "v1.2.0: Added MarkdownV2 & HTML format support"
                )
            )

            db.pluginDao().insertPlugin(
                PluginExtension(
                    packageId = "com.hermes.plugin.homeassistant",
                    name = "Home Assistant IoT Hub",
                    version = "2.0.1",
                    author = "IoT Community",
                    description = "Control smart lights, switches, scenes, and trigger home automations directly from Hermes agent.",
                    iconName = "home",
                    permissions = "INTERNET,NETWORK",
                    configJson = """{"baseUrl": "http://homeassistant.local:8123", "bearerToken": ""}""",
                    actionsJson = """[{"name":"ha_call_service","description":"Call Home Assistant service (e.g. light/turn_on)","params":["domain","service","entityId"]},{"name":"ha_get_state","description":"Get entity state from Home Assistant","params":["entityId"]}]""",
                    sourceCode = """
                        function execute(action, params, config) {
                            const baseUrl = config.baseUrl;
                            const headers = { "Authorization": "Bearer " + config.bearerToken, "Content-Type": "application/json" };
                            if (action === "ha_call_service") {
                                const endpoint = baseUrl + "/api/services/" + params.domain + "/" + params.service;
                                return device.httpPost(endpoint, JSON.stringify({ entity_id: params.entityId }), headers);
                            }
                            return { success: true };
                        }
                    """.trimIndent(),
                    updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/homeassistant.json",
                    changelog = "v2.0.1: Enhanced entity state polling and batch service dispatch"
                )
            )

            db.pluginDao().insertPlugin(
                PluginExtension(
                    packageId = "com.hermes.plugin.dailybrief",
                    name = "Daily AI Briefing & Audio Digest",
                    version = "1.0.4",
                    author = "Hermes Studio",
                    description = "Collects system stats, clipboard insights, daily goals and synthesizes a morning audio briefing.",
                    iconName = "campaign",
                    permissions = "VIBRATE,AUDIO,NOTIFICATION",
                    configJson = """{"greetingName": "Commander", "includeWeather": true, "enableSpeech": true}""",
                    actionsJson = """[{"name":"generate_daily_brief","description":"Synthesize and speak daily morning briefing with device health and agenda","params":["notes"]}]""",
                    sourceCode = """
                        function execute(action, params, config) {
                            const stats = device.getDeviceStats();
                            const greeting = "Good morning, " + (config.greetingName || "User") + "!";
                            const msg = greeting + " Battery is " + stats.batteryLevel + " percent. All systems operational.";
                            if (config.enableSpeech) {
                                device.speak(msg);
                            }
                            device.notify("Daily Briefing", msg);
                            return { success: true, summary: msg };
                        }
                    """.trimIndent(),
                    updateUrl = "https://raw.githubusercontent.com/hermes-plugins/registry/main/dailybrief.json",
                    changelog = "v1.0.4: Added customizable TTS greeting and system health report"
                )
            )
        }
    }
}
