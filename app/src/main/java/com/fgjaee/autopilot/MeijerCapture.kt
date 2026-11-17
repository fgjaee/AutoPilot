package com.fgjaee.autopilot

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.io.IOException
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.view.Display
import android.os.Build
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * App-scoped instrumentation: captures the actual Meijer Android app's visible
 * accessibility controls and user interaction events. It does not authenticate,
 * hook methods, send network traffic, or clip coupons on its own.
 */
object MeijerCapture {
    const val PACKAGE = "com.meijer.mobile.meijer"
    private const val PREFS = "meijer_capture"
    private const val MAX_EVENTS = 160

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun logFile(ctx: Context) = File(ctx.filesDir, "meijer-interactions.jsonl")

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)

    fun begin(ctx: Context) {
        logFile(ctx).writeText("")
        listOf("meijer-before.png", "meijer-after.png", "meijer-ui-snapshot.json", "meijer-screenshot-error.txt")
            .forEach { File(ctx.filesDir, it).delete() }
        prefs(ctx).edit().putBoolean("enabled", true).putInt("events", 0).apply()
    }

    fun finish(ctx: Context) = prefs(ctx).edit().putBoolean("enabled", false).apply()

    /** Called only by our AccessibilityService, always on its main event thread. */
    fun observe(ctx: Context, event: AccessibilityEvent) {
        if (!enabled(ctx) || event.packageName?.toString() != PACKAGE) return
        val count = prefs(ctx).getInt("events", 0)
        if (count >= MAX_EVENTS) { finish(ctx); return }
        // Capture important transitions, not every text/scroll update in a webview.
        if (event.eventType !in setOf(
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            )) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && count % 4 != 0) return
        val source = event.source
        val bounds = Rect().also { source?.getBoundsInScreen(it) }
        val record = JSONObject()
            .put("time_ms", System.currentTimeMillis())
            .put("type", AccessibilityEvent.eventTypeToString(event.eventType))
            .put("class", event.className?.toString() ?: "")
            .put("view_id", source?.viewIdResourceName ?: "")
            .put("bounds", JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom))
            .put("clickable", source?.isClickable ?: false)
            .put("text", event.text.joinToString(" ").take(160))
            .put("description", source?.contentDescription?.toString()?.take(160) ?: "")
        synchronized(this) {
            logFile(ctx).appendText(record.toString() + "\n")
            prefs(ctx).edit().putInt("events", count + 1).apply()
        }
    }

    fun currentSnapshot(service: AccessibilityService?): JSONObject {
        if (service == null) return JSONObject().put("ok", false).put("reason", "Accessibility not enabled")
        val root = service.rootInActiveWindow
            ?: return JSONObject().put("ok", false).put("reason", "No active accessible UI tree")
        val actual = root.packageName?.toString() ?: ""
        if (actual != PACKAGE) return JSONObject().put("ok", false)
            .put("reason", "Meijer is not the active accessible window")
            .put("foreground", actual)
        val nodes = JSONArray()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25 || nodes.length() >= 240) return
            val r = Rect().also(n::getBoundsInScreen)
            nodes.put(JSONObject()
                .put("class", n.className?.toString() ?: "")
                .put("text", n.text?.toString()?.take(150) ?: "")
                .put("description", n.contentDescription?.toString()?.take(150) ?: "")
                .put("view_id", n.viewIdResourceName ?: "")
                .put("clickable", n.isClickable)
                .put("scrollable", n.isScrollable)
                .put("bounds", JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom)))
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return JSONObject().put("ok", true).put("package", actual)
            .put("count", nodes.length()).put("nodes", nodes)
    }

    /** Save a snapshot while Meijer, rather than AutoPilot, is in the foreground. */
    fun saveAccessibleSnapshot(service: AccessibilityService) {
        if (!enabled(service)) return
        val snapshot = currentSnapshot(service)
        File(service.filesDir, "meijer-ui-snapshot.json").writeText(snapshot.toString(2))
    }

    /** Accessibility screenshot is independent of uiautomator XML inspection. */
    fun takeEvidenceScreenshot(service: AccessibilityService, fileName: String) {
        if (!enabled(service) || Build.VERSION.SDK_INT < 30) return
        service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(
                            buffer, result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        software?.let { bitmap ->
                            FileOutputStream(File(service.filesDir, fileName)).use { file ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, file)
                            }
                            bitmap.recycle()
                        }
                    } catch (_: Exception) {
                        // APK and event trace remain available even if screenshot capture fails.
                    } finally {
                        buffer.close()
                    }
                }
                override fun onFailure(errorCode: Int) {
                    File(service.filesDir, "meijer-screenshot-error.txt")
                        .writeText("Accessibility screenshot error code: $errorCode")
                }
            })
    }

    fun info(ctx: Context): JSONObject {
        val pm = ctx.packageManager
        val data = JSONObject().put("package", PACKAGE)
        try {
            val pkg = pm.getPackageInfo(PACKAGE, 0)
            val ai = pm.getApplicationInfo(PACKAGE, 0)
            data.put("installed", true).put("version_name", pkg.versionName ?: "")
                .put("version_code", pkg.longVersionCode)
                .put("split_count", ai.splitSourceDirs?.size ?: 0)
        } catch (e: PackageManager.NameNotFoundException) {
            data.put("installed", false)
        }
        return data
    }

    /**
     * Exports installed APK archives + locally recorded Meijer UI events.
     * No account databases, cookies or app-private files are included.
     * Use Android's CREATE_DOCUMENT picker; nothing is sent anywhere.
     */
    fun exportBundle(ctx: Context, uri: Uri): String {
        val pm = ctx.packageManager
        val ai = pm.getApplicationInfo(PACKAGE, 0)
        val apkPaths = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        if (apkPaths.isEmpty()) throw IOException("No installed Meijer APK files found")
        val output: OutputStream = ctx.contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open selected export destination")
        var archived = 0
        ZipOutputStream(output.buffered()).use { zip ->
            zip.setLevel(0) // APK files are already compressed; avoid CPU-heavy recompression
            fun putText(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            putText("meijer-package.json", info(ctx).toString(2))
            val trace = logFile(ctx)
            if (trace.exists()) putText("meijer-interactions.jsonl", trace.readText())
            val snapshot = File(ctx.filesDir, "meijer-ui-snapshot.json")
            if (snapshot.exists()) putText("meijer-ui-snapshot.json", snapshot.readText())
            val screenshotError = File(ctx.filesDir, "meijer-screenshot-error.txt")
            if (screenshotError.exists()) putText("meijer-screenshot-error.txt", screenshotError.readText())
            for (name in listOf("meijer-before.png", "meijer-after.png")) {
                val picture = File(ctx.filesDir, name)
                if (picture.exists()) {
                    zip.putNextEntry(ZipEntry(name))
                    picture.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            putText("export-notes.txt", "Only installed APK files and Meijer Accessibility events. No account data or credentials.\n")
            apkPaths.forEachIndexed { index, source ->
                val target = if (index == 0) "apks/base.apk" else "apks/split-${index}-${File(source).name}"
                // Android normally permits reading installed APK files. Report if a ROM restricts it.
                val stream = try {
                    FileInputStream(source)
                } catch (_: Exception) {
                    openApkThroughRoot(ctx, source, index)
                }
                stream.use { input ->
                    zip.putNextEntry(ZipEntry(target))
                    input.copyTo(zip, 1024 * 128)
                    zip.closeEntry()
                    archived++
                }
            }
        }
        return "Exported $archived APK archive(s) and Meijer interaction trace to the selected file."
    }
    private fun shellQuote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun openApkThroughRoot(ctx: Context, path: String, index: Int): FileInputStream {
        val temp = File(ctx.cacheDir, "meijer-installed-$index.apk")
        val cmd = "cp ${shellQuote(path)} ${shellQuote(temp.absolutePath)} && chmod 0644 ${shellQuote(temp.absolutePath)}"
        val process = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText().take(500) }
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0)
            throw IOException("Could not copy installed APK via root: $stdout")
        temp.deleteOnExit()
        return FileInputStream(temp)
    }

}
