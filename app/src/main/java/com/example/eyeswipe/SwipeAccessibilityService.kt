package com.example.eyeswipe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

private const val INSTAGRAM_PACKAGE = "com.instagram.android"

class SwipeAccessibilityService : AccessibilityService() {

    private val scopeJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + scopeJob)

    private var instagramInForeground = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            GazeEventBus.events.collect { gesture ->
                if (instagramInForeground) handleGesture(gesture)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            instagramInForeground = event.packageName?.toString() == INSTAGRAM_PACKAGE
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun handleGesture(gesture: GazeGesture) {
        when (gesture) {
            GazeGesture.LOOK_DOWN -> performSwipe(upward = true)   // reels: swipe up = next
            GazeGesture.LOOK_UP -> performSwipe(upward = false)    // swipe down = previous
            GazeGesture.LONG_BLINK -> performTap()                 // e.g. double-tap-to-like proxy: single tap here
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun performSwipe(upward: Boolean) {
        val (width, height) = screenSize()
        val centerX = width / 2f
        val startY = if (upward) height * 0.75f else height * 0.25f
        val endY = if (upward) height * 0.25f else height * 0.75f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun performTap() {
        val (width, height) = screenSize()
        val path = Path().apply {
            moveTo(width / 2f, height / 2f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        scopeJob.cancel()
    }
}
