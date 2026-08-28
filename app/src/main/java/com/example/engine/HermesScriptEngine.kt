package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ScriptExecutionResult(
    val success: Boolean,
    val logs: List<String>,
    val output: String,
    val durationMs: Long,
    val error: String? = null
)

class HermesScriptEngine(
    private val deviceController: HermesDeviceController
) {
    /**
     * Executes a Hermes Automation Script line-by-line / DSL evaluation.
     * Supports Hermes DSL & JavaScript-like function calls:
     * - device.toast(msg)
     * - device.vibrate(ms)
     * - device.speak(text)
     * - device.notify(title, message)
     * - device.launchApp(name)
     * - device.openUrl(url)
     * - device.openSettings(type)
     * - device.readClipboard()
     * - device.setClipboard(text)
     * - device.toggleFlashlight(bool)
     * - device.getDeviceStats()
     * - device.httpGet(url)
     * - device.httpPost(url, bodyJson, headersJson)
     * - device.delay(ms)
     * - device.saveFile(name, content)
     */
    suspend fun executeScript(
        code: String,
        inputParams: Map<String, Any?> = emptyMap()
    ): ScriptExecutionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<String>()
        var lastOutput = ""

        logs.add("[Hermes Script Engine] Initializing execution context...")

        try {
            val lines = code.lines()
            val variables = mutableMapOf<String, Any>()
            // Inject input params into variables
            inputParams.forEach { (k, v) ->
                if (v != null) variables[k] = v
            }

            var i = 0
            while (i < lines.size) {
                val rawLine = lines[i].trim()
                i++

                // Skip blank lines & comments
                if (rawLine.isEmpty() || rawLine.startsWith("//") || rawLine.startsWith("/*") || rawLine.startsWith("*")) {
                    continue
                }

                logs.add(">> Line: $rawLine")

                // Variable assignment e.g. const stats = device.getDeviceStats();
                if (rawLine.startsWith("const ") || rawLine.startsWith("let ") || rawLine.startsWith("var ")) {
                    val decl = rawLine.substringAfter(" ").trim()
                    val varName = decl.substringBefore("=").trim()
                    val expr = decl.substringAfter("=").trim().removeSuffix(";")

                    val value = evaluateExpression(expr, variables, logs)
                    if (value != null) {
                        variables[varName] = value
                        logs.add("   Variable '$varName' = $value")
                    }
                    continue
                }

                // Return statement
                if (rawLine.startsWith("return ")) {
                    val expr = rawLine.removePrefix("return ").trim().removeSuffix(";")
                    val result = evaluateExpression(expr, variables, logs)
                    lastOutput = result?.toString() ?: "Execution completed"
                    logs.add("[Hermes Script Engine] Return output: $lastOutput")
                    break
                }

                // Standalone expression / action call
                val evalResult = evaluateExpression(rawLine.removeSuffix(";"), variables, logs)
                if (evalResult != null) {
                    lastOutput = evalResult.toString()
                }
            }

            if (lastOutput.isEmpty()) {
                lastOutput = "Script executed successfully. Variables computed: ${variables.keys.joinToString()}"
            }

            val duration = System.currentTimeMillis() - startTime
            logs.add("[Hermes Script Engine] Finished in ${duration}ms (Status: OK)")

            ScriptExecutionResult(
                success = true,
                logs = logs,
                output = lastOutput,
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val errMsg = e.localizedMessage ?: "Unknown execution error"
            logs.add("[Hermes Script Engine] ERROR: $errMsg")
            ScriptExecutionResult(
                success = false,
                logs = logs,
                output = "Failed: $errMsg",
                durationMs = duration,
                error = errMsg
            )
        }
    }

    private suspend fun evaluateExpression(
        expr: String,
        variables: MutableMap<String, Any>,
        logs: MutableList<String>
    ): Any? {
        val trimmed = expr.trim()

        // 1. device.getDeviceStats()
        if (trimmed.contains("device.getDeviceStats()")) {
            val stats = deviceController.getDeviceStats()
            return mapOf(
                "batteryLevel" to stats.batteryLevel,
                "isCharging" to stats.isCharging,
                "networkType" to stats.networkType,
                "freeStorageMb" to stats.freeStorageMb,
                "totalStorageMb" to stats.totalStorageMb,
                "deviceModel" to stats.deviceModel
            )
        }

        // 2. device.toast(...)
        if (trimmed.startsWith("device.toast(")) {
            val rawArg = extractFunctionArg(trimmed, "device.toast")
            val resolvedArg = resolveStringTemplate(rawArg, variables)
            deviceController.showToast(resolvedArg)
            return "Toast displayed"
        }

        // 3. device.speak(...)
        if (trimmed.startsWith("device.speak(")) {
            val rawArg = extractFunctionArg(trimmed, "device.speak")
            val resolvedArg = resolveStringTemplate(rawArg, variables)
            deviceController.speak(resolvedArg)
            return "Speech synthesized"
        }

        // 4. device.vibrate(...)
        if (trimmed.startsWith("device.vibrate(")) {
            val rawArg = extractFunctionArg(trimmed, "device.vibrate")
            val duration = rawArg.trim().toLongOrNull() ?: 250L
            deviceController.vibrate(duration)
            return "Vibrated ${duration}ms"
        }

        // 5. device.notify(...)
        if (trimmed.startsWith("device.notify(")) {
            val args = extractMultipleArgs(trimmed, "device.notify")
            val title = resolveStringTemplate(args.getOrNull(0) ?: "Hermes Notification", variables)
            val msg = resolveStringTemplate(args.getOrNull(1) ?: "Task executed", variables)
            deviceController.sendNotification(title, msg)
            return "Notification posted"
        }

        // 6. device.launchApp(...)
        if (trimmed.startsWith("device.launchApp(")) {
            val app = resolveStringTemplate(extractFunctionArg(trimmed, "device.launchApp"), variables)
            val res = deviceController.launchApp(app)
            return res.message
        }

        // 7. device.openUrl(...)
        if (trimmed.startsWith("device.openUrl(")) {
            val url = resolveStringTemplate(extractFunctionArg(trimmed, "device.openUrl"), variables)
            val res = deviceController.openUrl(url)
            return res.message
        }

        // 8. device.openSettings(...)
        if (trimmed.startsWith("device.openSettings(")) {
            val type = resolveStringTemplate(extractFunctionArg(trimmed, "device.openSettings"), variables)
            val res = deviceController.openSettings(type)
            return res.message
        }

        // 9. device.readClipboard()
        if (trimmed.contains("device.readClipboard()")) {
            return deviceController.readClipboard()
        }

        // 10. device.setClipboard(...)
        if (trimmed.startsWith("device.setClipboard(")) {
            val text = resolveStringTemplate(extractFunctionArg(trimmed, "device.setClipboard"), variables)
            deviceController.setClipboard(text)
            return "Clipboard updated"
        }

        // 11. device.toggleFlashlight(...)
        if (trimmed.startsWith("device.toggleFlashlight(")) {
            val rawArg = extractFunctionArg(trimmed, "device.toggleFlashlight")
            val state = rawArg.trim().toBooleanStrictOrNull() ?: true
            return deviceController.toggleFlashlight(state).message
        }

        // 12. device.delay(...)
        if (trimmed.startsWith("device.delay(")) {
            val ms = extractFunctionArg(trimmed, "device.delay").trim().toLongOrNull() ?: 500L
            delay(ms)
            return "Delayed ${ms}ms"
        }

        // 13. device.httpGet(url)
        if (trimmed.startsWith("device.httpGet(")) {
            val url = resolveStringTemplate(extractFunctionArg(trimmed, "device.httpGet"), variables)
            val resp = deviceController.httpFetch("GET", url)
            return mapOf(
                "statusCode" to resp.statusCode,
                "isSuccessful" to resp.isSuccessful,
                "body" to resp.body,
                "durationMs" to resp.durationMs
            )
        }

        // 14. device.httpPost(url, body, headers)
        if (trimmed.startsWith("device.httpPost(")) {
            val args = extractMultipleArgs(trimmed, "device.httpPost")
            val url = resolveStringTemplate(args.getOrNull(0) ?: "", variables)
            val body = resolveStringTemplate(args.getOrNull(1) ?: "{}", variables)
            val resp = deviceController.httpFetch("POST", url, body = body)
            return mapOf(
                "statusCode" to resp.statusCode,
                "isSuccessful" to resp.isSuccessful,
                "body" to resp.body,
                "durationMs" to resp.durationMs
            )
        }

        // 15. device.saveFile(name, content)
        if (trimmed.startsWith("device.saveFile(")) {
            val args = extractMultipleArgs(trimmed, "device.saveFile")
            val name = resolveStringTemplate(args.getOrNull(0) ?: "note.txt", variables)
            val content = resolveStringTemplate(args.getOrNull(1) ?: "", variables)
            return deviceController.saveLocalFile(name, content).message
        }

        // 16. device.clickText(text, exactMatch)
        if (trimmed.startsWith("device.clickText(")) {
            val args = extractMultipleArgs(trimmed, "device.clickText")
            val text = resolveStringTemplate(args.getOrNull(0) ?: "", variables)
            val exact = args.getOrNull(1)?.toBooleanStrictOrNull() ?: false
            val res = deviceController.clickScreenText(text, exact)
            logs.add("   [RPA] clickText('$text'): ${res.message}")
            return res.message
        }

        // 17. device.clickId(viewId)
        if (trimmed.startsWith("device.clickId(")) {
            val id = resolveStringTemplate(extractFunctionArg(trimmed, "device.clickId"), variables)
            val res = deviceController.clickScreenId(id)
            logs.add("   [RPA] clickId('$id'): ${res.message}")
            return res.message
        }

        // 18. device.inputText(text)
        if (trimmed.startsWith("device.inputText(")) {
            val text = resolveStringTemplate(extractFunctionArg(trimmed, "device.inputText"), variables)
            val res = deviceController.inputText(text)
            logs.add("   [RPA] inputText('$text'): ${res.message}")
            return res.message
        }

        // 19. device.tap(x, y)
        if (trimmed.startsWith("device.tap(")) {
            val args = extractMultipleArgs(trimmed, "device.tap")
            val x = args.getOrNull(0)?.toFloatOrNull() ?: 500f
            val y = args.getOrNull(1)?.toFloatOrNull() ?: 500f
            val res = deviceController.tapCoordinate(x, y)
            logs.add("   [RPA] tap($x, $y): ${res.message}")
            return res.message
        }

        // 20. device.swipe(startX, startY, endX, endY)
        if (trimmed.startsWith("device.swipe(")) {
            val args = extractMultipleArgs(trimmed, "device.swipe")
            val sx = args.getOrNull(0)?.toFloatOrNull() ?: 500f
            val sy = args.getOrNull(1)?.toFloatOrNull() ?: 1200f
            val ex = args.getOrNull(2)?.toFloatOrNull() ?: 500f
            val ey = args.getOrNull(3)?.toFloatOrNull() ?: 300f
            val res = deviceController.swipeScreen(sx, sy, ex, ey)
            logs.add("   [RPA] swipe: ${res.message}")
            return res.message
        }

        // 21. device.inspectScreen()
        if (trimmed.contains("device.inspectScreen()")) {
            val res = deviceController.inspectScreenNodes()
            return (res.data as? String) ?: res.message
        }

        // 22. device.floatingBubble(enable)
        if (trimmed.startsWith("device.floatingBubble(")) {
            val arg = extractFunctionArg(trimmed, "device.floatingBubble").toBooleanStrictOrNull() ?: true
            return deviceController.toggleFloatingBubble(arg).message
        }

        // Variable lookup or simple literal
        if (variables.containsKey(trimmed)) {
            return variables[trimmed]
        }

        return resolveStringTemplate(trimmed, variables)
    }

    private fun extractFunctionArg(expr: String, fnName: String): String {
        val start = expr.indexOf("$fnName(") + fnName.length + 1
        val end = expr.lastIndexOf(")")
        if (start in 0..end) {
            return expr.substring(start, end).trim()
        }
        return ""
    }

    private fun extractMultipleArgs(expr: String, fnName: String): List<String> {
        val inner = extractFunctionArg(expr, fnName)
        if (inner.isEmpty()) return emptyList()
        // Simple comma split respecting quoted strings
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (ch in inner) {
            if ((ch == '"' || ch == '\'' || ch == '`') && (current.isEmpty() || current.last() != '\\')) {
                if (inQuotes && ch == quoteChar) {
                    inQuotes = false
                } else if (!inQuotes) {
                    inQuotes = true
                    quoteChar = ch
                }
                current.append(ch)
            } else if (ch == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result
    }

    private fun resolveStringTemplate(template: String, variables: Map<String, Any>): String {
        var str = template.trim()
        // Strip outer quotes if any
        if ((str.startsWith("\"") && str.endsWith("\"")) ||
            (str.startsWith("'") && str.endsWith("'")) ||
            (str.startsWith("`") && str.endsWith("`"))) {
            str = str.substring(1, str.length - 1)
        }

        // Replace template interpolation e.g. ${stats.batteryLevel} or ${varName}
        val regex = Regex("\\$\\{([a-zA-Z0-9_.]+)\\}")
        str = regex.replace(str) { matchResult ->
            val key = matchResult.groupValues[1]
            if (key.contains(".")) {
                val parent = key.substringBefore(".")
                val prop = key.substringAfter(".")
                val parentObj = variables[parent]
                if (parentObj is Map<*, *>) {
                    parentObj[prop]?.toString() ?: ""
                } else {
                    ""
                }
            } else {
                variables[key]?.toString() ?: ""
            }
        }

        return str
    }

    fun hotReload() {
        // Clear cached execution contexts and reload runtime hooks
    }
}
