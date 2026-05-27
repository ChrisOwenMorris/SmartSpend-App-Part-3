package com.smartspend

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.annotation.SuppressLint
import androidx.core.graphics.toColorInt
import com.smartspend.data.dao.CategoryWithTotal

@SuppressLint("NewApi")
@RequiresApi(Build.VERSION_CODES.O)
class ReportsActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Track the currently selected period so button state can be highlighted
    private var currentPeriod = "month"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // --- NAVIGATION --- //
        NavigationHelper.setupMenu(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@ReportsActivity)
            }
        })

        // --- VIEWS --- //
        val btnWeek  = findViewById<Button>(R.id.btnWeek)
        val btnMonth = findViewById<Button>(R.id.btnMonth)
        val btnYear  = findViewById<Button>(R.id.btnYear)
        val tvPeriodLabel  = findViewById<TextView>(R.id.tvPeriodLabel)
        val tvTotalAmount  = findViewById<TextView>(R.id.tvTotalAmount)
        val tvComparison   = findViewById<TextView>(R.id.tvComparison)
        val rvTopMerchants = findViewById<RecyclerView>(R.id.rvTopMerchants)
        val btnExport      = findViewById<Button>(R.id.btnExport)

        // --- RECYCLERVIEW --- //
        rvTopMerchants.layoutManager = LinearLayoutManager(this)

        // Helper to update button visual state
        fun updateButtonStates(selected: String) {
            val alpha = 0.5f
            btnWeek.alpha  = if (selected == "week")  1f else alpha
            btnMonth.alpha = if (selected == "month") 1f else alpha
            btnYear.alpha  = if (selected == "year")  1f else alpha
        }

        // --- PERIOD BUTTON CLICKS --- //
        btnWeek.setOnClickListener {
            currentPeriod = "week"
            updateButtonStates("week")
            loadReport("week", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
        }
        btnMonth.setOnClickListener {
            currentPeriod = "month"
            updateButtonStates("month")
            loadReport("month", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
        }
        btnYear.setOnClickListener {
            currentPeriod = "year"
            updateButtonStates("year")
            loadReport("year", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
        }

        // --- EXPORT (Member 4) --- //
        btnExport.setOnClickListener {
            // PDF export functionality handled by Member 4
        }

        // --- LOAD DEFAULT (MONTH) --- //
        updateButtonStates("month")
        loadReport("month", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
    }

    private fun loadReport(
        period: String,
        tvPeriodLabel: TextView,
        tvTotalAmount: TextView,
        tvComparison: TextView,
        rvTopMerchants: RecyclerView
    ) {
        val today    = LocalDate.now()
        val endDate  = today.format(formatter)

        // Determine look-back window and display label based on selected period
        val daysBack: Long
        val label: String
        when (period) {
            "week" -> {
                daysBack = 7L
                label    = getString(R.string.btn_week)
            }
            "year" -> {
                daysBack = 365L
                label    = getString(R.string.btn_year)
            }
            else -> {                          // default = month
                daysBack = 30L
                label    = getString(R.string.btn_month)
            }
        }

        val startDate = today.minusDays(daysBack).format(formatter)
        val prevStart = today.minusDays(daysBack * 2).format(formatter)

        Log.d("ReportsActivity", "loadReport($period) — $startDate → $endDate")

        lifecycleScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
                    Log.w("ReportsActivity", "No authenticated user — aborting report load")
                    return@launch
                }

                // ── 1. SUMMARY CARD ──────────────────────────────────────────
                val totalExpenses  = db.expenseDao().getTotalByDateRange(userId, startDate, endDate) ?: 0.0
                val previousTotal  = db.expenseDao().getTotalByDateRange(userId, prevStart, startDate) ?: 0.0
                val topCategories  = db.expenseDao().getTopCategoriesWithNames(userId, startDate, endDate) ?: emptyList()

                tvPeriodLabel.text = label
                tvTotalAmount.text = getString(R.string.amount_format, totalExpenses)

                val changePct = if (previousTotal > 0.0) {
                    (totalExpenses - previousTotal) / previousTotal * 100
                } else {
                    0.0
                }
                val sign = if (changePct >= 0) "+" else ""
                tvComparison.text = getString(R.string.comparison_format, sign, changePct)

                Log.d("ReportsActivity", "Summary — total: $totalExpenses, prev: $previousTotal, Δ: $sign${String.format("%.1f", changePct)}%")

                // ── 2. INCOME VS EXPENSES BAR CHART ──────────────────────────
                val totalIncome = db.incomeDao().getTotalIncomeByDateRange(userId, startDate, endDate) ?: 0.0
                Log.d("ReportsActivity", "Bar chart — income: $totalIncome, expenses: $totalExpenses")

                val incomeExpenseChart = findViewById<IncomeExpenseBarChartView>(R.id.incomeExpenseChart)
                incomeExpenseChart?.setData(totalIncome, totalExpenses)

                // ── 3. PIE CHART — SPENDING BY CATEGORY ──────────────────────
                val pieData = db.expenseDao().getExpensesGroupedByCategory(userId, startDate, endDate) ?: emptyList()
                val colorPalette = listOf(
                    "#6A11CB".toColorInt(),
                    "#2575FC".toColorInt(),
                    "#FF5F6D".toColorInt(),
                    "#FFA17F".toColorInt(),
                    "#00C9FF".toColorInt()
                )
                val slices = pieData.mapIndexed { index, summary ->
                    PieSlice(
                        name  = summary.categoryName,
                        value = summary.total,
                        color = colorPalette[index % colorPalette.size]
                    )
                }
                Log.d("ReportsActivity", "Pie chart — ${slices.size} slices for $period")
                findViewById<PieChartView>(R.id.pieChart)?.setData(slices)

                // ── 4. Y-AXIS CHART — SPENDING BY CATEGORY (BAR WITH Y AXIS) ──
                val yAxisData = pieData.map { summary -> Pair(summary.categoryName, summary.total) }
                Log.d("ReportsActivity", "Y-axis chart — ${yAxisData.size} categories for $period")
                findViewById<YAxisChartView>(R.id.yAxisChart)?.setData(yAxisData)

                // ── 5. TREND CHART — period-aware window ──────────────────────
                // Show a trend that matches the selected period:
                //   week  → last 7 days  (daily breakdown)
                //   month → last 6 months (monthly breakdown — existing behaviour)
                //   year  → last 12 months
                val trendStartDate = when (period) {
                    "week"  -> today.minusWeeks(1).format(formatter)
                    "year"  -> today.minusMonths(12).format(formatter)
                    else    -> today.minusMonths(6).format(formatter)   // month default
                }
                val trendData = db.expenseDao().getMonthlyTrends(userId, trendStartDate) ?: emptyList()
                Log.d("ReportsActivity", "Trend chart — ${trendData.size} point(s) for $period from $trendStartDate")
                findViewById<TrendChartView>(R.id.trendChart)?.setData(trendData)

                // ── 5. TOP CATEGORIES RECYCLERVIEW ───────────────────────────
                rvTopMerchants.adapter = TopCategoriesAdapter(topCategories)

            } catch (e: Exception) {
                Log.e("ReportsActivity", "Error loading report: ${e.message}", e)
            }
        }
    }
}
