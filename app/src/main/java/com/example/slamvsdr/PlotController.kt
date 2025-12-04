package com.example.slamvsdr

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.*

class PlotController {

    private val drPath = Path()
    private val slamPath = Path()

    private val drPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val slamPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 7f
        isAntiAlias = true
    }

    private val landmarkPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val covariancePaint = Paint().apply {
        color = Color.argb(50, 255, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        isAntiAlias = true
    }

    private var scale = 50f // Pixels per meter
    private var offsetX = 0f
    private var offsetY = 0f

    private var lastDrX = 0f
    private var lastDrY = 0f
    private var lastSlamX = 0f
    private var lastSlamY = 0f

    private var isFirstPoint = true
    private var isDrVisible = true
    private var isSlamVisible = true

    init {
        reset()
    }

    fun setScale(newScale: Float) {
        scale = newScale
    }

    fun setOffset(x: Float, y: Float) {
        offsetX = x
        offsetY = y
    }

    fun showDr(show: Boolean) {
        isDrVisible = show
    }

    fun showSlam(show: Boolean) {
        isSlamVisible = show
    }

    fun updatePose(fusionResult: FusionController.FusionResult) {
        val centerX = 500f // Canvas center X
        val centerY = 500f // Canvas center Y

        // Convert to screen coordinates
        val drScreenX = centerX + fusionResult.drX.toFloat() * scale
        val drScreenY = centerY - fusionResult.drY.toFloat() * scale // Invert Y for screen

        val slamScreenX = centerX + fusionResult.slamX.toFloat() * scale
        val slamScreenY = centerY - fusionResult.slamY.toFloat() * scale

        if (isFirstPoint) {
            // Start paths
            drPath.moveTo(drScreenX, drScreenY)
            slamPath.moveTo(slamScreenX, slamScreenY)
            isFirstPoint = false
        } else {
            // Add to paths
            if (isDrVisible) {
                drPath.lineTo(drScreenX, drScreenY)
            }
            if (isSlamVisible && fusionResult.slamEnabled) {
                slamPath.lineTo(slamScreenX, slamScreenY)
            }
        }

        lastDrX = drScreenX
        lastDrY = drScreenY
        lastSlamX = slamScreenX
        lastSlamY = slamScreenY
    }

    fun draw(canvas: Canvas, fusionResult: FusionController.FusionResult) {
        // Draw paths
        if (isDrVisible) {
            canvas.drawPath(drPath, drPaint)
        }

        if (isSlamVisible && fusionResult.slamEnabled) {
            canvas.drawPath(slamPath, slamPaint)
        }

        // Draw current positions
        if (isDrVisible) {
            canvas.drawCircle(lastDrX, lastDrY, 10f, drPaint.apply { style = Paint.Style.FILL })
        }

        if (isSlamVisible && fusionResult.slamEnabled) {
            canvas.drawCircle(lastSlamX, lastSlamY, 12f, slamPaint.apply { style = Paint.Style.FILL })
        }

        // Draw landmarks
        for (landmark in fusionResult.landmarks) {
            val screenX = 500f + landmark.first.toFloat() * scale
            val screenY = 500f - landmark.second.toFloat() * scale

            // Draw landmark
            canvas.drawCircle(screenX, screenY, 8f, landmarkPaint)

            // Draw uncertainty ellipse (simplified)
            val uncertaintyRadius = 15f
            canvas.drawCircle(screenX, screenY, uncertaintyRadius, covariancePaint)
        }

        // Draw legend and info
        drawInfo(canvas, fusionResult)
    }

    private fun drawInfo(canvas: Canvas, fusionResult: FusionController.FusionResult) {
        var yPos = 50f

        // Title
        canvas.drawText("EKF-SLAM vs Dead Reckoning", 20f, yPos, textPaint)
        yPos += 40f

        // DR Info
        canvas.drawText("DR: (${"%.2f".format(fusionResult.drX)}, ${"%.2f".format(fusionResult.drY)})",
            20f, yPos, drPaint.apply { textSize = 25f })
        yPos += 30f

        // SLAM Info
        if (fusionResult.slamEnabled) {
            canvas.drawText("SLAM: (${"%.2f".format(fusionResult.slamX)}, ${"%.2f".format(fusionResult.slamY)})",
                20f, yPos, slamPaint.apply { textSize = 25f })
            yPos += 30f

            // Error
            val errorX = abs(fusionResult.slamX - fusionResult.drX)
            val errorY = abs(fusionResult.slamY - fusionResult.drY)
            val totalError = sqrt(errorX * errorX + errorY * errorY)

            canvas.drawText("Error: ${"%.3f".format(totalError)} m", 20f, yPos, textPaint.apply { textSize = 25f })
            yPos += 30f

            // Landmark count
            canvas.drawText("Landmarks: ${fusionResult.landmarks.size}", 20f, yPos, textPaint.apply { textSize = 25f })
        } else {
            canvas.drawText("SLAM: Disabled", 20f, yPos, textPaint.apply { textSize = 25f })
        }

        // Draw legend
        yPos += 50f
        canvas.drawText("Blue: Dead Reckoning", 20f, yPos, drPaint.apply { textSize = 25f })
        yPos += 30f
        canvas.drawText("Red: EKF-SLAM", 20f, yPos, slamPaint.apply { textSize = 25f })
        yPos += 30f
        canvas.drawText("Green: Landmarks", 20f, yPos, landmarkPaint.apply { textSize = 25f })
    }

    fun reset() {
        drPath.reset()
        slamPath.reset()
        isFirstPoint = true
        lastDrX = 0f
        lastDrY = 0f
        lastSlamX = 0f
        lastSlamY = 0f
    }

    fun getDrPath(): Path = drPath
    fun getSlamPath(): Path = slamPath
}