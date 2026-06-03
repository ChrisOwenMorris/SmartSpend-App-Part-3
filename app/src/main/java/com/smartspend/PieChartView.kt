package com.smartspend

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.smartspend.data.PieSlice
import kotlin.math.cos
import kotlin.math.sin

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var slices: List<PieSlice> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Label paint for drawing slice labels (Category + %)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val oval = RectF()

    fun setData(newSlices: List<PieSlice>) {
        slices = newSlices
        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val isGoalChart = slices.any { it.name == "Saved" || it.name == "Remaining" }

        val size = minOf(widthF, heightF) * 0.7f // Slightly smaller to make room for labels
        if (size <= 0f) return

        val cx = widthF / 2f
        val cy = heightF / 2f
        val radius = size / 2f

        val totalValue = slices.sumOf { it.value }.toFloat()
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        var startAngle = -90f

        for (slice in slices) {
            val sweepAngle = if (totalValue > 0) (slice.value.toFloat() / totalValue) * 360f else 0f
            if (sweepAngle <= 0f) continue

            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL

            // Apply Gradient for Goal Chart, solid color otherwise
            if (slice.name == "Saved" && isGoalChart) {
                val shader = SweepGradient(cx, cy, intArrayOf(
                    Color.parseColor("#0066cc"),
                    Color.parseColor("#10b981"),
                    Color.parseColor("#0066cc")
                ), null)
                val matrix = Matrix()
                matrix.postRotate(-90f, cx, cy)
                shader.setLocalMatrix(matrix)
                paint.shader = shader
            } else {
                paint.color = slice.color
            }

            canvas.drawArc(oval, startAngle, sweepAngle, true, paint)

            // Draw Labels for non-goal charts
            if (!isGoalChart && sweepAngle > 20f) {
                val angleRad = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                val labelRadius = radius * 1.25f // Position outside the slice
                val labelX = cx + (labelRadius * cos(angleRad)).toFloat()
                val labelY = cy + (labelRadius * sin(angleRad)).toFloat()

                val labelText = "${slice.name}\n${slice.percentage.toInt()}%"
                // Draw multiple lines if needed (simple implementation)
                canvas.drawText(slice.name, labelX, labelY, labelPaint)
                canvas.drawText("${slice.percentage.toInt()}%", labelX, labelY + 30f, labelPaint)
            }

            startAngle += sweepAngle
        }

        // Draw Donut Hole only for Goal Chart
        if (isGoalChart) {
            paint.shader = null
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius * 0.65f, paint)

            // Draw center percentage
            val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 50f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val savedSlice = slices.find { it.name == "Saved" }
            val text = "${savedSlice?.percentage?.toInt() ?: 0}%"
            canvas.drawText(text, cx, cy - (centerTextPaint.descent() + centerTextPaint.ascent()) / 2, centerTextPaint)
        }
    }
}