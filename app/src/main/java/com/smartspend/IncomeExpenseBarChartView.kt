package com.smartspend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class IncomeExpenseBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var income: Double = 0.0
    private var expense: Double = 0.0

    private val incomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#4CAF50".toColorInt()
        style = Paint.Style.FILL
    }

    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#F44336".toColorInt()
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }

    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    fun setData(incomeVal: Double, expenseVal: Double) {
        income = incomeVal
        expense = expenseVal
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 100f
        val chartHeight = height - (padding * 2)
        val chartWidth = width - (padding * 2)

        val maxVal = maxOf(income, expense).toFloat().coerceAtLeast(1f)
        val barWidth = chartWidth / 3.5f

        val incomeHeight = if (maxVal > 0) (income.toFloat() / maxVal) * chartHeight else 0f
        val incomeLeft = padding + (chartWidth * 0.15f)

        val incomeRect = RectF(
            incomeLeft,
            (height - padding) - incomeHeight,
            incomeLeft + barWidth,
            height - padding
        )
        canvas.drawRoundRect(incomeRect, 8f, 8f, incomePaint)
        canvas.drawText("Income", incomeRect.centerX(), height - 40f, textPaint)
        canvas.drawText("R ${income.toInt()}", incomeRect.centerX(), incomeRect.top - 10f, amountPaint)

        val expenseHeight = if (maxVal > 0) (expense.toFloat() / maxVal) * chartHeight else 0f
        val expenseLeft = padding + (chartWidth * 0.55f)

        val expenseRect = RectF(
            expenseLeft,
            (height - padding) - expenseHeight,
            expenseLeft + barWidth,
            height - padding
        )
        canvas.drawRoundRect(expenseRect, 8f, 8f, expensePaint)
        canvas.drawText("Expenses", expenseRect.centerX(), height - 40f, textPaint)
        canvas.drawText("R ${expense.toInt()}", expenseRect.centerX(), expenseRect.top - 10f, amountPaint)
    }
}