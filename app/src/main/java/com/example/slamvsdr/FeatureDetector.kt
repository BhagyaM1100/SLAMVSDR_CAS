package com.example.slamvsdr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlin.math.*

class FeatureDetector {

    private var previousFeatures: List<FeaturePoint> = emptyList()
    private var featureHistory = mutableListOf<List<FeaturePoint>>()
    private val maxHistorySize = 3

    // Position tracking state
    private var positionX = 0f
    private var positionY = 0f
    private var positionZ = 2f
    private var velocityX = 0f
    private var velocityY = 0f

    // Visual settings
    private var showFeatureTracks = true
    private var showMotionArrow = true
    private var showFeatureHistory = true

    fun detectFeatures(imageProxy: ImageProxy): FeatureDetectionResult {
        val startTime = System.currentTimeMillis()

        return try {
            // Get Y channel (luminance) from YUV image
            val yBuffer = imageProxy.planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height

            // Copy Y data
            val yData = ByteArray(yBuffer.remaining())
            yBuffer.get(yData)
            yBuffer.rewind()

            // Detect features - MORE AGGRESSIVE DETECTION
            val features = detectAggressiveFeatures(yData, width, height)

            // Store feature history for visualization
            featureHistory.add(0, features)
            if (featureHistory.size > maxHistorySize) {
                featureHistory.removeAt(maxHistorySize)
            }

            // Match features
            val matches = if (previousFeatures.isNotEmpty() && features.isNotEmpty()) {
                matchFeaturesBetter(features, previousFeatures)
            } else {
                emptyList()
            }

            // Estimate position from feature flow
            val positionUpdate = if (matches.size >= 3) {
                estimatePositionFromFeatureFlow(matches, width, height)
            } else {
                Triple(positionX, positionY, positionZ)
            }

            // Update position
            positionX = positionUpdate.first
            positionY = positionUpdate.second
            positionZ = positionUpdate.third

            previousFeatures = features

            // Debug log
            if (features.size > 0) {
                Log.d("FeatureTracker",
                    "Features: ${features.size}, Matches: ${matches.size}, " +
                            "Pos: (${"%.2f".format(positionX)}, ${"%.2f".format(positionY)})"
                )
            }

            FeatureDetectionResult(
                featureCount = features.size,
                features = features,
                matches = matches,
                positionX = positionX,
                positionY = positionY,
                positionZ = positionZ,
                processingTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            Log.e("FeatureDetector", "Error: ${e.message}")
            FeatureDetectionResult()
        }
    }

    private fun detectAggressiveFeatures(yData: ByteArray, width: Int, height: Int): List<FeaturePoint> {
        val features = mutableListOf<FeaturePoint>()

        // Use DENSE grid for maximum features
        val gridSize = 12  // More dense grid

        for (y in gridSize until height - gridSize step gridSize) {
            for (x in gridSize until width - gridSize step gridSize) {
                // Use faster corner detection
                val score = calculateFastCornerScore(yData, x, y, width, height)

                // VERY LOW THRESHOLD - detect almost everything
                if (score > 20) {
                    features.add(FeaturePoint(x.toFloat(), y.toFloat(), score.toFloat()))

                    // Add extra points around for dense coverage
                    for (dy in -1..1 step 2) {
                        for (dx in -1..1 step 2) {
                            if (x + dx * 2 in 0 until width && y + dy * 2 in 0 until height) {
                                val subScore = calculateFastCornerScore(yData, x + dx * 2, y + dy * 2, width, height)
                                if (subScore > 15) {
                                    features.add(FeaturePoint((x + dx * 2).toFloat(), (y + dy * 2).toFloat(), subScore.toFloat()))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Also detect along strong edges
        detectEdgeFeatures(yData, width, height, features)

        Log.d("FeatureDetector", "Detected ${features.size} features")
        return features
    }

    private fun calculateFastCornerScore(yData: ByteArray, x: Int, y: Int, width: Int, height: Int): Int {
        if (x < 2 || x >= width - 2 || y < 2 || y >= height - 2) return 0

        val center = getLuminance(yData, x, y, width)

        // Simple gradient in 4 directions
        val right = getLuminance(yData, x + 1, y, width)
        val left = getLuminance(yData, x - 1, y, width)
        val bottom = getLuminance(yData, x, y + 1, width)
        val top = getLuminance(yData, x, y - 1, width)

        // Check for corners (high gradient in orthogonal directions)
        val horizontalDiff = abs(center - right) + abs(center - left)
        val verticalDiff = abs(center - bottom) + abs(center - top)

        // Also check diagonals
        val diag1 = abs(center - getLuminance(yData, x + 1, y + 1, width))
        val diag2 = abs(center - getLuminance(yData, x - 1, y + 1, width))

        return (horizontalDiff + verticalDiff + diag1 + diag2) / 2
    }

    private fun detectEdgeFeatures(yData: ByteArray, width: Int, height: Int, features: MutableList<FeaturePoint>) {
        // Detect along image borders (where texture often exists)
        val borderMargin = 8

        // Horizontal borders
        for (x in borderMargin until width - borderMargin step 10) {
            // Top border
            val scoreTop = calculateFastCornerScore(yData, x, borderMargin, width, height)
            if (scoreTop > 25) {
                features.add(FeaturePoint(x.toFloat(), borderMargin.toFloat(), scoreTop.toFloat()))
            }

            // Bottom border
            val scoreBottom = calculateFastCornerScore(yData, x, height - borderMargin, width, height)
            if (scoreBottom > 25) {
                features.add(FeaturePoint(x.toFloat(), (height - borderMargin).toFloat(), scoreBottom.toFloat()))
            }
        }

        // Vertical borders
        for (y in borderMargin until height - borderMargin step 10) {
            // Left border
            val scoreLeft = calculateFastCornerScore(yData, borderMargin, y, width, height)
            if (scoreLeft > 25) {
                features.add(FeaturePoint(borderMargin.toFloat(), y.toFloat(), scoreLeft.toFloat()))
            }

            // Right border
            val scoreRight = calculateFastCornerScore(yData, width - borderMargin, y, width, height)
            if (scoreRight > 25) {
                features.add(FeaturePoint((width - borderMargin).toFloat(), y.toFloat(), scoreRight.toFloat()))
            }
        }
    }

    private fun getLuminance(yData: ByteArray, x: Int, y: Int, width: Int): Int {
        val index = y * width + x
        return if (index in yData.indices) {
            yData[index].toInt() and 0xFF
        } else {
            128
        }
    }

    private fun matchFeaturesBetter(
        current: List<FeaturePoint>,
        previous: List<FeaturePoint>
    ): List<Pair<FeaturePoint, FeaturePoint>> {
        val matches = mutableListOf<Pair<FeaturePoint, FeaturePoint>>()
        val maxDistance = 20f  // Tighter matching

        // Use kd-tree like approach (simple nearest neighbor)
        for (curr in current.take(60)) {
            var bestMatch: FeaturePoint? = null
            var bestDistance = Float.MAX_VALUE

            for (prev in previous.take(60)) {
                val dx = curr.x - prev.x
                val dy = curr.y - prev.y
                val distance = sqrt(dx * dx + dy * dy)

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

    private fun estimatePositionFromFeatureFlow(
        matches: List<Pair<FeaturePoint, FeaturePoint>>,
        width: Int,
        height: Int
    ): Triple<Float, Float, Float> {
        // Calculate optical flow
        var totalDx = 0f
        var totalDy = 0f

        for ((current, previous) in matches) {
            totalDx += (current.x - previous.x)
            totalDy += (current.y - previous.y)
        }

        val avgDx = totalDx / matches.size
        val avgDy = totalDy / matches.size

        // Convert to real-world motion
        val focalLength = 500f
        val frameTime = 0.033f

        val moveX = (avgDx * positionZ) / focalLength
        val moveY = (avgDy * positionZ) / focalLength

        // Smooth velocity
        velocityX = 0.7f * velocityX + 0.3f * moveX / frameTime
        velocityY = 0.7f * velocityY + 0.3f * moveY / frameTime

        // Update position
        positionX += velocityX * frameTime
        positionY += velocityY * frameTime

        // Simple depth estimation
        val avgFlow = sqrt(avgDx * avgDx + avgDy * avgDy)
        val flowChange = avgFlow - 8f
        positionZ += flowChange * 0.005f
        positionZ = positionZ.coerceIn(1.0f, 4.0f)

        return Triple(positionX, positionY, positionZ)
    }

    fun createFeatureOverlay(
        width: Int,
        height: Int,
        features: List<FeaturePoint>,
        matches: List<Pair<FeaturePoint, FeaturePoint>>
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        // Draw motion direction with LARGER, MORE VISIBLE arrow
        if (showMotionArrow && matches.isNotEmpty()) {
            val centerX = width / 2f
            val centerY = height / 2f

            // Calculate average motion
            var avgMotionX = 0f
            var avgMotionY = 0f
            for ((current, previous) in matches.take(20)) {
                avgMotionX += (current.x - previous.x)
                avgMotionY += (current.y - previous.y)
            }
            avgMotionX /= max(matches.size, 1)
            avgMotionY /= max(matches.size, 1)

            // Draw LARGE motion arrow
            val motionPaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 4f  // Thicker
                isAntiAlias = true
                alpha = 230  // More opaque
            }

            val arrowLength = 60f  // Longer arrow
            val arrowEndX = centerX + avgMotionX * 5f  // More amplification
            val arrowEndY = centerY + avgMotionY * 5f

            // Draw arrow shaft
            canvas.drawLine(centerX, centerY, arrowEndX, arrowEndY, motionPaint)

            // Draw LARGE arrow head
            drawLargeArrow(canvas, centerX, centerY, arrowEndX, arrowEndY, motionPaint)

            // Draw motion magnitude text
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f  // Larger text
                isAntiAlias = true
                alpha = 220
            }

            val motionMag = sqrt(avgMotionX * avgMotionX + avgMotionY * avgMotionY)
            val motionText = "Motion: ${"%.1f".format(motionMag)} px"
            canvas.drawText(motionText, 30f, 50f, textPaint)
        }

        // Draw feature tracks (blue lines)
        if (showFeatureTracks) {
            val trackPaint = Paint().apply {
                color = Color.argb(200, 0, 150, 255)  // Brighter blue
                style = Paint.Style.STROKE
                strokeWidth = 1.8f  // Slightly thicker
                isAntiAlias = true
            }

            for ((current, previous) in matches.take(40)) {
                canvas.drawLine(previous.x, previous.y, current.x, current.y, trackPaint)
            }
        }

        // Draw BRIGHT GREEN current features
        val featurePaint = Paint().apply {
            color = Color.argb(230, 0, 255, 0)  // Bright green
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw ALL current features (not just first 100)
        for (feature in features) {
            // Size based on feature score
            val radius = 3 + (feature.score / 80f).toInt().coerceIn(2, 5)
            canvas.drawCircle(feature.x, feature.y, radius.toFloat(), featurePaint)

            // Add glow effect for important features
            if (feature.score > 100) {
                val glowPaint = Paint().apply {
                    color = Color.argb(80, 0, 255, 0)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(feature.x, feature.y, (radius * 1.5f), glowPaint)
            }
        }

        // Draw feature history (faint older features)
        if (showFeatureHistory && featureHistory.size > 1) {
            val historyPaint = Paint().apply {
                color = Color.argb(80, 255, 255, 0)  // Faint yellow
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            // Draw features from previous frames
            for (i in 1 until min(featureHistory.size, 3)) {
                for (oldFeature in featureHistory[i].take(30)) {
                    canvas.drawCircle(oldFeature.x, oldFeature.y, 2f, historyPaint)
                }
            }
        }

        // Draw info overlay
        drawInfoOverlay(canvas, width, height, features.size, matches.size)

        return bitmap
    }

    private fun drawLargeArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val angle = atan2(y2 - y1, x2 - x1)
        val arrowLength = 20f  // Larger arrow head

        val x3 = x2 - arrowLength * cos(angle + PI.toFloat() / 5)  // Wider angle
        val y3 = y2 - arrowLength * sin(angle + PI.toFloat() / 5)
        val x4 = x2 - arrowLength * cos(angle - PI.toFloat() / 5)
        val y4 = y2 - arrowLength * sin(angle - PI.toFloat() / 5)

        canvas.drawLine(x2, y2, x3, y3, paint)
        canvas.drawLine(x2, y2, x4, y4, paint)

        // Draw filled arrow head for better visibility
        val fillPaint = Paint(paint).apply {
            style = Paint.Style.FILL
            color = Color.argb(180, 255, 255, 0)  // Semi-transparent yellow fill
        }

        val path = android.graphics.Path()
        path.moveTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawInfoOverlay(canvas: Canvas, width: Int, height: Int, featureCount: Int, matchCount: Int) {
        val infoPaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            isAntiAlias = true
            alpha = 200
        }

        val stats = "Features: $featureCount  |  Matches: $matchCount"
        canvas.drawText(stats, 30f, height - 40f, infoPaint)

        // Draw position indicator
        val posText = "Pos: (${"%.2f".format(positionX)}, ${"%.2f".format(positionY)})"
        canvas.drawText(posText, 30f, height - 70f, infoPaint)
    }

    // Toggle visualization options
    fun toggleFeatureTracks(show: Boolean) { showFeatureTracks = show }
    fun toggleMotionArrow(show: Boolean) { showMotionArrow = show }
    fun toggleFeatureHistory(show: Boolean) { showFeatureHistory = show }

    fun reset() {
        previousFeatures = emptyList()
        featureHistory.clear()
        positionX = 0f
        positionY = 0f
        positionZ = 2f
        velocityX = 0f
        velocityY = 0f
        Log.d("FeatureDetector", "Reset complete")
    }
}

data class FeaturePoint(
    val x: Float,
    val y: Float,
    val score: Float = 1.0f
)

data class FeatureDetectionResult(
    val featureCount: Int = 0,
    val features: List<FeaturePoint> = emptyList(),
    val matches: List<Pair<FeaturePoint, FeaturePoint>> = emptyList(),
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val positionZ: Float = 0f,
    val processingTimeMs: Long = 0
)