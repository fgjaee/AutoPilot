package com.fgjaee.autopilot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper

/** The only component that touches another app's accessibility tree. */
class AutoPilotAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var active: AutoPilotAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    private val captureHandler = Handler(Looper.getMainLooper())
    private var initialCaptureTaken = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        MeijerCapture.observe(this, event)
        if (!MeijerCapture.enabled(this)) {
            initialCaptureTaken = false
            return
        }
        if (event.packageName?.toString() != MeijerCapture.PACKAGE) return
        val tapped = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        val firstWindow = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !initialCaptureTaken
        if (firstWindow) initialCaptureTaken = true
        if (tapped || firstWindow) {
            val captureName = if (tapped) "meijer-after.png" else "meijer-before.png"
            captureHandler.postDelayed({
                if (MeijerCapture.enabled(this)) {
                    MeijerCapture.saveAccessibleSnapshot(this)
                    MeijerCapture.takeEvidenceScreenshot(this, captureName)
                }
            }, if (tapped) 600 else 800)
        }
    }
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }
}
