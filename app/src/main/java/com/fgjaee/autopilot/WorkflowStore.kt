package com.fgjaee.autopilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** User-approved workflows are saved in private app storage as deterministic tool steps. */
class WorkflowStore(context: Context) {
    private val prefs = context.getSharedPreferences("workflows", Context.MODE_PRIVATE)

    fun save(steps: List<AgentClient.Step>) {
        require(steps.isNotEmpty()) { "No tool calls to save" }
        require(steps.size <= 20) { "Workflow exceeds step limit" }
        require(steps.all { it.success }) { "The AI run had failed tool calls; cannot save as a successful workflow" }
        val data = JSONArray()
        steps.forEach { step ->
            require(step.tool in DeviceTools.supported) { "Unknown tool ${step.tool}" }
            data.put(JSONObject().put("name", step.tool).put("args", step.args))
        }
        prefs.edit().putString("saved", data.toString()).apply()
    }

    fun load(): JSONArray = JSONArray(prefs.getString("saved", "[]"))

    fun setLastRun(report: String) {
        prefs.edit().putString("last_run", report.take(12000)).apply()
    }

    fun lastRun(): String = prefs.getString("last_run", "No workflow runs yet")!!
}

class WorkflowRunner(private val context: Context) {
    data class Report(val successful: Boolean, val log: String)

    fun run(): Report {
        val steps = WorkflowStore(context).load()
        if (steps.length() == 0) return Report(false, "No saved workflow")
        val executor = DeviceTools(context)
        val output = StringBuilder()
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val name = step.getString("name")
            val args = step.optJSONObject("args") ?: JSONObject()
            val result = executor.execute(name, args)
            output.append(i + 1).append(". ").append(name)
                .append(" -> ").append(result.toString().take(900)).append('\n')
            if (!result.optBoolean("ok", false)) {
                val report = Report(false, output.toString())
                WorkflowStore(context).setLastRun(report.log)
                return report
            }
        }
        val report = Report(true, output.toString())
        WorkflowStore(context).setLastRun(report.log)
        return report
    }
}
