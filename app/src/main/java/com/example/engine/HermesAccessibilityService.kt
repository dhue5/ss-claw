package com.example.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HermesAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isAccessibilityConnected.value = true
        _currentActiveApp.value = "Service Connected"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != currentActiveApp.value && packageName != applicationContext.packageName) {
            _currentActiveApp.value = packageName
        }
    }

    override fun onInterrupt() {
        _isAccessibilityConnected.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isAccessibilityConnected.value = false
    }

    // --- RPA SCREEN ACTIONS ---

    fun clickByText(text: String, exactMatch: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) return false

        for (node in nodes) {
            val nodeText = node.text?.toString() ?: ""
            if (!exactMatch || nodeText.equals(text, ignoreCase = true)) {
                if (performNodeClick(node)) return true
            }
        }
        return false
    }

    fun clickById(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) return false
        for (node in nodes) {
            if (performNodeClick(node)) return true
        }
        return false
    }

    fun inputByTextOrFocus(inputText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText)
            }
            return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    private fun performNodeClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        // Fallback: Click center coordinates if gesture available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                return performTapCoordinate(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
        }
        return false
    }

    fun performTapCoordinate(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun performSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun dumpScreenHierarchy(): List<String> {
        val root = rootInActiveWindow ?: return listOf("No active window accessible")
        val list = mutableListOf<String>()
        traverseNode(root, 0, list)
        return list
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, depth: Int, result: MutableList<String>) {
        if (node == null || result.size > 100) return
        val indent = "  ".repeat(depth)
        val text = node.text?.toString()?.take(40) ?: ""
        val desc = node.contentDescription?.toString()?.take(40) ?: ""
        val id = node.viewIdResourceName?.take(40) ?: ""
        val className = node.className?.toString()?.substringAfterLast(".") ?: "View"

        val info = buildString {
            append("$indent[$className]")
            if (id.isNotBlank()) append(" id=$id")
            if (text.isNotBlank()) append(" text=\"$text\"")
            if (desc.isNotBlank()) append(" desc=\"$desc\"")
            if (node.isClickable) append(" [clickable]")
            if (node.isEditable) append(" [editable]")
        }
        if (info.trim().length > className.length + 2) {
            result.add(info)
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), depth + 1, result)
        }
    }

    companion object {
        var instance: HermesAccessibilityService? = null
            private set

        private val _isAccessibilityConnected = MutableStateFlow(false)
        val isAccessibilityConnected = _isAccessibilityConnected.asStateFlow()

        private val _currentActiveApp = MutableStateFlow<String>("None")
        val currentActiveApp = _currentActiveApp.asStateFlow()

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${HermesAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                if (componentNameString.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback
            }
        }
    }
}
