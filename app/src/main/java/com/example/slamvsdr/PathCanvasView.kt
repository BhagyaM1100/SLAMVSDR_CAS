package com.example.slamvsdr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class PathCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pathPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val positionPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val path = Path()
    private var centerX = 0f
    private var centerY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isInitialized = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        if (!isInitialized) {
            path.moveTo(centerX, centerY)
            isInitialized = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, pathPaint)
        canvas.drawCircle(centerX + currentX, centerY + currentY, 15f, positionPaint)
    }

    fun updatePosition(x: Double, y: Double) {
        val newX = (centerX + x.toFloat())
        val newY = (centerY - y.toFloat())
        path.lineTo(newX, newY)
        currentX = x.toFloat()
        currentY = -y.toFloat()
        invalidate()
    }

    fun resetPath() {
        path.reset()
        currentX = 0f
        currentY = 0f
        if (isInitialized) path.moveTo(centerX, centerY)
        invalidate()
    }
}