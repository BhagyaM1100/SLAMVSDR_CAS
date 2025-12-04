package com.example.slamvsdr

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class PathCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var plotController: PlotController? = null
    private var fusionController: FusionController? = null

    fun setPlotController(controller: PlotController) {
        plotController = controller
    }

    fun setFusionController(controller: FusionController) {
        fusionController = controller
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw coordinate grid
        drawGrid(canvas)

        // Draw paths if controllers are set
        fusionController?.let { fusion ->
            plotController?.draw(canvas, fusion.updateIMU(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val gridSize = 50f // pixels between grid lines

        // Draw grid lines
        val gridPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
        }

        // Vertical lines
        var x = centerX % gridSize
        while (x < width) {
            canvas.drawLine(x, 0f, x, height, gridPaint)
            x += gridSize
        }

        // Horizontal lines
        var y = centerY % gridSize
        while (y < height) {
            canvas.drawLine(0f, y, width, y, gridPaint)
            y += gridSize
        }

        // Draw axes
        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            strokeWidth = 3f
        }

        // X axis
        canvas.drawLine(0f, centerY, width, centerY, axisPaint)
        // Y axis
        canvas.drawLine(centerX, 0f, centerX, height, axisPaint)

        // Draw origin marker
        canvas.drawCircle(centerX, centerY, 10f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                style = android.graphics.Paint.Style.FILL
            })

        // Draw axis labels
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isAntiAlias = true
        }

        canvas.drawText("X", width - 30f, centerY - 10f, textPaint)
        canvas.drawText("Y", centerX + 10f, 30f, textPaint)
        canvas.drawText("0", centerX + 10f, centerY - 10f, textPaint)
    }
}