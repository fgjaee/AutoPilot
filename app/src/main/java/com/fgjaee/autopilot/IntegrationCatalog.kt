package com.fgjaee.autopilot

import android.content.Context

/** Discovery only; no hooks are automatically installed in new apps. */
object IntegrationCatalog {
    private data class Entry(
        val name: String,
        val label: String,
        val knownPackage: String? = null,
        val implemented: Boolean = false
    )
    private val targets = listOf(
        Entry("Meijer · mPerks", "Meijer", "com.meijer.mobile.meijer", true),
        Entry("Achievers", "Achievers"),
        Entry("Workday", "Workday"),
        Entry("SmartSense", "SmartSense")
    )

    fun report(context: Context): String {
        val pm = context.packageManager
        val installed = runCatching {
            pm.getInstalledApplications(0).map { app ->
                val name = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault("")
                name to app.packageName
            }
        }.getOrDefault(emptyList())

        val lines = mutableListOf(
            "AutoPilot: single native app + shared LSPosed bridge",
            "New automation = saved workflow; not a new module.",
            "New target app = one scope selection when its adapter is ready.",
            ""
        )
        for (target in targets) {
            val matching = installed.filter { (name, pkg) ->
                pkg == target.knownPackage || name.contains(target.label, ignoreCase = true)
            }.take(4)
            val state = if (target.implemented) "Meijer trace adapter active in code" else "Adapter pending (no hooks)"
            lines.add("${target.name}: $state")
            lines.add(if (matching.isEmpty()) "  Not detected"
                else matching.joinToString(" | ") { (name, pkg) -> "  $name [$pkg]" })
        }
        return lines.joinToString("\n")
    }
}
