package com.example.slamvsdr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

class VisualSlamController(context: Context) {

    // SLAM State
    var slamX = 0.0
    var slamY = 0.0
    var slamZ = 0.0
    var slamYaw = 0.0
    var slamPitch = 0.0
    var slamRoll = 0.0

    // Tracking state
    var isTracking = false
    var trackingQuality = 0.0f
    var frameCount = 0
    private var lastFrameTime = System.currentTimeMillis()

    // Simple feature tracking
    private var previousFeatures: List<FeaturePoint> = emptyList()
    private var previousFrameTime = 0L

    // Map points storage
    private val mapPoints = mutableListOf<Point3>()

    data class FeaturePoint(
        val x: Int,
        val y: Int,
        val value: Int
    )

    data class VisualSlamResult(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Double,
        val pitch: Double,
        val roll: Double,
        val isTracking: Boolean,
        val trackingQuality: Float,
        val featuresDetected: Int,
        val matches: Int
    )

    data class Point3(
        val x: Double,
        val y: Double,
        val z: Double
    )

    init {
        Log.d("VisualSLAM", "VisualSLAM Controller initialized (No OpenCV)")
    }

    // ADD THIS METHOD: Get map points
    fun getMapPoints(): List<Point3> {
        // If we don't have enough map points, create some simulated ones
        if (mapPoints.size < 10 && frameCount > 30) {
            generateSimulatedMapPoints()
        }
        return mapPoints.toList()
    }

    // ADD THIS METHOD: Generate simulated map points
    private fun generateSimulatedMapPoints() {
        mapPoints.clear()

        // Create a circular pattern of map points
        for (i in 0 until 20) {
            val angle = i * 2 * PI / 20
            val radius = 2.0 + (i % 3) * 0.5

            val x = slamX + cos(angle) * radius
            val y = slamY + sin(angle) * radius
            val z = slamZ + sin(angle * 2) * 0.3

            mapPoints.add(Point3(x, y, z))
        }

        Log.d("VisualSLAM", "Generated ${mapPoints.size} simulated map points")
    }

    // ADD THIS METHOD: Add a map point
    private fun addMapPoint(x: Double, y: Double, z: Double) {
        // Only add if not too close to existing points
        val minDistance = 0.5
        var tooClose = false

        for (point in mapPoints) {
            val dx = x - point.x
            val dy = y - point.y
            val dz = z - point.z
            val distance = sqrt(dx*dx + dy*dy + dz*dz)

            if (distance < minDistance) {
                tooClose = true
                break
            }
        }

        if (!tooClose && mapPoints.size < 100) {
            mapPoints.add(Point3(x, y, z))

            if (frameCount % 50 == 0) {
                Log.d("VisualSLAM", "Added map point at ($x, $y, $z). Total: ${mapPoints.size}")
            }
        }
    }

    fun createImageAnalyzer(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    Executors.newSingleThreadExecutor()
                ) { imageProxy ->
                    analyzeFrame(imageProxy)
                }
            }
    }

    @OptIn(ExperimentalGetImage::class) private fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            val image = imageProxy.image ?: return
            frameCount++

            // Convert YUV to grayscale values
            val grayValues = yuvToGrayValues(image)
            val width = image.width
            val height = image.height

            // Detect simple features (edges/corners)
            val features = detectSimpleFeatures(grayValues, width, height)

            // Track features from previous frame
            val matches = if (previousFeatures.isNotEmpty()) {
                matchFeatures(features, previousFeatures)
            } else {
                emptyList()
            }

            // Update SLAM state based on feature movement
            updateSlamFromFeatures(features, matches, width, height)

            // Update tracking quality
            trackingQuality = calculateTrackingQuality(features.size, matches.size)
            isTracking = trackingQuality > 0.3f

            // Occasionally add map points when tracking is good
            if (isTracking && frameCount % 20 == 0 && matches.size > 10) {
                addMapPoint(slamX, slamY, slamZ)
            }

            // Update previous features
            previousFeatures = features

            // Log periodically
            if (frameCount % 30 == 0) {
                Log.d("VisualSLAM",
                    "Frame $frameCount: ${features.size} features, ${matches.size} matches, " +
                            "Pos: (${"%.2f".format(slamX)}, ${"%.2f".format(slamY)}, ${"%.2f".format(slamZ)}), " +
                            "Quality: ${(trackingQuality * 100).toInt()}%, " +
                            "Map Points: ${mapPoints.size}"
                )
            }

        } catch (e: Exception) {
            Log.e("VisualSLAM", "Error analyzing frame ${frameCount}: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun yuvToGrayValues(image: Image): IntArray {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val width = image.width
        val height = image.height

        val yData = ByteArray(yBuffer.remaining())
        yBuffer.get(yData)

        val grayValues = IntArray(yData.size)
        for (i in yData.indices) {
            grayValues[i] = yData[i].toInt() and 0xFF
        }

        return grayValues
    }

    private fun detectSimpleFeatures(grayValues: IntArray, width: Int, height: Int): List<FeaturePoint> {
        val features = mutableListOf<FeaturePoint>()
        val gridSize = 20 // Check every 20th pixel

        for (y in gridSize until height - gridSize step gridSize) {
            for (x in gridSize until width - gridSize step gridSize) {
                val score = calculateCornerScore(grayValues, x, y, width)
                if (score > 50) { // Threshold for corner detection
                    features.add(FeaturePoint(x, y, score))
                }
            }
        }

        return features
    }

    private fun calculateCornerScore(grayValues: IntArray, x: Int, y: Int, width: Int): Int {
        val center = grayValues[y * width + x]
        var horizontalDiff = 0
        var verticalDiff = 0

        // Check 3x3 neighborhood
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue

                val neighborX = x + dx
                val neighborY = y + dy
                if (neighborX in 0 until width && neighborY in 0 until grayValues.size / width) {
                    val neighbor = grayValues[neighborY * width + neighborX]
                    val diff = Math.abs(center - neighbor)

                    if (dx == 0) verticalDiff += diff
                    if (dy == 0) horizontalDiff += diff
                }
            }
        }

        // Corner has high gradients in both directions
        return Math.min(horizontalDiff, verticalDiff)
    }

    private fun matchFeatures(
        current: List<FeaturePoint>,
        previous: List<FeaturePoint>
    ): List<Pair<FeaturePoint, FeaturePoint>> {
        val matches = mutableListOf<Pair<FeaturePoint, FeaturePoint>>()
        val maxDistance = 30 // pixels

        for (curr in current.take(50)) {
            var bestMatch: FeaturePoint? = null
            var bestDistance = Int.MAX_VALUE

            for (prev in previous.take(50)) {
                val dx = curr.x - prev.x
                val dy = curr.y - prev.y
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toInt()

                if (distance < maxDistance && distance < bestDistance) {
                    bestDistance = distance
                    bestMatch = prev
                }
            }

            if (bestMatch != null) {
                matches.add(Pair(curr, bestMatch))
            }
        }

        return matches
    }

    private fun updateSlamFromFeatures(
        features: List<FeaturePoint>,
        matches: List<Pair<FeaturePoint, FeaturePoint>>,
        width: Int,
        height: Int
    ) {
        if (matches.size < 5) {
            // Not enough matches for reliable tracking
            isTracking = false
            return
        }

        // Calculate average feature movement
        var totalDx = 0.0
        var totalDy = 0.0

        for ((current, previous) in matches) {
            totalDx += (current.x - previous.x).toDouble()
            totalDy += (current.y - previous.y).toDouble()
        }

        val avgDx = totalDx / matches.size
        val avgDy = totalDy / matches.size

        // Convert pixel movement to real-world movement
        val timeDelta = (System.currentTimeMillis() - lastFrameTime) / 1000.0
        lastFrameTime = System.currentTimeMillis()

        // Simple motion model
        val scale = 0.001 // scaling factor

        // Update position based on feature flow
        slamX += avgDx * scale
        slamY += avgDy * scale

        // Simple yaw estimation from feature distribution
        if (features.size > 10) {
            val centerX = width / 2
            val centerY = height / 2
            var moment = 0.0

            for (feature in features.take(20)) {
                val dx = feature.x - centerX
                val dy = feature.y - centerY
                moment += (dx * dy).toDouble()
            }

            // Simple rotation estimation
            slamYaw += moment * 0.000001
            slamYaw = normalizeAngle(slamYaw)
        }

        // Add small simulated vertical motion
        slamZ = sin(System.currentTimeMillis() / 5000.0) * 0.1

        isTracking = true
    }

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle
        while (normalized > PI) normalized -= 2 * PI
        while (normalized <= -PI) normalized += 2 * PI
        return normalized
    }

    private fun calculateTrackingQuality(featureCount: Int, matchCount: Int): Float {
        return when {
            featureCount == 0 -> 0.0f
            matchCount > featureCount * 0.7 -> 0.9f
            matchCount > featureCount * 0.4 -> 0.6f
            matchCount > featureCount * 0.2 -> 0.3f
            else -> 0.1f
        }
    }

    fun getCurrentPose(): DoubleArray {
        return doubleArrayOf(slamX, slamY, slamZ, slamYaw, slamPitch, slamRoll)
    }

    fun getTrackingStatus(): String {
        return if (isTracking) "Tracking (${(trackingQuality * 100).toInt()}%)" else "Lost"
    }

    fun getSlamResult(): VisualSlamResult {
        return VisualSlamResult(
            slamX, slamY, slamZ, slamYaw, slamPitch, slamRoll,
            isTracking, trackingQuality, previousFeatures.size, 0
        )
    }

    fun reset() {
        Log.d("VisualSLAM", "Resetting Visual SLAM")

        slamX = 0.0
        slamY = 0.0
        slamZ = 0.0
        slamYaw = 0.0
        slamPitch = 0.0
        slamRoll = 0.0

        previousFeatures = emptyList()
        mapPoints.clear()
        isTracking = false
        trackingQuality = 0.0f
        frameCount = 0

        Log.d("VisualSLAM", "Visual SLAM reset")
    }

    fun fuseWithDR(drX: Double, drY: Double, drTheta: Double): DoubleArray {
        val visualWeight = trackingQuality.coerceIn(0.1f, 0.8f)
        val drWeight = 1.0f - visualWeight

        val fusedX = visualWeight * slamX + drWeight * drX
        val fusedY = visualWeight * slamY + drWeight * drY
        val fusedTheta = visualWeight * slamYaw + drWeight * drTheta

        return doubleArrayOf(fusedX, fusedY, fusedTheta)
    }

    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "frameCount" to frameCount,
            "tracking" to isTracking,
            "trackingQuality" to trackingQuality,
            "mapPoints" to mapPoints.size,
            "position" to mapOf("x" to slamX, "y" to slamY, "z" to slamZ),
            "rotation" to mapOf("yaw" to slamYaw, "pitch" to slamPitch, "roll" to slamRoll)
        )
    }
}