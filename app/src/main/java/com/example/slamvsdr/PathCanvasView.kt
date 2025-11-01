package com.example.slamvsdr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PathCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val currentPositionPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var currentX = 0f
    private var currentY = 0f
    private val scaleFactor = 50f // Scale to make movement visible on screen

    fun updatePosition(x: Double, y: Double) {
        // Scale factor to make movement visible but contained
        val scaleFactor = 20f // Reduced from 50f to keep path on screen

        // Convert coordinates to screen coordinates (flip Y axis and scale)
        val screenX = (width / 2) + (x * scaleFactor).toFloat()
        val screenY = (height / 2) - (y * scaleFactor).toFloat() // Flip Y axis

        // Keep path within bounds (prevent going too far off-screen)
        val boundedX = screenX.coerceIn(50f, (width - 50).toFloat())
        val boundedY = screenY.coerceIn(50f, (height - 50).toFloat())

        if (path.isEmpty) {
            // Start the path at current position
            path.moveTo(boundedX, boundedY)
        } else {
            // Add line to new position
            path.lineTo(boundedX, boundedY)
        }

        currentX = boundedX
        currentY = boundedY

        // Redraw the view
        invalidate()
    }

    fun resetPath() {
        path.reset()
        currentX = (width / 2).toFloat()
        currentY = (height / 2).toFloat()


        path.moveTo(currentX, currentY)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw grid background
        drawGrid(canvas)

        // Draw the path
        canvas.drawPath(path, paint)

        // Draw current position as a red dot
        canvas.drawCircle(currentX, currentY, 12f, currentPositionPaint)

        // Draw center crosshair
        drawCrosshair(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        val gridPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        val centerX = width / 2
        val centerY = height / 2

        // Draw vertical line
        canvas.drawLine(centerX.toFloat(), 0f, centerX.toFloat(), height.toFloat(), gridPaint)
        // Draw horizontal line
        canvas.drawLine(0f, centerY.toFloat(), width.toFloat(), centerY.toFloat(), gridPaint)
    }

    private fun drawCrosshair(canvas: Canvas) {
        val crossPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 2f
        }

        val centerX = width / 2
        val centerY = height / 2
        val size = 20f

        canvas.drawLine(centerX - size, centerY.toFloat(), centerX + size, centerY.toFloat(), crossPaint)
        canvas.drawLine(centerX.toFloat(), centerY - size, centerX.toFloat(), centerY + size, crossPaint)
    }
}