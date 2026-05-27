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
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        color = Color.DKGRAY
    }

    private val oval = RectF()

    fun setData(newSlices: List<PieSlice>) {
        slices = newSlices
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Fix: Explicitly convert to Float to prevent type mismatch redlining
        val widthF = width.toFloat()
        val heightF = height.toFloat()

        val size = minOf(widthF, heightF * 0.7f)
        val cx = widthF / 2f
        val cy = (size / 2f) + 20f // Offset center to leave room for legends at the bottom
        val radius = (size / 2f) * 0.85f

        val totalValue = slices.sumOf { it.value }.toFloat()
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        if (totalValue == 0f) {
            paint.color = "#F0F0F0".toColorInt()
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
            if (percentage > 5) { // Only draw percentages for readable slices
                canvas.drawText("$percentage%", labelX, labelY + 8f, labelPaint)
            }
            startAngle += sweepAngle
        }

        // Donut hole ring structure
        canvas.drawCircle(cx, cy, radius * 0.5f, innerPaint)

        drawLegend(canvas, cy + radius + 40f)
    }

    private fun drawLegend(canvas: Canvas, startY: Float) {
        if (slices.isEmpty()) return
        val itemWidth = width.toFloat() / 2f

        slices.forEachIndexed { index, slice ->
            val col = index % 2
            val row = index / 2
            val x = col * itemWidth + 40f
            val y = startY + (row * 45f)

            paint.color = slice.color
            canvas.drawRect(x, y, x + 24f, y + 24f, paint)

            canvas.drawText("${slice.name}: R${slice.value.toInt()}", x + 35f, y + 20f, legendPaint)
        }
    }
}