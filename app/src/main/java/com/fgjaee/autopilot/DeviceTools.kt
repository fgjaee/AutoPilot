package com.fgjaee.autopilot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Synchronous tool execution for background worker / AI request threads.
 * UI tree access is marshalled onto the Android main thread.
 * Never call execute() from the main thread for a gesture or shell command.
 */
class DeviceTools(private val context: Context) {
    companion object {
        val supported = setOf(
            "accessibility_status", "inspect_ui", "launch_app", "tap_element", "tap_text",
            "tap_coordinates", "scroll", "type_text", "global_action", "root_shell",
            "battery_status", "wait"
        )
    }

    fun execute(tool: String, args: JSONObject = JSONObject()): JSONObject {
        if (tool !in supported) return err("Unknown tool: $tool")
        return try {
            when (tool) {
                "accessibility_status" -> ok("connected", AutoPilotAccessibilityService.active != null)
                "inspect_ui" -> mainCall { inspect() }
                "launch_app" -> mainCall { launch(args.getString("package")) }
                "tap_element" -> tapElement(args.getInt("index"))
                "tap_text" -> tapText(args.getString("text"))
                "tap_coordinates" -> tapCoordinates(args.getInt("x"), args.getInt("y"))
                "scroll" -> mainCall { scroll(args.optString("direction", "down")) }
                "type_text" -> mainCall { typeText(args.getString("text")) }
                "global_action" -> mainCall { globalAction(args.getString("action")) }
                "root_shell" -> rootShell(args.getString("command"))
                "battery_status" -> batteryStatus()
                "wait" -> {
                    val delay = args.optLong("milliseconds", 500).coerceIn(0, 3000)
                    Thread.sleep(delay)
                    ok("waited_ms", delay)
                }
                else -> err("Unsupported tool")
            }
        } catch (e: Exception) {
            err("${e.javaClass.simpleName}: ${e.message ?: "failed"}")
        }
    }

    private fun inspect(): JSONObject {
        val root = service().rootInActiveWindow ?: return err("No active window. Unlock the phone and open an app.")
        val entries = JSONArray()
        flatten(root).take(120).forEachIndexed { index, node ->
            val r = Rect().also(node::getBoundsInScreen)
            val item = JSONObject()
                .put("index", index)
                .put("class", node.className?.toString() ?: "")
                .put("text", node.text?.toString()?.take(96) ?: "")
                .put("description", node.contentDescription?.toString()?.take(96) ?: "")
                .put("view_id", node.viewIdResourceName ?: "")
                .put("clickable", node.isClickable)
                .put("editable", node.isEditable)
                .put("scrollable", node.isScrollable)
                .put("bounds", "${r.left},${r.top},${r.right},${r.bottom}")
            entries.put(item)
        }
        return JSONObject().put("ok", true)
            .put("package", root.packageName?.toString() ?: "")
            .put("nodes", entries)
            .put("count", entries.length())
    }

    private fun launch(packageName: String): JSONObject {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "Invalid package name" }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return err("No launch intent for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ok("launched", packageName)
    }

    private fun tapElement(index: Int): JSONObject {
        val outcome = mainCall {
            val root = service().rootInActiveWindow ?: error("No active window")
            val nodes = flatten(root)
            require(index in nodes.indices) { "Element index $index is stale; inspect_ui again" }
            val node = nodes[index]
            val clicked = clickNodeOrParent(node)
            val r = Rect().also(node::getBoundsInScreen)
            Triple(clicked, r.centerX(), r.centerY())
        }
        if (outcome.first) return ok("clicked_index", index)
        return gestureTap(outcome.second, outcome.third)
    }

    private fun tapText(text: String): JSONObject {
        require(text.isNotBlank()) { "text is empty" }
        val outcome = mainCall {
            val root = service().rootInActiveWindow ?: error("No active window")
            val node = flatten(root).firstOrNull {
                it.text?.toString()?.contains(text, ignoreCase = true) == true ||
                it.contentDescription?.toString()?.contains(text, ignoreCase = true) == true
            } ?: error("Text not found: $text")
            val clicked = clickNodeOrParent(node)
            val rect = Rect().also(node::getBoundsInScreen)
            Triple(clicked, rect.centerX(), rect.centerY())
        }
        if (outcome.first) return ok("clicked_text", text)
        return gestureTap(outcome.second, outcome.third)
    }

    private fun tapCoordinates(x: Int, y: Int): JSONObject {
        require(x >= 0 && y >= 0) { "Coordinates must be nonnegative" }
        return gestureTap(x, y)
    }

    private fun gestureTap(x: Int, y: Int): JSONObject {
        val svc = service()
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val completed = CountDownLatch(1)
        var succeeded = false
        val requested = mainCall {
            svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    succeeded = true
                    completed.countDown()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    completed.countDown()
                }
            }, Handler(Looper.getMainLooper()))
        }
        if (!requested) return err("Android rejected gesture")
        if (!completed.await(5, TimeUnit.SECONDS)) return err("Gesture timeout")
        return if (succeeded) ok("tapped", "$x,$y") else err("Gesture cancelled")
    }

    private fun scroll(direction: String): JSONObject {
        val root = service().rootInActiveWindow ?: return err("No active window")
        val node = flatten(root).firstOrNull { it.isScrollable }
            ?: return err("No scrollable node; inspect_ui and try another screen")
        val action = if (direction.equals("up", true))
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        return if (node.performAction(action)) ok("scrolled", direction) else err("Scroll failed")
    }

    private fun typeText(value: String): JSONObject {
        val root = service().rootInActiveWindow ?: return err("No active window")
        val node = flatten(root).firstOrNull { it.isEditable && it.isFocused }
            ?: flatten(root).firstOrNull { it.isEditable }
            ?: return err("No editable field visible")
        val data = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, data)) ok("typed", true)
            else err("Could not enter text")
    }

    private fun globalAction(value: String): JSONObject {
        val action = when (value.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> return err("Action must be back, home, recents, or notifications")
        }
        return if (service().performGlobalAction(action)) ok("global_action", value)
            else err("Global action was rejected")
    }

    private fun rootShell(command: String): JSONObject {
        require(command.isNotBlank() && command.length <= 4000) { "Invalid shell command length" }
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true).start()
        val readerPool = Executors.newSingleThreadExecutor()
        try {
            val outputTask = readerPool.submit<String> {
                process.inputStream.bufferedReader().use { it.readText().take(16000) }
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return err("Root command timed out after 15 seconds")
            }
            val exitCode = process.exitValue()
            val output = outputTask.get(2, TimeUnit.SECONDS)
            return JSONObject().put("ok", exitCode == 0)
                .put("exit_code", exitCode).put("output", output)
        } finally {
            readerPool.shutdownNow()
            process.destroy()
        }
    }

    private fun batteryStatus(): JSONObject {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return JSONObject().put("ok", pct in 0..100).put("battery_percent", pct)
            .put("android", Build.VERSION.RELEASE)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        repeat(8) {
            val current = target ?: return false
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            target = current.parent
        }
        return false
    }

    private fun flatten(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>(128)
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (result.size >= 180 || depth > 20) return
            result.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return result
    }

    private fun service(): AutoPilotAccessibilityService =
        AutoPilotAccessibilityService.active ?: error("AutoPilot Accessibility is not enabled")

    private fun <T> mainCall(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        Handler(Looper.getMainLooper()).post {
            result = runCatching(action)
            latch.countDown()
        }
        if (!latch.await(8, TimeUnit.SECONDS)) error("Main-thread UI action timed out")
        return result!!.getOrThrow()
    }

    private fun ok(field: String, value: Any): JSONObject = JSONObject().put("ok", true).put(field, value)
    private fun err(message: String): JSONObject = JSONObject().put("ok", false).put("error", message)
}
