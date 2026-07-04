package com.example.eyeswipe

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import com.example.eyeswipe.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisUseCase: ImageAnalysis? = null

    private var tracking = false
    private var calibrating = false
    private var neutralPitch = 0f
    private val calibrationSamples = mutableListOf<Float>()

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, R.string.grant_camera, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnCalibrate.setOnClickListener { runCalibration() }

        binding.btnToggleTracking.setOnClickListener {
            tracking = !tracking
            binding.btnToggleTracking.setText(
                if (tracking) R.string.stop_tracking else R.string.start_tracking
            )
            binding.statusText.text = if (tracking) "Tracking..." else "Paused"
        }

        requestCameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindUseCases()
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(
                    cameraExecutor,
                    GazeAnalyzer(
                        onCalibrationSample = { pitch -> if (calibrating) calibrationSamples.add(pitch) },
                        getNeutralPitch = { neutralPitch },
                        onGesture = { gesture ->
                            if (tracking) {
                                GazeEventBus.publish(gesture)
                                runOnUiThread { binding.statusText.text = "Gesture: $gesture" }
                            }
                        }
                    )
                )
            }

        val selector = CameraSelector.DEFAULT_FRONT_CAMERA

        provider.unbindAll()
        provider.bindToLifecycle(this, selector, preview, analysisUseCase)
    }

    private fun runCalibration() {
        calibrationSamples.clear()
        calibrating = true
        binding.statusText.text = "Calibrating... look straight at the screen"
        binding.root.postDelayed({
            calibrating = false
            if (calibrationSamples.isNotEmpty()) {
                neutralPitch = calibrationSamples.average().toFloat()
                binding.statusText.text = "Calibrated (neutral pitch = %.1f°)".format(neutralPitch)
            } else {
                binding.statusText.text = "Calibration failed, face not detected"
            }
        }, 2000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
