package com.example.data.remote

import com.example.BuildConfig
import com.example.data.local.AiModelEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiAgentResponse(
    val thought: String?,
    val toolCall: AgentToolCall?,
    val finalResponse: String?,
    val rawJson: String?
)

data class AgentToolCall(
    val name: String,
    val arguments: Map<String, Any?>
)

class GeminiApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun generateAgentPlan(
        userPrompt: String,
        systemInstruction: String,
        activeEndpoint: AiModelEndpoint? = null,
        customApiKey: String? = null,
        modelName: String = "gemini-2.5-flash",
        deviceContextJson: String = "{}"
    ): GeminiAgentResponse = withContext(Dispatchers.IO) {
        // If an active custom endpoint is provided, route according to its provider
        if (activeEndpoint != null) {
            val provider = activeEndpoint.provider.uppercase()
            val effectiveKey = activeEndpoint.apiKey.ifBlank {
                if (provider == "GEMINI") {
                    customApiKey?.ifBlank { BuildConfig.GEMINI_API_KEY } ?: BuildConfig.GEMINI_API_KEY
                } else ""
            }

            if (provider == "GEMINI") {
                return@withContext callGeminiApi(
                    userPrompt = userPrompt,
                    systemInstruction = systemInstruction,
                    apiKey = effectiveKey,
                    modelName = activeEndpoint.modelName.ifBlank { modelName },
                    deviceContextJson = deviceContextJson,
                    temperature = activeEndpoint.temperature
                )
            } else {
                // OpenAI-compatible format for DeepSeek, Qwen, Ollama, OpenAI, Claude-compatible proxy, etc.
                return@withContext callOpenAiCompatibleApi(
                    endpoint = activeEndpoint,
                    apiKey = effectiveKey,
                    userPrompt = userPrompt,
                    systemInstruction = systemInstruction,
                    deviceContextJson = deviceContextJson
                )
            }
        }

        // Fallback default Gemini routing
        val effectiveKey = customApiKey?.ifBlank { BuildConfig.GEMINI_API_KEY } ?: BuildConfig.GEMINI_API_KEY
        if (effectiveKey.isBlank() || effectiveKey == "MY_GEMINI_API_KEY") {
            return@withContext runLocalHeuristicAgent(userPrompt, deviceContextJson)
        }

        callGeminiApi(
            userPrompt = userPrompt,
            systemInstruction = systemInstruction,
            apiKey = effectiveKey,
            modelName = modelName,
            deviceContextJson = deviceContextJson,
            temperature = 0.3f
        )
    }

    private fun callGeminiApi(
        userPrompt: String,
        systemInstruction: String,
        apiKey: String,
        modelName: String,
        deviceContextJson: String,
        temperature: Float
    ): GeminiAgentResponse {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return runLocalHeuristicAgent(userPrompt, deviceContextJson)
        }

        try {
            val cleanModel = modelName.removePrefix("models/")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

            val promptWithContext = buildAgentPromptTemplate(userPrompt, deviceContextJson)

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptWithContext)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature.toDouble())
                    put("responseMimeType", "application/json")
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Accept", "application/json; charset=utf-8")
                .addHeader("Accept-Charset", "utf-8")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBytes = response.body?.bytes()
            val responseBody = if (responseBytes != null) String(responseBytes, Charsets.UTF_8) else ""

            if (!response.isSuccessful) {
                // If 404/400 or invalid key, fallback seamlessly to intelligent local heuristic engine
                return runLocalHeuristicAgent(
                    prompt = userPrompt,
                    deviceContextJson = deviceContextJson,
                    note = "在线模型接口响应码 ${response.code}，已无缝激活 Hermes 本地神经规则引擎接管执行。"
                )
            }

            return parseGeminiResponse(responseBody, userPrompt)
        } catch (e: Exception) {
            return runLocalHeuristicAgent(userPrompt, deviceContextJson, "Gemini 网络异常: ${e.localizedMessage}")
        }
    }

    private fun callOpenAiCompatibleApi(
        endpoint: AiModelEndpoint,
        apiKey: String,
        userPrompt: String,
        systemInstruction: String,
        deviceContextJson: String
    ): GeminiAgentResponse {
        try {
            val base = endpoint.baseUrl.trim().trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

            val promptWithContext = buildAgentPromptTemplate(userPrompt, deviceContextJson)

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", promptWithContext)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", endpoint.modelName)
                put("messages", messages)
                put("temperature", endpoint.temperature.toDouble())
                put("max_tokens", endpoint.maxTokens)
                if (endpoint.provider != "OLLAMA") {
                    put("response_format", JSONObject().apply { put("type", "json_object") })
                }
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Accept", "application/json; charset=utf-8")
                .addHeader("Accept-Charset", "utf-8")
                .post(requestJson.toString().toRequestBody(mediaType))

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            // Custom headers if defined
            try {
                if (endpoint.customHeadersJson.isNotBlank() && endpoint.customHeadersJson != "{}") {
                    val headersObj = JSONObject(endpoint.customHeadersJson)
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        requestBuilder.addHeader(k, headersObj.getString(k))
                    }
                }
            } catch (_: Exception) {}

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBytes = response.body?.bytes()
            val responseBody = if (responseBytes != null) String(responseBytes, Charsets.UTF_8) else ""

            if (!response.isSuccessful) {
                return runLocalHeuristicAgent(
                    prompt = userPrompt,
                    deviceContextJson = deviceContextJson,
                    note = "${endpoint.name} 接口响应码 ${response.code}，已自动激活本地引擎完成任务。"
                )
            }

            return parseOpenAiResponse(responseBody, userPrompt)
        } catch (e: Exception) {
            return runLocalHeuristicAgent(userPrompt, deviceContextJson, "${endpoint.name} 请求异常: ${e.localizedMessage}")
        }
    }

    suspend fun testEndpointConnectivity(endpoint: AiModelEndpoint): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            if (endpoint.provider.uppercase() == "GEMINI") {
                val apiKey = endpoint.apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    return@withContext Pair(false, 0L)
                }
                val cleanModel = endpoint.modelName.removePrefix("models/")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "ping") })
                            })
                        })
                    })
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder().url(url).post(body).build()
                val resp = client.newCall(req).execute()
                val duration = System.currentTimeMillis() - startTime
                Pair(resp.isSuccessful, duration)
            } else {
                val base = endpoint.baseUrl.trim().trimEnd('/')
                val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                val reqBuilder = Request.Builder().url(url)

                if (endpoint.apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer ${endpoint.apiKey}")
                }

                val body = JSONObject().apply {
                    put("model", endpoint.modelName)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "ping")
                        })
                    })
                    put("max_tokens", 10)
                }.toString().toRequestBody("application/json".toMediaType())

                reqBuilder.post(body)
                val resp = client.newCall(reqBuilder.build()).execute()
                val duration = System.currentTimeMillis() - startTime
                Pair(resp.isSuccessful, duration)
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Pair(false, duration)
        }
    }

    private fun buildAgentPromptTemplate(userPrompt: String, deviceContextJson: String): String {
        return """
            User Request: $userPrompt
            
            Current Phone State:
            $deviceContextJson
            
            Instructions:
            1. If this requires performing a phone action or running a script/plugin, output a JSON object with:
               {
                 "thought": "Your step reasoning in Chinese",
                 "tool_call": {
                   "name": "<tool_name>",
                   "arguments": { ... }
                 },
                 "final_response": "Friendly Chinese status summary for the user"
               }
            2. Available tools include:
               - launch_app(package_or_app_name)
               - open_url(url)
               - send_notification(title, message)
               - set_clipboard(text)
               - read_clipboard()
               - speak(text)
               - vibrate(duration_ms)
               - toggle_flashlight(enabled)
               - http_request(method, url, body)
               - run_script(script_name_or_code)
               - get_device_stats()
               - plugin_action(package_id, action_name, params)
            3. If no tool is needed, respond with standard assistance in "final_response".
            
            Respond ONLY with a valid JSON object.
        """.trimIndent()
    }

    private fun parseGeminiResponse(jsonString: String, originalPrompt: String): GeminiAgentResponse {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

            parseStructuredJson(rawText)
        } catch (e: Exception) {
            GeminiAgentResponse(
                thought = "直接解析响应内容",
                toolCall = null,
                finalResponse = "响应: $jsonString",
                rawJson = jsonString
            )
        }
    }

    private fun parseOpenAiResponse(jsonString: String, originalPrompt: String): GeminiAgentResponse {
        return try {
            val root = JSONObject(jsonString)
            val choices = root.optJSONArray("choices")
            val choice = choices?.optJSONObject(0)
            val message = choice?.optJSONObject("message")
            val rawText = message?.optString("content") ?: ""

            parseStructuredJson(rawText)
        } catch (e: Exception) {
            GeminiAgentResponse(
                thought = "已解析响应",
                toolCall = null,
                finalResponse = jsonString,
                rawJson = jsonString
            )
        }
    }

    private fun parseStructuredJson(rawText: String): GeminiAgentResponse {
        val unescapedRaw = unescapeUnicode(rawText.trim())
        val cleanedJson = unescapedRaw.removeSurrounding("```json", "```").trim()
        val parsed = JSONObject(cleanedJson)

        val thought = unescapeUnicode(parsed.optString("thought", "正在分析目标并规划指令..."))
        val finalResp = unescapeUnicode(parsed.optString("final_response", "动作已准备就绪。"))

        var toolCall: AgentToolCall? = null
        if (parsed.has("tool_call") && !parsed.isNull("tool_call")) {
            val toolObj = parsed.getJSONObject("tool_call")
            val toolName = toolObj.getString("name")
            val argsObj = toolObj.optJSONObject("arguments") ?: JSONObject()
            val argsMap = mutableMapOf<String, Any?>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val v = argsObj.get(key)
                argsMap[key] = if (v is String) unescapeUnicode(v) else v
            }
            toolCall = AgentToolCall(toolName, argsMap)
        }

        return GeminiAgentResponse(
            thought = thought,
            toolCall = toolCall,
            finalResponse = finalResp,
            rawJson = cleanedJson
        )
    }

    private fun unescapeUnicode(input: String): String {
        if (!input.contains("\\u") && !input.contains("\\U")) return input
        return try {
            val regex = Regex("""\\u([0-9a-fA-F]{4})""")
            regex.replace(input) { match ->
                val hex = match.groupValues[1]
                hex.toInt(16).toChar().toString()
            }
        } catch (_: Exception) {
            input
        }
    }

    private fun runLocalHeuristicAgent(prompt: String, deviceContextJson: String, note: String? = null): GeminiAgentResponse {
        val lower = prompt.lowercase()
        val thoughtPrefix = if (note != null) "$note\n已自动切换至 Hermes 本地离线启发式规则引擎:\n" else "Hermes 本地启发式引擎:\n"

        return when {
            lower.contains("battery") || lower.contains("电量") || lower.contains("状态") || lower.contains("status") -> {
                GeminiAgentResponse(
                    thought = "$thoughtPrefix 用户请求查询手机电量、存储及网络遥测状态。",
                    toolCall = AgentToolCall("get_device_stats", emptyMap()),
                    finalResponse = "正在获取设备电量、存储和网络实时状态...",
                    rawJson = null
                )
            }
            lower.contains("clipboard") || lower.contains("剪贴板") || lower.contains("复制") -> {
                if (lower.contains("copy") || lower.contains("写入") || lower.contains("set") || lower.contains("复制到")) {
                    val textToCopy = prompt.substringAfter(":", prompt.substringAfter("：", prompt))
                    GeminiAgentResponse(
                        thought = "$thoughtPrefix 正在将文本写入系统剪贴板。",
                        toolCall = AgentToolCall("set_clipboard", mapOf("text" to textToCopy)),
                        finalResponse = "已将内容写入系统剪贴板。",
                        rawJson = null
                    )
                } else {
                    GeminiAgentResponse(
                        thought = "$thoughtPrefix 读取并分析剪贴板当前内容。",
                        toolCall = AgentToolCall("read_clipboard", emptyMap()),
                        finalResponse = "正在读取剪贴板内容...",
                        rawJson = null
                    )
                }
            }
            lower.contains("vibrate") || lower.contains("震动") -> {
                GeminiAgentResponse(
                    thought = "$thoughtPrefix 触发设备触觉震动马达脉冲。",
                    toolCall = AgentToolCall("vibrate", mapOf("duration_ms" to 350)),
                    finalResponse = "已触发触觉震动反馈。",
                    rawJson = null
                )
            }
            lower.contains("torch") || lower.contains("flash") || lower.contains("手电筒") -> {
                val state = !lower.contains("off") && !lower.contains("关")
                GeminiAgentResponse(
                    thought = "$thoughtPrefix 切换手电筒开关状态为 $state。",
                    toolCall = AgentToolCall("toggle_flashlight", mapOf("enabled" to state)),
                    finalResponse = if (state) "正在开启手电筒..." else "正在关闭手电筒...",
                    rawJson = null
                )
            }
            lower.contains("speak") || lower.contains("说") || lower.contains("读") || lower.contains("朗读") || lower.contains("播报") -> {
                val speechText = prompt.substringAfter("说", prompt.substringAfter("speak", prompt.substringAfter("播报", prompt))).trim()
                GeminiAgentResponse(
                    thought = "$thoughtPrefix 调度 TTS 语音合成器进行播报。",
                    toolCall = AgentToolCall("speak", mapOf("text" to if (speechText.isNotBlank()) speechText else "Hermes 自动化引擎运行正常。")),
                    finalResponse = "正在语音播报状态...",
                    rawJson = null
                )
            }
            lower.contains("notify") || lower.contains("通知") || lower.contains("提醒") -> {
                GeminiAgentResponse(
                    thought = "$thoughtPrefix 发送系统通知横幅提示。",
                    toolCall = AgentToolCall("send_notification", mapOf("title" to "Hermes 任务提醒", "message" to prompt)),
                    finalResponse = "已发送系统通知横幅。",
                    rawJson = null
                )
            }
            lower.contains("open") || lower.contains("打开") || lower.contains("启动") -> {
                val target = prompt.replace("open", "").replace("打开", "").replace("启动", "").trim()
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    GeminiAgentResponse(
                        thought = "$thoughtPrefix 在浏览器中打开网址链接。",
                        toolCall = AgentToolCall("open_url", mapOf("url" to target)),
                        finalResponse = "正在打开网址 $target...",
                        rawJson = null
                    )
                } else {
                    GeminiAgentResponse(
                        thought = "$thoughtPrefix 启动指定应用程序: $target。",
                        toolCall = AgentToolCall("launch_app", mapOf("package_or_app_name" to target)),
                        finalResponse = "正在启动应用 $target...",
                        rawJson = null
                    )
                }
            }
            lower.contains("script") || lower.contains("脚本") || lower.contains("运行") -> {
                GeminiAgentResponse(
                    thought = "$thoughtPrefix 调用 Hermes 自定义脚本任务管道。",
                    toolCall = AgentToolCall("run_script", mapOf("script_name" to "快速设备状态语音播报")),
                    finalResponse = "正在执行自动化脚本...",
                    rawJson = null
                )
            }
            else -> {
                GeminiAgentResponse(
                    thought = "$thoughtPrefix 自然语言对话模式激活。您可在【系统设置】中接入并保存多个外部 API 密钥与大模型（如 Gemini、DeepSeek、千问、Ollama等），解锁全局深度推理与多模态自动化能力。",
                    toolCall = null,
                    finalResponse = "Hermes 智能体就绪！支持多模型接入与热更新。您可以随时在设置中配置外部 API、测试延迟、或说'查询电量'、'播报状态'、'读取剪贴板'、'运行脚本'等。",
                    rawJson = null
                )
            }
        }
    }
}
