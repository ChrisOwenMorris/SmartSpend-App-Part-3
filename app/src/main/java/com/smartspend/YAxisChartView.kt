package com.smartspend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

/**
 * YAxisChartView
 *
 * A bar chart with a clearly labelled Y-axis showing spending per category
 * for the currently selected reporting period. Called from ReportsActivity
 * via setData() whenever the period changes (week / month / year).
 *
 * Data contract: List of Pair<String, Double>
 *   - first  = category label (X-axis)
 *   - second = total amount   (Y-axis)
 */
class YAxisChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<String, Double>> = emptyList()

    // ── Paints ────────────────────────────────────────────────────────────────

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = null   // set per-bar below
        style = Paint.Style.FILL
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textSize = 26f
        textAlign = Paint.Align.RIGHT
    }

    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    // Gradient colours reused across bars (cycles if more categories than colours)
    private val barColors = listOf(
        intArrayOf("#6A11CB".toColorInt(), "#2575FC".toColorInt()),
        intArrayOf("#FF5F6D".toColorInt(), "#FFA17F".toColorInt()),
        intArrayOf("#00C9FF".toColorInt(), "#92FE9D".toColorInt()),
        intArrayOf("#F7971E".toColorInt(), "#FFD200".toColorInt()),
        intArrayOf("#DA22FF".toColorInt(), "#9733EE".toColorInt())
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun setData(newData: List<Pair<String, Double>>) {
        data = newData
        invalidate()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft   = 110f   // room for Y-axis labels
        val paddingRight  = 24f
        val paddingTop    = 40f
        val paddingBottom = 70f    // room for X-axis labels

        val chartW = width  - paddingLeft - paddingRight
        val chartH = height - paddingTop  - paddingBottom

        // Empty state
        if (data.isEmpty()) {
            canvas.drawText(
                "No data for this period",
                width / 2f,
                height / 2f,
                emptyPaint
            )
            return
        }

        val maxValue = data.maxOf { it.second }.toFloat().coerceAtLeast(1f)

        // Round max up to a clean number for grid labels
        val nGridLines  = 5
        val gridStep    = niceStep(maxValue, nGridLines)
        val adjustedMax = gridStep * nGridLines

        // ── Grid lines + Y-axis labels ────────────────────────────────────────
        for (i in 0..nGridLines) {
            val value = i * gridStep
            val y     = paddingTop + chartH - (value / adjustedMax) * chartH

            canvas.drawLine(paddingLeft, y, paddingLeft + chartW, y, gridPaint)

            val label = if (value >= 1000) "R${(value / 1000).toInt()}k"
            else               "R${value.toInt()}"
            canvas.drawText(label, paddingLeft - 10f, y + 8f, yLabelPaint)
        }

        // ── Y-axis line ───────────────────────────────────────────────────────
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, paddingTop + chartH, axisPaint)
        // X-axis line
        canvas.drawLine(
            paddingLeft, paddingTop + chartH,
            paddingLeft + chartW, paddingTop + chartH,
            axisPaint
        )

        // ── Bars ──────────────────────────────────────────────────────────────
        val barCount   = data.size
        val totalSlots = chartW / barCount
        val barWidth   = (totalSlots * 0.55f).coerceAtMost(80f)
        val gap        = totalSlots - barWidth

        data.forEachIndexed { index, (label, value) ->
            val slotCenterX = paddingLeft + (index * totalSlots) + totalSlots / 2f
            val barLeft     = slotCenterX - barWidth / 2f
            val barRight    = slotCenterX + barWidth / 2f
            val barTop      = paddingTop + chartH - (value.toFloat() / adjustedMax) * chartH
            val barBottom   = paddingTop + chartH

            // Gradient fill cycling through palette
            val colors = barColors[index % barColors.size]
            barPaint.shader = LinearGradient(
                barLeft, barTop, barLeft, barBottom,
                colors[0], colors[1],
                Shader.TileMode.CLAMP
            )

            val rect = RectF(barLeft, barTop, barRight, barBottom)
            canvas.drawRoundRect(rect, 10f, 10f, barPaint)

            // Value label above bar
            if (value > 0) {
                val valLabel = if (value >= 1000) "R${String.format("%.1f", value / 1000)}k"
                else               "R${value.toInt()}"
                canvas.drawText(valLabel, slotCenterX, barTop - 10f, valuePaint)
            }

            // X-axis category label — truncate if too long
            val maxChars   = ((barWidth + gap) / (xLabelPaint.textSize * 0.55f)).toInt().coerceAtLeast(4)
            val displayLabel = if (label.length > maxChars) label.take(maxChars - 1) + "…" else label
            canvas.drawText(displayLabel, slotCenterX, paddingTop + chartH + 45f, xLabelPaint)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns a "nice" step value so Y-axis labels land on round numbers.
     * e.g. maxValue=4300, steps=5 → step=1000 → labels: 0,1k,2k,3k,4k,5k
     */
    private fun niceStep(maxValue: Float, steps: Int): Float {
        val rawStep = maxValue / steps
        val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawStep.toDouble()))).toFloat()
        val normalised = rawStep / magnitude
        val nice = when {
            normalised <= 1f  -> 1f
            normalised <= 2f  -> 2f
            normalised <= 5f  -> 5f
            else              -> 10f
        }
        return nice * magnitude
    }
}