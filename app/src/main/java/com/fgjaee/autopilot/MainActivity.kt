package com.fgjaee.autopilot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val meijerExportCode = 4217
    private val pool = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var apiKey: EditText
    private lateinit var prompt: EditText
    private lateinit var settings: ProviderSettings
    private lateinit var tools: DeviceTools
    private var lastAgentRun: AgentClient.RunResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = ProviderSettings(this)
        tools = DeviceTools(this)
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 48)
        }
        scroll.addView(column)
        setContentView(scroll)

        fun heading(value: String) = TextView(this).apply {
            text = value
            textSize = 22f
            setPadding(0, 22, 0, 12)
            column.addView(this)
        }
        fun field(hint: String, initial: String, lines: Int = 1) = EditText(this).apply {
            setText(initial)
            this.hint = hint
            minLines = lines
            maxLines = if (lines == 1) 1 else 6
            setSingleLine(lines == 1)
            column.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        fun button(label: String, action: () -> Unit) {
            column.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        heading("AutoPilot · Android automation")
        status = TextView(this).apply { textSize = 16f; column.addView(this) }
        heading("Meijer · Installed Android app")
        val meijerInfo = MeijerCapture.info(this)
        TextView(this).apply {
            text = if (meijerInfo.optBoolean("installed"))
                "Meijer installed · ${meijerInfo.optString("version_name")} · ${meijerInfo.optInt("split_count")} split APKs"
            else "Meijer is not installed on this phone"
            textSize = 15f
            column.addView(this)
        }
        button("Read last Meijer clip request trace · root") {
            runAsync("Meijer network trace") {
                val result = tools.execute("root_shell", JSONObject().put(
                    "command", "logcat -d -v brief -s AutoPilotMeijer:I | tail -n 80"))
                result.toString(2)
            }
        }
        TextView(this).apply {
            text = "For network tracing: install this APK, enable its LSPosed module scoped ONLY to Meijer, force-stop Meijer, reopen, and manually clip ONE coupon. AutoPilot reads the request method/path/JSON key names/HTTP status only."
            textSize = 12f
            setPadding(2, 8, 2, 14)
            column.addView(this)
        }
        button("Open Meijer app") {
            runAsync("Meijer") {
                tools.execute("launch_app", JSONObject().put("package", MeijerCapture.PACKAGE)).toString(2)
            }
        }
        button("Start recording Meijer UI interactions") {
            MeijerCapture.begin(this)
            show("Recording Meijer interactions. Switch to Meijer, clip ONE coupon manually, return here, then Stop recording. Only Meijer app events are captured.")
        }
        button("Stop Meijer recording") {
            MeijerCapture.finish(this)
            show("Meijer recording stopped. Export the APK + interaction bundle for analysis.")
        }
        button("View last Meijer capture status") {
            val trace = java.io.File(filesDir, "meijer-interactions.jsonl")
            val screenshot = java.io.File(filesDir, "meijer-after.png")
            show("Captured events: ${if (trace.exists()) trace.readLines().size else 0} · Screenshot captured: ${screenshot.exists()}. Export the bundle to analyze actual Meijer controls.")
        }
        button("Export installed Meijer APKs + UI trace") {
            if (!MeijerCapture.info(this).optBoolean("installed")) {
                show("Meijer is not installed on this device")
            } else {
                val save = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "Meijer-installed-analysis.zip")
                }
                startActivityForResult(save, meijerExportCode)
            }
        }
        TextView(this).apply {
            text = "This captures the installed app package and Meijer-only UI events; it does not clip coupons yet. We'll choose the fastest app-native integration after inspecting the exported APK."
            textSize = 12f
            setPadding(2, 8, 2, 14)
            column.addView(this)
        }

        button("Enable / configure Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Check root access") {
            runAsync("Root check") { tools.execute("root_shell", JSONObject().put("command", "id")).toString(2) }
        }
        button("Open Android Settings") {
            runAsync("Launch app") { tools.execute("launch_app", JSONObject().put("package", "com.android.settings")).toString(2) }
        }
        button("Inspect current screen") {
            runAsync("Screen inspection") { tools.execute("inspect_ui").toString(2) }
        }
        button("Test: launch Settings + inspect there") {
            runAsync("Settings app test") {
                val launch = tools.execute("launch_app", JSONObject().put("package", "com.android.settings"))
                if (!launch.optBoolean("ok")) launch.toString(2)
                else {
                    Thread.sleep(1300)
                    tools.execute("inspect_ui").toString(2)
                }
            }
        }
        button("Battery percentage") {
            runAsync("Battery") { tools.execute("battery_status").toString(2) }
        }

        heading("Other app integrations")
        TextView(this).apply {
            text = "Achievers · Workday · SmartSense — adapters will share this native Android engine. No app-specific actions are enabled until verified."
            textSize = 14f
            column.addView(this)
        }

        heading("AI provider · OpenAI-compatible tool calling")
        endpoint = field("Base URL", settings.endpoint)
        model = field("Model", settings.model)
        apiKey = field("API key (leave blank to preserve saved key / use local host)", "")
        apiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        button("Save provider settings") { saveProvider() }
        button("Clear saved API key") {
            settings.clearApiKey()
            apiKey.text.clear()
            show("Saved provider API key cleared")
        }
        prompt = field("Tell AutoPilot what to do", "Open Android Settings, inspect the screen and report what you see. Don't change settings.", 3)
        button("Run with AI") {
            if (!saveProvider()) return@button
            val instruction = prompt.text.toString()
            runAsync("AI agent") {
                val result = AgentClient(this, tools).run(instruction)
                lastAgentRun = result
                "${result.response}\n\nExecuted tool calls:\n${result.transcript}"
            }
        }

        heading("Reusable workflows")
        button("Save last AI tool sequence") {
            val result = lastAgentRun
            if (result == null || result.steps.isEmpty()) {
                show("No AI tool sequence to save")
            } else {
                runAsync("Save workflow") {
                    WorkflowStore(this).save(result.steps)
                    "Saved ${result.steps.size} actions. Replay uses recorded arguments, not AI."
                }
            }
        }
        button("Replay saved workflow now") {
            runAsync("Workflow replay") { WorkflowRunner(this).run().log }
        }
        button("Schedule saved workflow daily around 6 AM") {
            runAsync("Scheduler") {
                require(WorkflowStore(this).load().length() > 0) { "Save a workflow first" }
                WorkflowScheduler.scheduleDailyAt6(this)
                "Daily workflow scheduled (approximate 6 AM; Android may defer background work)."
            }
        }
        button("Cancel daily workflow") {
            WorkflowScheduler.cancel(this)
            show("Daily schedule cancelled")
        }
        button("Show last background workflow result") {
            show(WorkflowStore(this).lastRun())
        }

        heading("Output")
        output = TextView(this).apply {
            text = "Ready. Enable Accessibility, then run the screen inspection test."
            setTextIsSelectable(true)
            textSize = 13f
            column.addView(this)
        }
        refreshStatus()
    }

    @Deprecated("Android activity-result API retained for min-dependency build")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == meijerExportCode && resultCode == RESULT_OK) {
            val destination: Uri = data?.data ?: return show("No export destination selected")
            runAsync("Export installed Meijer") {
                MeijerCapture.exportBundle(this, destination)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun refreshStatus() {
        status.text = "Accessibility: ${if (AutoPilotAccessibilityService.active != null) "connected" else "not connected"} · tool executor ready"
    }

    private fun saveProvider(): Boolean {
        return try {
            settings.save(endpoint.text.toString(), model.text.toString(), apiKey.text.toString())
            apiKey.text.clear()
            show("Provider settings saved. Key stored encrypted if supplied.")
            true
        } catch (e: Exception) {
            show("Provider settings error: ${e.message}")
            false
        }
    }

    private fun runAsync(label: String, task: () -> String) {
        show("$label: running...")
        pool.execute {
            val result = runCatching(task).getOrElse { "${it.javaClass.simpleName}: ${it.message}" }
            runOnUiThread { show("$label\n$result") }
        }
    }

    private fun show(text: String) {
        if (::output.isInitialized) output.text = text.take(20000)
    }

    override fun onDestroy() {
        pool.shutdownNow()
        super.onDestroy()
    }
}
