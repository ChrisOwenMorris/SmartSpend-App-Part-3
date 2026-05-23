package com.smartspend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.smartspend.data.dao.TrendSummary

class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<TrendSummary> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2575FC".toColorInt()
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2575FC".toColorInt()
        style = Paint.Style.FILL
    }

    private val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 24f
        textAlign = Paint.Align.RIGHT
    }

    private val path = Path()
    private val points = mutableListOf<PointF>()

    fun setData(newData: List<TrendSummary>) {
        data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 80f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 80f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (data.isEmpty()) {
            canvas.drawText("No data available", width / 2f, height / 2f, labelPaint)
            return
        }

        val maxAmount = data.maxOfOrNull { it.total }?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val minAmount = 0f

        for (i in 0..4) {
            val y = paddingTop + chartHeight - (i * chartHeight / 4)
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            val value = minAmount + (i * (maxAmount - minAmount) / 4)
            canvas.drawText("R${(value / 1000).toInt()}k", paddingLeft - 10f, y + 8f, valuePaint)
        }

        points.clear()
        path.reset()

        data.forEachIndexed { index, summary ->
            val x = paddingLeft + (index * chartWidth / (data.size - 1).coerceAtLeast(1))
            val normalizedValue = if (maxAmount > minAmount) (summary.total.toFloat() - minAmount) / (maxAmount - minAmount) else 0f
            val y = paddingTop + chartHeight - (normalizedValue * chartHeight)
            points.add(PointF(x, y))

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, linePaint)

        points.forEachIndexed { index, point ->
            canvas.drawCircle(point.x, point.y, 12f, dotPaint)
            canvas.drawCircle(point.x, point.y, 6f, dotOutlinePaint)

            val monthLabel = data.getOrNull(index)?.month ?: ""
            canvas.drawText(monthLabel, point.x, height - 20f, labelPaint)

            val value = data.getOrNull(index)?.total ?: 0.0
            canvas.drawText("R${value.toInt()}", point.x, point.y - 20f, valuePaint)
        }
    }
}