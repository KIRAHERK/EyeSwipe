package com.example.eyeswipe

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageAnalysis
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * NOTE ON ACCURACY
 * ------------------------------------------------------------------
 * Genuine pupil-level gaze tracking needs either dedicated IR hardware
 * (e.g. Tobii) or a heavy on-device iris-landmark model. A phone's RGB
 * front camera alone can't reliably resolve where the pupil is pointed.
 *
 * This analyzer instead uses ML Kit's on-device face detector to read:
 *   - headEulerAngleX (pitch: nodding up/down)
 *   - left/right eye-open probability (for blink detection)
 * and treats "tilt head down" / "tilt head up" / "long blink" as the
 * triggers. It's a well-established, much more reliable proxy for
 * hands-free scrolling than raw gaze estimation on commodity phones.
 * ------------------------------------------------------------------
 */
class GazeAnalyzer(
    private val onCalibrationSample: ((pitch: Float) -> Unit)? = null,
    private val getNeutralPitch: () -> Float,
    private val onGesture: (GazeGesture) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // needed for eye-open probability
            .build()
    )

    // Thresholds (degrees / seconds) — tune these after real-world testing.
    private val pitchDownThreshold = 12f   // degrees below neutral counts as "look down"
    private val pitchUpThreshold = 10f     // degrees above neutral counts as "look up"
    private val blinkClosedProb = 0.15f    // below this probability = eye considered closed
    private val longBlinkMillis = 600L     // sustained closed-eyes duration to count as deliberate blink

    private var closedSince = 0L
    private var lastGestureAtMs = 0L
    private val gestureCooldownMs = 900L   // prevents repeated triggers from a single head movement

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces -> faces.firstOrNull()?.let { handleFace(it) } }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleFace(face: Face) {
        val pitch = face.headEulerAngleX // positive = looking up, negative = looking down (device dependent)
        onCalibrationSample?.invoke(pitch)

        val neutral = getNeutralPitch()
        val delta = pitch - neutral
        val now = System.currentTimeMillis()

        // Blink detection first (independent of head pose)
        val leftOpen = face.leftEyeOpenProbability ?: 1f
        val rightOpen = face.rightEyeOpenProbability ?: 1f
        val eyesClosed = leftOpen < blinkClosedProb && rightOpen < blinkClosedProb

        if (eyesClosed) {
            if (closedSince == 0L) closedSince = now
            if (now - closedSince >= longBlinkMillis && now - lastGestureAtMs > gestureCooldownMs) {
                fire(GazeGesture.LONG_BLINK, now)
            }
            return
        } else {
            closedSince = 0L
        }

        if (now - lastGestureAtMs <= gestureCooldownMs) return

        when {
            delta <= -pitchDownThreshold -> fire(GazeGesture.LOOK_DOWN, now)
            delta >= pitchUpThreshold -> fire(GazeGesture.LOOK_UP, now)
        }
    }

    private fun fire(gesture: GazeGesture, now: Long) {
        lastGestureAtMs = now
        onGesture(gesture)
    }
}
