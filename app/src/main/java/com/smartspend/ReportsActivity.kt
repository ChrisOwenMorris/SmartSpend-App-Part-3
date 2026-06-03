package com.smartspend

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.lang.Exception
import androidx.core.graphics.toColorInt
import com.smartspend.data.PieSlice


@SuppressLint("NewApi")
@RequiresApi(Build.VERSION_CODES.O)
class ReportsActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Track the currently selected period so button state can be highlighted
    private var currentPeriod = "month"

    // Permission request code for WRITE_EXTERNAL_STORAGE (API 24/25)
    private val REQUEST_WRITE_STORAGE = 0x1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = getSharedPreferences("smartspend_prefs", MODE_PRIVATE)
        val savedTheme = themePrefs.getString("theme_colour", "blue") ?: "blue"
        val themeRes = when (savedTheme) {
            "green"  -> R.style.Theme_SmartSpend_Green
            "purple" -> R.style.Theme_SmartSpend_Purple
            "orange" -> R.style.Theme_SmartSpend_Orange
            else     -> R.style.Theme_SmartSpend_Blue
        }
        setTheme(themeRes)
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
            // Start export flow: check permission then generate PDF
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // For API 24/25 we need WRITE_EXTERNAL_STORAGE at runtime
                if (hasWritePermission()) {
                    lifecycleScope.launch { exportCurrentPeriodReportToPdf() }
                } else {
                    requestWritePermission()
                }
            } else {
                // For API 29+ use MediaStore (no WRITE_EXTERNAL_STORAGE required)
                lifecycleScope.launch { exportCurrentPeriodReportToPdf() }
            }
        }

        // --- LOAD DEFAULT (MONTH) --- //
        updateButtonStates("month")
        loadReport("month", tvPeriodLabel, tvTotalAmount, tvComparison, rvTopMerchants)
    }

    private fun hasWritePermission(): Boolean {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWritePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_WRITE_STORAGE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lifecycleScope.launch { exportCurrentPeriodReportToPdf() }
            } else {
                Toast.makeText(this, "Storage permission is required to export PDF", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Export the currently selected period report to PDF.
     * This function fetches the same data used by loadReport(...) for the selected period,
     * captures the chart views as bitmaps, and writes a multipage PDF to Downloads.
     */
    @SuppressLint("DefaultLocale")
    private suspend fun exportCurrentPeriodReportToPdf() {
        withContext(Dispatchers.IO) {
            try {
                val today = LocalDate.now()
                val endDate = today.format(formatter)

                val daysBack: Long = when (currentPeriod) {
                    "week" -> 7L
                    "year" -> 365L
                    else -> 30L
                }
                val startDate = today.minusDays(daysBack).format(formatter)

                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ReportsActivity, "Not signed in - cannot export", Toast.LENGTH_LONG).show()
                    }
                    return@withContext
                }

                // Fetch entries and totals (reuse same DAO methods you already use)
                val expenses = db.expenseDao().getExpensesByDateRange(userId, startDate, endDate)
                val totalExpenses = db.expenseDao().getTotalByDateRange(userId, startDate, endDate)
                val totalIncome =
                    db.incomeDao().getTotalIncomeByDateRange(userId, startDate, endDate)

                // Capture chart views on UI thread
                val chartsBitmaps = withContext(Dispatchers.Main) {
                    val list = mutableListOf<Bitmap>()
                    val incomeExpenseChart = findViewById<IncomeExpenseBarChartView>(R.id.incomeExpenseChart)
                    val pieChart = findViewById<PieChartView>(R.id.pieChart)
                    val yAxisChart = findViewById<YAxisChartView>(R.id.yAxisChart)
                    val trendChart = findViewById<TrendChartView>(R.id.trendChart)

                    incomeExpenseChart?.let { captureViewBitmap(it)?.let { bmp -> list.add(bmp) } }
                    pieChart?.let { captureViewBitmap(it)?.let { bmp -> list.add(bmp) } }
                    yAxisChart?.let { captureViewBitmap(it)?.let { bmp -> list.add(bmp) } }
                    trendChart?.let { captureViewBitmap(it)?.let { bmp -> list.add(bmp) } }

                    list
                }

                // Create PDF
                val pdf = PdfDocument()
                val pageWidth = 595 // A4-ish width in points
                val pageHeight = 842

                // Page 1: Summary + totals + small entries list
                val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                val page1 = pdf.startPage(pageInfo1)
                val canvas1 = page1.canvas
                val paint = Paint().apply { color = Color.BLACK; textSize = 12f }
                val titlePaint = Paint().apply { color = Color.BLACK; textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

                var y = 40f
                canvas1.drawText("SmartSpend Report", 40f, y, titlePaint)
                y += 30f
                canvas1.drawText("Period: $currentPeriod", 40f, y, paint)
                y += 20f
                canvas1.drawText("Total Income: ${String.format("%.2f", totalIncome)}", 40f, y, paint)
                y += 20f
                canvas1.drawText("Total Expenses: ${String.format("%.2f", totalExpenses)}", 40f, y, paint)
                y += 20f
                canvas1.drawText("Net: ${String.format("%.2f", totalIncome - totalExpenses)}", 40f, y, paint)
                y += 30f

                canvas1.drawText("Entries (showing up to 30):", 40f, y, titlePaint)
                y += 24f

                // List entries (limit to avoid overflowing page)
                val maxLines = 30
                var lines = 0
                for (e in expenses) {
                    if (lines >= maxLines) {
                        canvas1.drawText("... (${expenses.size - maxLines} more entries)", 40f, y, paint)
                        y += 20f
                        break
                    }
                    val desc = e.description ?: ""
                    val date = e.date ?: ""
                    val amount = String.format("%.2f", e.amount)
                    val line = "$date  $desc  R$amount"
                    canvas1.drawText(line, 40f, y, paint)
                    y += 18f
                    lines++
                    // If we reach near bottom, stop listing
                    if (y > pageHeight - 80) {
                        canvas1.drawText("... (truncated)", 40f, y, paint)
                        break
                    }
                }

                pdf.finishPage(page1)

                // Additional pages: charts (one or two charts per page depending on size)
                var pageIndex = 2
                val margin = 40f
                val availableWidth = pageWidth - (margin * 2)
                val availableHeight = pageHeight - (margin * 2)

                var i = 0
                while (i < chartsBitmaps.size) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
                    val page = pdf.startPage(pageInfo)
                    val canvas = page.canvas

                    var yPos = margin
                    // Draw up to two charts per page vertically
                    val chartsPerPage = 2
                    for (j in 0 until chartsPerPage) {
                        if (i >= chartsBitmaps.size) break
                        val bmp = chartsBitmaps[i]
                        // Scale bitmap to fit width while preserving aspect ratio
                        val scale = minOf(availableWidth / bmp.width.toFloat(), (availableHeight / 2f) / bmp.height.toFloat())
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val left = margin
                        val top = yPos
                        val destRect = RectF(left, top, left + drawW, top + drawH)
                        canvas.drawBitmap(bmp, null, destRect, null)
                        yPos += drawH + 20f
                        i++
                    }

                    pdf.finishPage(page)
                    pageIndex++
                }

                // Save PDF to Downloads (API 24/25 path) or MediaStore for API 29+
                val fileName = "SmartSpend_Report_${currentPeriod}_${System.currentTimeMillis()}.pdf"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri).use { out ->
                            pdf.writeTo(out)
                        }
                        val firebaseRepo = com.smartspend.data.firebase.FirebaseRepository()
                        val downloadUrl = firebaseRepo.uploadPdfReport(uri, fileName)
                        if (downloadUrl != null) {
                            Log.d("ReportsActivity", "PDF uploaded to Firebase: $downloadUrl")
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ReportsActivity, "Report saved to Downloads and uploaded to cloud", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // fallback
                        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloads, fileName)
                        FileOutputStream(file).use { out -> pdf.writeTo(out) }
                        val fileUri = android.net.Uri.fromFile(file)
                        val firebaseRepo = com.smartspend.data.firebase.FirebaseRepository()
                        val downloadUrl = firebaseRepo.uploadPdfReport(fileUri, fileName)
                        if (downloadUrl != null) {
                            Log.d("ReportsActivity", "PDF uploaded to Firebase: $downloadUrl")
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ReportsActivity, "Report saved to Downloads and uploaded to cloud", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    // API < Q: write directly to Downloads (requires WRITE_EXTERNAL_STORAGE permission)
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloads.exists()) downloads.mkdirs()
                    val file = File(downloads, fileName)
                    FileOutputStream(file).use { out -> pdf.writeTo(out) }
                    val fileUri = android.net.Uri.fromFile(file)
                    val firebaseRepo = com.smartspend.data.firebase.FirebaseRepository()
                    firebaseRepo.uploadPdfReport(fileUri, fileName)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ReportsActivity, "Report saved to Downloads and uploaded to cloud", Toast.LENGTH_LONG).show()
                    }
                }

                pdf.close()

            } catch (ex: Exception) {
                Log.e("ReportsActivity", "Export failed: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReportsActivity, "Failed to export report: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Capture a view into a Bitmap. Returns null if capture fails.
     * Must be called on the main thread.
     */
    private fun captureViewBitmap(view: android.view.View): Bitmap? {
        return try {
            // Ensure view has been measured and laid out
            if (view.width == 0 || view.height == 0) {
                // Try to measure with unspecified specs
                val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                view.measure(widthSpec, heightSpec)
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val bg = view.background
            if (bg != null) {
                bg.draw(canvas)
            } else {
                canvas.drawColor(Color.WHITE)
            }
            view.draw(canvas)
            bmp
        } catch (e: Exception) {
            Log.e("ReportsActivity", "captureViewBitmap failed: ${e.message}", e)
            null
        }
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
                val totalSpent = pieData.sumOf { it.total }

                val colorPalette = listOf(
                    "#6A11CB".toColorInt(),
                    "#2575FC".toColorInt(),
                    "#FF5F6D".toColorInt(),
                    "#FFA17F".toColorInt(),
                    "#00C9FF".toColorInt()
                )

                val slices = pieData.mapIndexed { index, summary ->
                    // 1. Calculate the percentage here in the Activity
                    val rawPercentage = if (totalSpent > 0) (summary.total / totalSpent) * 100 else 0.0
                    val roundedPercentage = String.format(java.util.Locale.US, "%.1f", rawPercentage).toDouble()

                    // 2. Pass it into the constructor (This matches the new PieSlice data class)
                    PieSlice(
                        name = summary.categoryName,
                        value = summary.total,
                        percentage = roundedPercentage, // 🌟 New parameter added here
                        color = colorPalette[index % colorPalette.size]
                    )
                }

                Log.d("ReportsActivity", "Pie chart — ${slices.size} slices prepared with pre-calculated percentages.")
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
