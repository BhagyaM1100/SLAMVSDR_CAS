package com.example.slamvsdr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraSlamActivity : AppCompatActivity() {

    // CameraX
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null

    // UI Elements
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: ImageView
    private lateinit var positionText: TextView
    private lateinit var trackingText: TextView
    private lateinit var landmarksText: TextView
    private lateinit var fpsText: TextView
    private lateinit var toggleCameraBtn: Button
    private lateinit var resetSlamBtn: Button
    private lateinit var backBtn: Button
    private lateinit var requestPermissionBtn: Button
    private lateinit var openSettingsBtn: Button
    private lateinit var useSimulatedBtn: Button

    // Visualization controls
    private lateinit var toggleTracksBtn: Button
    private lateinit var toggleArrowBtn: Button
    private lateinit var toggleHistoryBtn: Button

    private lateinit var permissionOverlay: View
    private lateinit var infoPanel: View
    private lateinit var controlPanel: View
    private lateinit var permissionText: TextView

    // Feature Detection
    private lateinit var featureDetector: FeatureDetector
    private var frameCount = 0
    private var lastFrameTime = System.currentTimeMillis()
    private var currentFps = 0
    private var lastFrameProcessingTime = 0L

    // State
    private var isCameraActive = false
    private var isSimulatedMode = false

    // Visualization states
    private var showFeatureTracks = true
    private var showMotionArrow = true
    private var showFeatureHistory = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
        private const val REQUEST_CODE_SETTINGS = 101
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_slam)

        Log.d("CameraSlam", "Activity created")

        // Initialize all views
        initializeViews()

        // Initialize feature detector
        featureDetector = FeatureDetector()

        // Setup camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup button listeners
        setupButtonListeners()

        // Check camera permission
        checkCameraPermission()
    }

    private fun initializeViews() {
        // Camera preview
        previewView = findViewById(R.id.camera_preview)

        // Overlay for features
        overlayView = findViewById(R.id.overlay_view)

        // Info panel
        positionText = findViewById(R.id.position_text)
        trackingText = findViewById(R.id.tracking_text)
        landmarksText = findViewById(R.id.landmarks_text)
        fpsText = findViewById(R.id.fps_text)

        // Control panel
        toggleCameraBtn = findViewById(R.id.toggle_camera_btn)
        resetSlamBtn = findViewById(R.id.reset_slam_btn)
        backBtn = findViewById(R.id.back_btn)

        // Visualization controls
        toggleTracksBtn = findViewById(R.id.toggle_tracks_btn)
        toggleArrowBtn = findViewById(R.id.toggle_arrow_btn)
        toggleHistoryBtn = findViewById(R.id.toggle_history_btn)

        // Permission overlay
        permissionOverlay = findViewById(R.id.permission_overlay)
        permissionText = findViewById(R.id.permission_text)
        requestPermissionBtn = findViewById(R.id.request_permission_btn)
        openSettingsBtn = findViewById(R.id.open_settings_btn)
        useSimulatedBtn = findViewById(R.id.use_simulated_btn)

        // Panels
        infoPanel = findViewById(R.id.info_panel)
        controlPanel = findViewById(R.id.control_panel)
    }

    private fun setupButtonListeners() {
        backBtn.setOnClickListener {
            finish()
        }

        resetSlamBtn.setOnClickListener {
            resetSLAM()
        }

        toggleCameraBtn.setOnClickListener {
            if (isCameraActive) {
                stopCamera()
                toggleCameraBtn.text = "Start Camera"
                toggleCameraBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } else {
                startCamera()
                toggleCameraBtn.text = "Stop Camera"
                toggleCameraBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            isCameraActive = !isCameraActive
        }

        requestPermissionBtn.setOnClickListener {
            requestCameraPermission()
        }

        openSettingsBtn.setOnClickListener {
            openAppSettings()
        }

        useSimulatedBtn.setOnClickListener {
            enableSimulatedMode()
        }

        // Visualization control buttons
        toggleTracksBtn.setOnClickListener {
            showFeatureTracks = !showFeatureTracks
            featureDetector.toggleFeatureTracks(showFeatureTracks)
            toggleTracksBtn.text = if (showFeatureTracks) "Tracks ON" else "Tracks OFF"
            toggleTracksBtn.setBackgroundColor(
                ContextCompat.getColor(this,
                    if (showFeatureTracks) android.R.color.holo_blue_dark else android.R.color.darker_gray
                )
            )
            Toast.makeText(this, if (showFeatureTracks) "Feature tracks enabled" else "Feature tracks disabled",
                Toast.LENGTH_SHORT).show()
        }

        toggleArrowBtn.setOnClickListener {
            showMotionArrow = !showMotionArrow
            featureDetector.toggleMotionArrow(showMotionArrow)
            toggleArrowBtn.text = if (showMotionArrow) "Arrow ON" else "Arrow OFF"
            toggleArrowBtn.setBackgroundColor(
                ContextCompat.getColor(this,
                    if (showMotionArrow) android.R.color.holo_orange_dark else android.R.color.darker_gray
                )
            )
            Toast.makeText(this, if (showMotionArrow) "Motion arrow enabled" else "Motion arrow disabled",
                Toast.LENGTH_SHORT).show()
        }

        toggleHistoryBtn.setOnClickListener {
            showFeatureHistory = !showFeatureHistory
            featureDetector.toggleFeatureHistory(showFeatureHistory)
            toggleHistoryBtn.text = if (showFeatureHistory) "History ON" else "History OFF"
            toggleHistoryBtn.setBackgroundColor(
                ContextCompat.getColor(this,
                    if (showFeatureHistory) android.R.color.holo_purple else android.R.color.darker_gray
                )
            )
            Toast.makeText(this, if (showFeatureHistory) "Feature history enabled" else "Feature history disabled",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCameraPermission() {
        if (allPermissionsGranted()) {
            // Permission granted - show camera UI
            permissionOverlay.visibility = View.GONE
            infoPanel.visibility = View.VISIBLE
            controlPanel.visibility = View.VISIBLE
            previewView.visibility = View.VISIBLE

            if (!isSimulatedMode) {
                startCamera()
            } else {
                startSimulatedMode()
            }
        } else {
            // Permission not granted - show permission overlay
            permissionOverlay.visibility = View.VISIBLE
            infoPanel.visibility = View.GONE
            controlPanel.visibility = View.GONE
            previewView.visibility = View.GONE

            // Update permission text based on situation
            if (shouldShowPermissionRationale()) {
                permissionText.text = "Camera permission is needed for Visual SLAM to work.\n\nPlease grant permission to use the camera."
                openSettingsBtn.visibility = View.GONE
            } else {
                permissionText.text = "Camera permission is required for Visual SLAM.\n\nIf you previously denied permission, please enable it in App Settings."
                openSettingsBtn.visibility = View.VISIBLE
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun shouldShowPermissionRationale(): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.CAMERA
        )
    }

    private fun requestCameraPermission() {
        Log.d("CameraSlam", "Requesting camera permission")
        Toast.makeText(this, "Requesting camera permission...", Toast.LENGTH_SHORT).show()

        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        )
    }

    private fun openAppSettings() {
        Log.d("CameraSlam", "Opening app settings")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, REQUEST_CODE_SETTINGS)
    }

    private fun startCamera() {
        Log.d("CameraSlam", "Starting camera...")
        isSimulatedMode = false

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Setup preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Setup image analysis for feature detection
                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480)) // Lower resolution for speed
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            // Process frame for feature detection
                            processCameraFrame(imageProxy)
                        }
                    }

                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind existing use cases first
                    cameraProvider?.unbindAll()

                    // Bind use cases to camera
                    camera = cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis
                    )

                    isCameraActive = true
                    trackingText.text = "Tracking: Initializing..."
                    trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    overlayView.visibility = View.VISIBLE

                    Toast.makeText(this, "Camera started - detecting features", Toast.LENGTH_SHORT).show()
                    Log.d("CameraSlam", "Camera started successfully")

                } catch (exc: Exception) {
                    Log.e("CameraSlam", "Use case binding failed", exc)
                    Toast.makeText(this, "Failed to start camera: ${exc.message}", Toast.LENGTH_LONG).show()
                    enableSimulatedMode() // Fallback to simulated mode
                }
            } catch (e: Exception) {
                Log.e("CameraSlam", "Failed to get camera provider", e)
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
                enableSimulatedMode() // Fallback to simulated mode
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processCameraFrame(imageProxy: ImageProxy) {
        try {
            frameCount++

            // Calculate FPS
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFrameTime >= 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFrameTime = currentTime
            }

            // Process frame for feature detection
            val startTime = System.currentTimeMillis()
            val detectionResult = featureDetector.detectFeatures(imageProxy)
            lastFrameProcessingTime = System.currentTimeMillis() - startTime

            // Update UI on main thread
            runOnUiThread {
                // SAFE ACCESS: Check if matches exists
                val matchCount = if (detectionResult.matches != null) {
                    detectionResult.matches.size
                } else {
                    0
                }

                updateFeatureDetectionUI(detectionResult, matchCount, imageProxy.width, imageProxy.height)
            }

            // Log for debugging - SAFE VERSION
            if (detectionResult.featureCount > 0) {
                val matchCount = if (detectionResult.matches != null) detectionResult.matches.size else 0
                Log.d("CameraSlam",
                    "Frame: ${detectionResult.featureCount} features, " +
                            "$matchCount matches, " +
                            "time: ${lastFrameProcessingTime}ms"
                )
            }

        } catch (e: Exception) {
            Log.e("CameraSlam", "Error processing frame: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun updateFeatureDetectionUI(result: FeatureDetectionResult, matchCount: Int, width: Int, height: Int) {
        // Update FPS
        fpsText.text = "FPS: $currentFps (${lastFrameProcessingTime}ms)"

        // Update feature info
        landmarksText.text = "Features: ${result.featureCount} | Matches: $matchCount"

        // Calculate tracking quality
        val trackingQuality = if (result.featureCount > 0) {
            matchCount.toFloat() / result.featureCount.coerceAtLeast(1)
        } else {
            0.0f
        }

        // Update tracking status
        val trackingStatus = when {
            trackingQuality > 0.5 -> {
                trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                "Tracking: Good"
            }
            trackingQuality > 0.2 -> {
                trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                "Tracking: Medium"
            }
            else -> {
                trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                "Tracking: Poor"
            }
        }
        trackingText.text = "$trackingStatus (${(trackingQuality * 100).toInt()}%)"

        // USE REAL POSITION FROM FEATURE DETECTOR
        positionText.text = String.format("Position: (%.3f, %.3f, %.2f)",
            result.positionX, result.positionY, result.positionZ)

        // Create and display feature overlay
        if (result.featureCount > 0) {
            val overlayBitmap = featureDetector.createFeatureOverlay(width, height, result.features, result.matches)
            overlayView.setImageBitmap(overlayBitmap)
            overlayView.visibility = View.VISIBLE
        } else {
            overlayView.visibility = View.GONE
        }
    }

    private fun updatePositionEstimate(featureCount: Int, trackingQuality: Float, matchCount: Int) {
        val time = System.currentTimeMillis() / 1000.0

        // Better position estimation using match count
        if (trackingQuality > 0.3 && featureCount > 10 && matchCount > 5) {
            // Estimate movement based on match count and quality
            val movementScale = trackingQuality * 0.2 * (matchCount.toFloat() / 50f)
            val x = kotlin.math.sin(time) * 2.0 * movementScale
            val y = kotlin.math.cos(time) * 1.5 * movementScale
            val z = kotlin.math.sin(time * 0.5) * 0.3 * movementScale

            positionText.text = String.format("Position: (%.2f, %.2f, %.2f)", x, y, z)
        } else {
            positionText.text = "Position: (0.00, 0.00, 0.00)"
        }
    }

    private fun stopCamera() {
        Log.d("CameraSlam", "Stopping camera...")

        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            camera = null
            imageAnalysis = null

            isCameraActive = false
            trackingText.text = "Tracking: Stopped"
            trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            overlayView.visibility = View.GONE

            Log.d("CameraSlam", "Camera stopped")
            Toast.makeText(this, "Camera stopped", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("CameraSlam", "Error stopping camera: ${e.message}")
        }
    }

    private fun enableSimulatedMode() {
        isSimulatedMode = true
        isCameraActive = false

        // Stop real camera if running
        stopCamera()

        // Show/hide UI elements
        permissionOverlay.visibility = View.GONE
        infoPanel.visibility = View.VISIBLE
        controlPanel.visibility = View.VISIBLE
        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE

        trackingText.text = "Tracking: Simulated Mode"
        trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        Toast.makeText(this, "Using simulated Visual SLAM mode", Toast.LENGTH_LONG).show()

        // Start simulated updates
        startSimulatedMode()
    }

    private fun startSimulatedMode() {
        // Start simulated Visual SLAM updates
        android.os.Handler(mainLooper).postDelayed({
            if (isSimulatedMode) {
                updateSimulatedData()
                startSimulatedMode()
            }
        }, 500)
    }

    private fun updateSimulatedData() {
        val time = System.currentTimeMillis() / 1000.0
        val x = kotlin.math.sin(time) * 2.0
        val y = kotlin.math.cos(time) * 1.5
        val z = kotlin.math.sin(time * 0.5) * 0.3
        val features = (kotlin.math.sin(time) * 50 + 100).toInt()
        val matches = (features * 0.7).toInt()
        val trackingQuality = 0.7f

        positionText.text = String.format("Position: (%.2f, %.2f, %.2f)", x, y, z)
        landmarksText.text = "Features: $features"
        trackingText.text = String.format("Tracking: Simulated (%.0f%%)", trackingQuality * 100)
        fpsText.text = "FPS: 15 (simulated)"
    }

    private fun resetSLAM() {
        frameCount = 0
        currentFps = 0
        lastFrameProcessingTime = 0

        // Reset feature detector
        featureDetector.reset()

        // Reset UI
        positionText.text = "Position: (0.00, 0.00, 0.00)"
        landmarksText.text = "Features: 0"
        fpsText.text = "FPS: 0"

        if (isSimulatedMode) {
            trackingText.text = "Tracking: Simulated Mode"
            trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        } else if (isCameraActive) {
            trackingText.text = "Tracking: Initializing..."
            trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        } else {
            trackingText.text = "Tracking: Stopped"
            trackingText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }

        Toast.makeText(this, "SLAM reset", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            Log.d("CameraSlam", "Permission result received")

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                Toast.makeText(this, "Camera permission granted!", Toast.LENGTH_SHORT).show()
                checkCameraPermission()
            } else {
                // Permission denied
                if (shouldShowPermissionRationale()) {
                    permissionText.text = "Camera permission denied.\n\nPlease grant permission to use Visual SLAM features."
                    openSettingsBtn.visibility = View.VISIBLE
                    Toast.makeText(this,
                        "Camera permission denied. Please grant permission to continue.",
                        Toast.LENGTH_LONG).show()
                } else {
                    permissionText.text = "Camera permission permanently denied.\n\nPlease enable it in App Settings or use Simulated Mode."
                    openSettingsBtn.visibility = View.VISIBLE
                    Toast.makeText(this,
                        "Camera permission denied permanently. Enable in Settings or use Simulated Mode.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SETTINGS) {
            // User returned from settings - check permission again
            Log.d("CameraSlam", "Returned from settings")
            checkCameraPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("CameraSlam", "Activity resumed")
        checkCameraPermission()
    }

    override fun onPause() {
        super.onPause()
        Log.d("CameraSlam", "Activity paused")
        if (isCameraActive) {
            stopCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop camera
        if (isCameraActive) {
            stopCamera()
        }

        // Shutdown executor
        cameraExecutor.shutdown()
        try {
            if (!cameraExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                cameraExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cameraExecutor.shutdownNow()
        }

        Log.d("CameraSlam", "Activity destroyed")
    }
}