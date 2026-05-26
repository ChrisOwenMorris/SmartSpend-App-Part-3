package com.smartspend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

data class PieSlice(val name: String, val value: Double, val color: Int)

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var slices: List<PieSlice> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 24f
    }

    private val oval = RectF()

    fun setData(newSlices: List<PieSlice>) {
        slices = newSlices
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f * 0.85f
        val totalValue = slices.sumOf { it.value }.toFloat()

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        if (totalValue == 0f) {
            paint.color = "#E0E0E0".toColorInt()
            canvas.drawCircle(cx, cy, radius, paint)
            return
        }

        var startAngle = -90f

        for (slice in slices) {
            val sweepAngle = (slice.value.toFloat() / totalValue) * 360f
            paint.color = slice.color

            canvas.drawArc(oval, startAngle, sweepAngle, true, paint)

            val midAngle = startAngle + sweepAngle / 2
            val labelRadius = radius * 0.65f
            val labelX = cx + labelRadius * Math.cos(Math.toRadians(midAngle.toDouble())).toFloat()
            val labelY = cy + labelRadius * Math.sin(Math.toRadians(midAngle.toDouble())).toFloat()

            val percentage = ((slice.value.toFloat() / totalValue) * 100).toInt()
            canvas.drawText("$percentage%", labelX, labelY + 8f, labelPaint)

            startAngle += sweepAngle
        }

        canvas.drawCircle(cx, cy, radius * 0.5f, innerPaint)

        drawLegend(canvas, cx, cy, radius)
    }

    private fun drawLegend(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val legendStartY = cy + radius + 30f
        val itemWidth = width / 2f

        slices.forEachIndexed { index, slice ->
            val col = index % 2
            val row = index / 2
            val x = col * itemWidth + 20f
            val y = legendStartY + (row * 40f)

            paint.color = slice.color
            canvas.drawRect(x, y, x + 20f, y + 20f, paint)

            legendPaint.color = Color.DKGRAY
            canvas.drawText("${slice.name}: R${slice.value.toInt()}", x + 30f, y + 18f, legendPaint)
        }
    }
}