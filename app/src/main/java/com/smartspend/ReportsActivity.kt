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

@SuppressLint("NewApi")
@RequiresApi(Build.VERSION_CODES.O)
class ReportsActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // --- EXISTING NAVIGATION CODE --- //
        NavigationHelper.setupMenu(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@ReportsActivity)
            }
        })

        // --- VIEWS --- //
        val btnWeek = findViewById<Button>(R.id.btnWeek)
        val btnMonth = findViewById<Button>(R.id.btnMonth)
        val btnYear = findViewById<Button>(R.id.btnYear)
        val tvPeriodLabel = findViewById<TextView>(R.id.tvPeriodLabel)
        val tvTotalAmount = findViewById<TextView>(R.id.tvTotalAmount)
        val tvComparison = findViewById<TextView>(R.id.tvComparison)
        val rvTopMerchants = findViewById<RecyclerView>(R.id.rvTopMerchants)
        val btnExport = findViewById<Button>(R.id.btnExport)

        // --- SETUP RECYCLERVIEW --- //
        rvTopMerchants.layoutManager = LinearLayoutManager(this)

        // --- LOAD DEFAULT VIEW (MONTH) --- //
        loadReport("month", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)

        // --- PERIOD BUTTON CLICKS --- //
        btnWeek.setOnClickListener {
            loadReport("week", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
        }
        btnMonth.setOnClickListener {
            loadReport("month", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
        }
        btnYear.setOnClickListener {
            loadReport("year", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
        }

        // --- EXPORT BUTTON --- //
        btnExport.setOnClickListener {
            // PDF export can be added later
        }
    }

    private fun loadReport(
        period: String,
        tvPeriodLabel: TextView,
        tvTotalAmount: TextView,
        tvComparison: TextView,
        rvTopMerchants: RecyclerView
    ) {
        val today = LocalDate.now()
        val endDate = today.format(formatter)
        val daysBack: Long
        val label: String

        when (period) {
            "week" -> {
                daysBack = 7L
                label = getString(R.string.btn_week)
            }
            "year" -> {
                daysBack = 365L
                label = getString(R.string.btn_year)
            }
            else -> {
                daysBack = 30L
                label = getString(R.string.btn_month)
            }
        }

        val startDate = today.minusDays(daysBack).format(formatter)
        val prevStart = today.minusDays(daysBack * 2).format(formatter)

        lifecycleScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                Log.d("ReportsActivity", "Loading report for userId: $userId")

                // 1. FETCH BASE DATA FROM DB
                val total = db.expenseDao().getTotalByDateRange(userId, startDate, endDate)
                val prevTotal = db.expenseDao().getTotalByDateRange(userId, prevStart, startDate)
                val topCategories = db.expenseDao().getTopCategoriesWithNames(userId, startDate, endDate)

                // 2. UPDATE SUMMARY TEXT VIEWS
                tvPeriodLabel.text = label
                val totalNum = total.toString().toDoubleOrNull() ?: 0.0
                tvTotalAmount.text = getString(R.string.amount_format, totalNum)

                val previousTotal = prevTotal.toString().toDoubleOrNull() ?: 0.0
                val change = if (previousTotal > 0) {
                    ((totalNum - previousTotal) / previousTotal * 100)
                } else {
                    0.0
                }
                val sign = if (change >= 0) "+" else ""
                tvComparison.text = getString(R.string.comparison_format, sign, change)

                // 3. INCOME VS EXPENSES BAR CHART
                val totalExpenses = db.expenseDao().getTotalByDateRange(userId, startDate, endDate)
                val totalIncome = db.incomeDao().getTotalIncomeByDateRange(userId, startDate, endDate)
                Log.d("ReportsActivity", "Bar chart — income: $totalIncome, expenses: $totalExpenses")

                val incomeExpenseChart = findViewById<IncomeExpenseBarChartView>(R.id.incomeExpenseChart)
                incomeExpenseChart?.setData(totalIncome, totalExpenses)

                // 4. PIE CHART — SPENDING BY CATEGORY
                val pieData = db.expenseDao().getExpensesGroupedByCategory(userId, startDate, endDate)
                val colorPalette = listOf(
                    "#6A11CB".toColorInt(),
                    "#2575FC".toColorInt(),
                    "#FF5F6D".toColorInt()
                )
                val slices = pieData.mapIndexed { index, summary ->
                    PieSlice(
                        name = summary.categoryName,
                        value = summary.total,
                        color = colorPalette[index % colorPalette.size]
                    )
                }
                Log.d("ReportsActivity", "Pie chart — ${slices.size} category slices")
                findViewById<PieChartView>(R.id.pieChart)?.setData(slices)

                // 5. TREND CHART — 6 MONTHS
                val sixMonthsAgo = LocalDate.now().minusMonths(6).format(formatter)
                val trendData = db.expenseDao().getMonthlyTrends(userId, sixMonthsAgo)
                Log.d("ReportsActivity", "Trend chart — ${trendData.size} month(s) of data")
                val trendChart = findViewById<TrendChartView>(R.id.trendChart)
                trendChart?.setData(trendData)

                // 6. TOP CATEGORIES LIST
                rvTopMerchants.adapter = TopCategoriesAdapter(topCategories)

            } catch (e: Exception) {
                Log.e("ReportsActivity", "Error loading report: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}