package com.example.eyeswipe

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Type of gaze/head gesture the analyzer detected. */
enum class GazeGesture {
    LOOK_DOWN,   // head tilted down -> swipe to next reel
    LOOK_UP,     // head tilted up -> swipe to previous reel
    LONG_BLINK   // deliberate long blink -> alternative trigger (e.g. like/pause)
}

/**
 * Simple process-wide event bus. MainActivity's camera analyzer publishes gaze
 * gestures here; SwipeAccessibilityService (running in the same process) subscribes
 * and turns them into real swipe gestures on screen.
 */
object GazeEventBus {
    private val _events = MutableSharedFlow<GazeGesture>(extraBufferCapacity = 4)
    val events: SharedFlow<GazeGesture> = _events.asSharedFlow()

    fun publish(gesture: GazeGesture) {
        _events.tryEmit(gesture)
    }
}
