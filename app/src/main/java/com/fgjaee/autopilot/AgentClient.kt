package com.fgjaee.autopilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One-shot tool-call loop. The model does NOT act unless DeviceTools confirms execution. */
class AgentClient(context: Context, private val device: DeviceTools) {
    private val provider = ProviderSettings(context)

    data class Step(val tool: String, val args: JSONObject, val success: Boolean)
    data class RunResult(val response: String, val steps: List<Step>, val transcript: String)

    fun run(prompt: String): RunResult {
        require(prompt.isNotBlank()) { "Prompt is empty" }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val steps = ArrayList<Step>()
        val transcript = StringBuilder()
        repeat(12) { round ->
            val request = JSONObject()
                .put("model", provider.model)
                .put("messages", messages)
                .put("tools", toolSpecs())
                .put("tool_choice", "auto")
                
            val payload = post(request)
            val choices = payload.optJSONArray("choices")
                ?: error("Provider did not return Chat Completions choices: ${payload.toString().take(400)}")
            if (choices.length() == 0) error("AI provider returned no choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                val answer = message.optString("content").ifBlank {
                    "Model returned no tool calls or usable response."
                }
                return RunResult(answer, steps, transcript.toString())
            }
            messages.put(message)
            for (i in 0 until toolCalls.length()) {
                if (steps.size >= 20) return RunResult("Stopped at 20 tool calls.", steps, transcript.toString())
                val call = toolCalls.getJSONObject(i)
                val function = call.getJSONObject("function")
                val name = function.getString("name")
                val arguments = runCatching { JSONObject(function.optString("arguments", "{}")) }
                    .getOrElse { JSONObject().put("_parse_error", it.message) }
                val result = if (arguments.has("_parse_error"))
                    JSONObject().put("ok", false).put("error", "Malformed tool JSON")
                else device.execute(name, arguments)
                steps.add(Step(name, arguments, result.optBoolean("ok", false)))
                transcript.append("#").append(round + 1).append(" ").append(name)
                    .append(" -> ").append(result.toString().take(700)).append("\n")
                messages.put(JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", call.getString("id"))
                    .put("content", result.toString()))
            }
        }
        return RunResult("Stopped after 12 reasoning rounds.", steps, transcript.toString())
    }

    private fun post(request: JSONObject): JSONObject {
        val endpoint = provider.endpoint.trimEnd('/')
        val url = if (endpoint.endsWith("/chat/completions")) endpoint else "$endpoint/chat/completions"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val key = provider.apiKey()
            if (key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $key")
            conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (status !in 200..299) error("Provider HTTP $status: ${body.take(450)}")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun toolSpecs(): JSONArray {
        val tools = JSONArray()
        fun add(name: String, description: String, fields: JSONObject = JSONObject(), required: JSONArray = JSONArray()) {
            val params = JSONObject().put("type", "object").put("properties", fields).put("required", required)
            tools.put(JSONObject().put("type", "function").put("function",
                JSONObject().put("name", name).put("description", description).put("parameters", params)))
        }
        fun str(description: String) = JSONObject().put("type", "string").put("description", description)
        fun num(description: String) = JSONObject().put("type", "integer").put("description", description)
        fun fields(vararg entries: Pair<String, JSONObject>): JSONObject = JSONObject().apply {
            entries.forEach { (key, value) -> put(key, value) }
        }
        fun required(vararg names: String) = JSONArray().apply { names.forEach { put(it) } }
        add("accessibility_status", "Check if Android Accessibility control is connected")
        add("inspect_ui", "Read currently visible Android accessibility UI nodes, their text and indices. Call before tapping; indices change when screens change.")
        add("launch_app", "Launch an installed Android app by its package identifier",
            fields("package" to str("Installed Android package, e.g. com.android.settings")), required("package"))
        add("tap_element", "Click a UI node by index from the most recent inspect_ui result",
            fields("index" to num("Node index")), required("index"))
        add("tap_text", "Click a visible control matching text or accessibility description",
            fields("text" to str("Visible label")), required("text"))
        add("tap_coordinates", "Tap screen coordinates when node tapping is unavailable",
            fields("x" to num("X pixel"), "y" to num("Y pixel")), required("x", "y"))
        add("scroll", "Scroll the current Android app up or down",
            fields("direction" to str("up or down")), required("direction"))
        add("type_text", "Replace the content of the focused or first editable UI field",
            fields("text" to str("Text to enter")), required("text"))
        add("global_action", "Perform Android back, home, recents, or notifications action",
            fields("action" to str("back, home, recents, or notifications")), required("action"))
        add("root_shell", "Execute a shell command as root through su; return exit code and stdout/stderr",
            fields("command" to str("Shell command")), required("command"))
        add("battery_status", "Read battery percentage from Android BatteryManager")
        add("wait", "Delay briefly after navigating screens",
            fields("milliseconds" to num("Delay 0..3000 ms")), required("milliseconds"))
        return tools
    }

    companion object {
        private const val SYSTEM_PROMPT = """
You are AutoPilot, an Android agent that can use real device tools.
Execute user tasks by calling provided function tools and inspecting their actual results.
Never claim an app was opened, a button tapped or a task completed unless tools reported success.
Prefer inspect_ui followed by tap_text or tap_element. Use root_shell only when necessary.
Report missing permissions or errors plainly. Stop when the task is complete.
Tool calls run on the user's Android device, not on a simulated phone.
"""
    }
}
