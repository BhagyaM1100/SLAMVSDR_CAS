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

    private val pathPaint = Paint().apply {
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

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val boundsPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 100
        isAntiAlias = true
    }

    private val crossPaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2f
        isAntiAlias = true
    }

    // world coordinate limits you requested
    private val worldXMin = -7f
    private val worldXMax = 7f
    private val worldYMin = -4f
    private val worldYMax = 4f

    // screen center
    private var centerX = 0f
    private var centerY = 0f

    private var currentX = 0f
    private var currentY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldh, oldw)
        centerX = w / 2f
        centerY = h / 2f
        resetPath()
    }

    /**
     * Convert world coordinate (-7..7, -4..4) to screen coordinate.
     */
    private fun worldToScreenX(x: Double): Float {
        val normalized = ((x - worldXMin) / (worldXMax - worldXMin)) // 0..1
        return (normalized * width).toFloat()
    }

    private fun worldToScreenY(y: Double): Float {
        val normalized = ((y - worldYMin) / (worldYMax - worldYMin)) // 0..1
        val screen = (normalized * height).toFloat()
        return height - screen  // invert y (so +y goes upward)
    }

    fun updatePosition(x: Double, y: Double) {
        val sx = worldToScreenX(x)
        val sy = worldToScreenY(y)

        if (path.isEmpty) {
            path.moveTo(sx, sy)
        } else {
            path.lineTo(sx, sy)
        }

        currentX = sx
        currentY = sy
        postInvalidateOnAnimation()
    }

    fun resetPath() {
        path.reset()

        // start at center of world (0,0)
        currentX = worldToScreenX(0.0)
        currentY = worldToScreenY(0.0)
        path.moveTo(currentX, currentY)

        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawGrid(canvas)
        drawBounds(canvas)

        canvas.drawPath(path, pathPaint)
        canvas.drawCircle(currentX, currentY, 12f, currentPositionPaint)

        drawCrosshair(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        // Y axis (x = 0)
        val zeroX = worldToScreenX(0.0)
        canvas.drawLine(zeroX, 0f, zeroX, height.toFloat(), gridPaint)

        // X axis (y = 0)
        val zeroY = worldToScreenY(0.0)
        canvas.drawLine(0f, zeroY, width.toFloat(), zeroY, gridPaint)
    }

    private fun drawBounds(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), boundsPaint)
    }

    private fun drawCrosshair(canvas: Canvas) {
        val size = 20f
        val cx = worldToScreenX(0.0)
        val cy = worldToScreenY(0.0)

        canvas.drawLine(cx - size, cy, cx + size, cy, crossPaint)
        canvas.drawLine(cx, cy - size, cx, cy + size, crossPaint)
    }
}
