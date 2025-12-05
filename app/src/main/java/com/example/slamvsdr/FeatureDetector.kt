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
    private var positionZ = 2f        // keep roughly constant for stability
    private var velocityX = 0f
    private var velocityY = 0f

    // Visual settings
    private var showFeatureTracks = true
    private var showMotionArrow = true
    private var showFeatureHistory = true

    // Tuning constants (aggressive smoothing & robustness)
    private val maxFeaturesToKeep = 180
    private val maxMatchesToUse = 80
    private val maxMatchDistancePx = 18f       // tighter
    private val minMatchesForMotion = 15
    private val maxFlowPerFramePx = 10f        // clamp optical flow magnitude
    private val minFlowForMotionPx = 0.5f      // ignore tiny noise
    private val frameTime = 0.033f             // ~30 FPS assumed

    fun detectFeatures(imageProxy: ImageProxy): FeatureDetectionResult {
        val startTime = System.currentTimeMillis()

        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height

            val yData = ByteArray(yBuffer.remaining())
            yBuffer.get(yData)
            yBuffer.rewind()

            // 1) Detect candidate features
            var features = detectAggressiveFeatures(yData, width, height)

            // 2) Keep only strongest K
            features = features
                .sortedByDescending { it.score }
                .take(maxFeaturesToKeep)

            // Store feature history for overlay
            featureHistory.add(0, features)
            if (featureHistory.size > maxHistorySize) {
                featureHistory.removeAt(maxHistorySize)
            }

            // 3) Match to previous frame
            var matches: List<Pair<FeaturePoint, FeaturePoint>> = emptyList()
            if (previousFeatures.isNotEmpty() && features.isNotEmpty()) {
                matches = matchFeaturesBetter(features, previousFeatures)
            }

            // 4) Robust motion estimation + strong smoothing
            if (matches.size >= minMatchesForMotion) {
                val filteredMatches = filterOutlierMatches(matches)
                if (filteredMatches.size >= minMatchesForMotion) {
                    val posUpdate = estimatePositionFromFeatureFlow(filteredMatches, width, height)
                    positionX = posUpdate.first
                    positionY = posUpdate.second
                    positionZ = posUpdate.third
                    matches = filteredMatches
                }
            }

            previousFeatures = features

            if (features.isNotEmpty()) {
                Log.d(
                    "FeatureTracker",
                    "Features=${features.size}, Matches=${matches.size}, " +
                            "Pos=(${String.format("%.2f", positionX)}, ${String.format("%.2f", positionY)}, Z=${String.format("%.2f", positionZ)})"
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

    // ---------- FEATURE DETECTION ----------

    private fun detectAggressiveFeatures(yData: ByteArray, width: Int, height: Int): List<FeaturePoint> {
        val features = mutableListOf<FeaturePoint>()

        // Slightly coarser grid for more stable features
        val gridSize = 16
        val cornerThreshold = 28

        for (y in gridSize until height - gridSize step gridSize) {
            for (x in gridSize until width - gridSize step gridSize) {
                val score = calculateFastCornerScore(yData, x, y, width, height)
                if (score > cornerThreshold) {
                    features.add(FeaturePoint(x.toFloat(), y.toFloat(), score.toFloat()))
                }
            }
        }

        // Limited border features (optional but can help)
        detectEdgeFeatures(yData, width, height, features)

        Log.d("FeatureDetector", "Detected ${features.size} raw features")
        return features
    }

    private fun calculateFastCornerScore(yData: ByteArray, x: Int, y: Int, width: Int, height: Int): Int {
        if (x < 2 || x >= width - 2 || y < 2 || y >= height - 2) return 0

        val center = getLuminance(yData, x, y, width)

        val right = getLuminance(yData, x + 1, y, width)
        val left = getLuminance(yData, x - 1, y, width)
        val bottom = getLuminance(yData, x, y + 1, width)
        val top = getLuminance(yData, x, y - 1, width)

        val diag1 = getLuminance(yData, x + 1, y + 1, width)
        val diag2 = getLuminance(yData, x - 1, y + 1, width)

        val horizontalDiff = abs(center - right) + abs(center - left)
        val verticalDiff = abs(center - bottom) + abs(center - top)
        val diagonalDiff = abs(center - diag1) + abs(center - diag2)

        return (horizontalDiff + verticalDiff + diagonalDiff) / 2
    }

    private fun detectEdgeFeatures(
        yData: ByteArray,
        width: Int,
        height: Int,
        features: MutableList<FeaturePoint>
    ) {
        val borderMargin = 10

        // Horizontal borders
        for (x in borderMargin until width - borderMargin step 18) {
            val scoreTop = calculateFastCornerScore(yData, x, borderMargin, width, height)
            if (scoreTop > 32) features.add(FeaturePoint(x.toFloat(), borderMargin.toFloat(), scoreTop.toFloat()))

            val scoreBottom = calculateFastCornerScore(yData, x, height - borderMargin, width, height)
            if (scoreBottom > 32) features.add(FeaturePoint(x.toFloat(), (height - borderMargin).toFloat(), scoreBottom.toFloat()))
        }

        // Vertical borders
        for (y in borderMargin until height - borderMargin step 18) {
            val scoreLeft = calculateFastCornerScore(yData, borderMargin, y, width, height)
            if (scoreLeft > 32) features.add(FeaturePoint(borderMargin.toFloat(), y.toFloat(), scoreLeft.toFloat()))

            val scoreRight = calculateFastCornerScore(yData, width - borderMargin, y, width, height)
            if (scoreRight > 32) features.add(FeaturePoint((width - borderMargin).toFloat(), y.toFloat(), scoreRight.toFloat()))
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

    // ---------- MATCHING + OUTLIER FILTER ----------

    private fun matchFeaturesBetter(
        current: List<FeaturePoint>,
        previous: List<FeaturePoint>
    ): List<Pair<FeaturePoint, FeaturePoint>> {
        val matches = mutableListOf<Pair<FeaturePoint, FeaturePoint>>()
        val maxDist = maxMatchDistancePx

        val currentStrong = current.sortedByDescending { it.score }.take(maxMatchesToUse)
        val previousStrong = previous.sortedByDescending { it.score }.take(maxMatchesToUse)

        for (curr in currentStrong) {
            var bestMatch: FeaturePoint? = null
            var bestDistSq = Float.MAX_VALUE

            for (prev in previousStrong) {
                val dx = curr.x - prev.x
                val dy = curr.y - prev.y
                val distSq = dx * dx + dy * dy
                if (distSq < bestDistSq && distSq <= maxDist * maxDist) {
                    bestDistSq = distSq
                    bestMatch = prev
                }
            }

            if (bestMatch != null) {
                matches.add(curr to bestMatch)
            }
        }

        Log.d("FeatureDetector", "Raw matches: ${matches.size}")
        return matches
    }

    private fun filterOutlierMatches(
        matches: List<Pair<FeaturePoint, FeaturePoint>>
    ): List<Pair<FeaturePoint, FeaturePoint>> {
        if (matches.size < 3) return matches

        val dxs = matches.map { (c, p) -> c.x - p.x }
        val dys = matches.map { (c, p) -> c.y - p.y }

        val medianDx = dxs.median()
        val medianDy = dys.median()

        // Angular + magnitude-based filtering
        val mags = matches.map { (c, p) ->
            val dx = c.x - p.x
            val dy = c.y - p.y
            hypot(dx, dy)
        }

        val medianMag = mags.median().coerceAtLeast(1e-3f)

        val filtered = matches.filterIndexed { i, (c, p) ->
            val dx = c.x - p.x
            val dy = c.y - p.y
            val mag = mags[i]

            // Reject insane magnitudes
            if (mag > maxFlowPerFramePx * 3f) return@filterIndexed false

            // Reject points very far from median magnitude
            val magDev = abs(mag - medianMag)
            if (magDev > maxFlowPerFramePx) return@filterIndexed false

            true
        }

        Log.d("FeatureDetector", "Filtered matches: ${filtered.size} (from ${matches.size})")
        return filtered
    }

    private fun List<Float>.median(): Float {
        if (isEmpty()) return 0f
        val sorted = this.sorted()
        val mid = size / 2
        return if (size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    // ---------- MOTION ESTIMATION ----------

    private fun estimatePositionFromFeatureFlow(
        matches: List<Pair<FeaturePoint, FeaturePoint>>,
        width: Int,
        height: Int
    ): Triple<Float, Float, Float> {
        if (matches.isEmpty()) return Triple(positionX, positionY, positionZ)

        val dxList = matches.map { (c, p) -> c.x - p.x }
        val dyList = matches.map { (c, p) -> c.y - p.y }

        var flowDx = dxList.median()
        var flowDy = dyList.median()

        var flowMag = hypot(flowDx, flowDy)

        // Ignore tiny flows (noise)
        if (flowMag < minFlowForMotionPx) {
            flowDx = 0f
            flowDy = 0f
            flowMag = 0f
        }

        // Clamp max per-frame flow
        if (flowMag > maxFlowPerFramePx) {
            val scale = maxFlowPerFramePx / flowMag
            flowDx *= scale
            flowDy *= scale
            flowMag = maxFlowPerFramePx
        }

        val focalLength = 500f

        val moveX = (flowDx * positionZ) / focalLength
        val moveY = (flowDy * positionZ) / focalLength

        // Strong low-pass filtering on velocity
        val alpha = 0.9f     // closer to 1 => slower changes, smoother
        val targetVx = moveX / frameTime
        val targetVy = moveY / frameTime

        velocityX = (1f - alpha) * velocityX + alpha * targetVx
        velocityY = (1f - alpha) * velocityY + alpha * targetVy

        // Clamp velocity to prevent explosions
        velocityX = velocityX.coerceIn(-1.5f, 1.5f)
        velocityY = velocityY.coerceIn(-1.5f, 1.5f)

        positionX += velocityX * frameTime
        positionY += velocityY * frameTime

        // Keep Z almost constant (avoid noisy depth)
        val targetZ = 2.0f
        positionZ = 0.95f * positionZ + 0.05f * targetZ

        return Triple(positionX, positionY, positionZ)
    }

    // ---------- VISUALIZATION ----------

    fun createFeatureOverlay(
        width: Int,
        height: Int,
        features: List<FeaturePoint>,
        matches: List<Pair<FeaturePoint, FeaturePoint>>
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        if (showMotionArrow && matches.isNotEmpty()) {
            val centerX = width / 2f
            val centerY = height / 2f

            val dxList = matches.map { (c, p) -> c.x - p.x }
            val dyList = matches.map { (c, p) -> c.y - p.y }
            val avgDx = dxList.median()
            val avgDy = dyList.median()

            val motionPaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
                alpha = 230
            }

            val arrowEndX = centerX + avgDx * 5f
            val arrowEndY = centerY + avgDy * 5f

            canvas.drawLine(centerX, centerY, arrowEndX, arrowEndY, motionPaint)
            drawLargeArrow(canvas, centerX, centerY, arrowEndX, arrowEndY, motionPaint)

            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f
                isAntiAlias = true
                alpha = 220
            }

            val motionMag = hypot(avgDx, avgDy)
            val motionText = "Motion: ${"%.1f".format(motionMag)} px"
            canvas.drawText(motionText, 30f, 50f, textPaint)
        }

        if (showFeatureTracks) {
            val trackPaint = Paint().apply {
                color = Color.argb(200, 0, 150, 255)
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
                isAntiAlias = true
            }

            for ((current, previous) in matches.take(60)) {
                canvas.drawLine(previous.x, previous.y, current.x, current.y, trackPaint)
            }
        }

        val featurePaint = Paint().apply {
            color = Color.argb(230, 0, 255, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        for (feature in features) {
            val radius = 3 + (feature.score / 80f).toInt().coerceIn(2, 5)
            canvas.drawCircle(feature.x, feature.y, radius.toFloat(), featurePaint)

            if (feature.score > 100) {
                val glowPaint = Paint().apply {
                    color = Color.argb(80, 0, 255, 0)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(feature.x, feature.y, (radius * 1.5f), glowPaint)
            }
        }

        if (showFeatureHistory && featureHistory.size > 1) {
            val historyPaint = Paint().apply {
                color = Color.argb(80, 255, 255, 0)
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            for (i in 1 until min(featureHistory.size, 3)) {
                for (oldFeature in featureHistory[i].take(30)) {
                    canvas.drawCircle(oldFeature.x, oldFeature.y, 2f, historyPaint)
                }
            }
        }

        drawInfoOverlay(canvas, width, height, features.size, matches.size)

        return bitmap
    }

    private fun drawLargeArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val angle = atan2(y2 - y1, x2 - x1)
        val arrowLength = 20f

        val x3 = x2 - arrowLength * cos(angle + PI.toFloat() / 5)
        val y3 = y2 - arrowLength * sin(angle + PI.toFloat() / 5)
        val x4 = x2 - arrowLength * cos(angle - PI.toFloat() / 5)
        val y4 = y2 - arrowLength * sin(angle - PI.toFloat() / 5)

        canvas.drawLine(x2, y2, x3, y3, paint)
        canvas.drawLine(x2, y2, x4, y4, paint)

        val fillPaint = Paint(paint).apply {
            style = Paint.Style.FILL
            color = Color.argb(180, 255, 255, 0)
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

        val posText = "Pos: (${String.format("%.2f", positionX)}, ${String.format("%.2f", positionY)})"
        canvas.drawText(posText, 30f, height - 70f, infoPaint)
    }

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
