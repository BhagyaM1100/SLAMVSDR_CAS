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
    private val scaleFactor = 15f // Reduced scale to keep path more contained
    private val margin = 50f // Margin from edges

    fun updatePosition(x: Double, y: Double) {
        // Convert coordinates to screen coordinates
        var screenX = (width / 2) + (x * scaleFactor).toFloat()
        var screenY = (height / 2) - (y * scaleFactor).toFloat() // Flip Y axis

        // Limit coordinates to stay within bounds with margin
        screenX = screenX.coerceIn(margin, width - margin)
        screenY = screenY.coerceIn(margin, height - margin)

        if (path.isEmpty) {
            // Start the path at current position
            path.moveTo(screenX, screenY)
        } else {
            // Add line to new position
            path.lineTo(screenX, screenY)
        }

        currentX = screenX
        currentY = screenY

        // Redraw the view
        invalidate()
    }

    fun resetPath() {
        path.reset()
        currentX = (width / 2).toFloat()
        currentY = (height / 2).toFloat()

        // Start the path at center
        if (width > 0 && height > 0) {
            path.moveTo(currentX, currentY)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw grid background
        drawGrid(canvas)

        // Draw bounds border
        drawBounds(canvas)

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

    private fun drawBounds(canvas: Canvas) {
        val boundsPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 100 // Semi-transparent
        }

        // Draw bounds rectangle
        canvas.drawRect(
            margin,
            margin,
            width - margin,
            height - margin,
            boundsPaint
        )
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reset path when view size changes
        resetPath()
    }
}