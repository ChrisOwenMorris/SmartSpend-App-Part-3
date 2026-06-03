package com.smartspend

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.entity.Expense
import com.smartspend.data.entity.ExpenseWithCategory
import com.smartspend.data.firebase.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlinx.coroutines.flow.first
import androidx.core.content.edit
import com.smartspend.data.entity.Income

/**
 * Main dashboard screen showing budget summary, spending totals, and recent transactions.
 * Refreshes data every time the activity resumes or receives a new intent.
 */
class DashboardActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var transactionAdapter: TransactionAdapter
    private var dashboardLoadJob: Job? = null
    private var transactions: List<ExpenseWithCategory> = emptyList()

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
        setContentView(R.layout.activity_dashboard)

        NavigationHelper.setupMenu(this)

        // Restore saved theme colour on every launch
        val prefs = getSharedPreferences("smartspend_prefs", MODE_PRIVATE)
        val savedColour = prefs.getString("theme_colour", "blue") ?: "blue"
        val colorHex = when (savedColour) {
            "green"  -> "#4CAF50"
            "purple" -> "#9C27B0"
            "orange" -> "#FF9800"
            else     -> "#1976D2"
        }
        window.statusBarColor = android.graphics.Color.parseColor(colorHex)

        val rvRecentExpenses = findViewById<RecyclerView>(R.id.rvRecentExpenses)
        rvRecentExpenses.layoutManager = LinearLayoutManager(this)

        transactionAdapter = TransactionAdapter(
            transactions = emptyList(),
            onImageClick = { receiptPath, imagePath ->
                val intent = Intent(this, ReceiptPreviewActivity::class.java)
                intent.putExtra("receiptPath", receiptPath)
                intent.putExtra("imagePath", imagePath)
                startActivity(intent)
            },
            onItemClick = null
        )
        rvRecentExpenses.adapter = transactionAdapter

        findViewById<Button>(R.id.btnQuickAddExpense).setOnClickListener {
            startActivity(Intent(this, ExpenseActivity::class.java))
        }

        findViewById<Button>(R.id.btnSetBudget).setOnClickListener {
            showSetBudgetDialog()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadDashboardData()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        loadDashboardData()
    }

    /**
     * Shows a month-picker dialog, then prompts for a budget amount and persists it to SharedPreferences and Firestore.
     */
    private fun showSetBudgetDialog() {
        val prefs = getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)

        val months = arrayOf(
            "January", "February", "March", "April",
            "May", "June", "July", "August",
            "September", "October", "November", "December"
        )

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        var selectedMonth = currentMonth

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_monthly_budget))
            .setSingleChoiceItems(months, currentMonth) { _, which ->
                selectedMonth = which
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val budgetKey = "monthly_budget_$selectedMonth"
                val existingBudget = prefs.getFloat(budgetKey, 0f)

                if (existingBudget > 0f) {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.budget_already_set_title))
                        .setMessage(
                            getString(
                                R.string.budget_already_set_message_format,
                                existingBudget,
                                months[selectedMonth]
                            )
                        )
                        .setPositiveButton(getString(R.string.update_anyway)) { _, _ ->
                            openBudgetAmountDialog(prefs, selectedMonth, months[selectedMonth])
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                } else {
                    openBudgetAmountDialog(prefs, selectedMonth, months[selectedMonth])
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openBudgetAmountDialog(
        prefs: SharedPreferences,
        monthIndex: Int,
        monthName: String
    ) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.budget_hint)
        }

        val container = LinearLayout(this).apply {
            setPadding(48, 16, 48, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.budget_for_month, monthName))
            .setMessage(getString(R.string.budget_dialog_message))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val budgetString = input.text.toString().trim()

                if (budgetString.isEmpty()) {
                    Toast.makeText(this, getString(R.string.budget_empty_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val budget = budgetString.toFloatOrNull()
                if (budget == null || budget <= 0f) {
                    Toast.makeText(this, getString(R.string.budget_invalid_error), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                prefs.edit {
                    putFloat("monthly_budget_$monthIndex", budget)
                    val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                    if (monthIndex == currentMonth) {
                        putFloat("monthly_budget", budget)
                    }
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val firebaseRepo = FirebaseRepository()
                        firebaseRepo.saveBudget(monthIndex, budget.toDouble())
                        Log.d("Dashboard", "Budget synced to Firebase successfully")
                    } catch (e: Exception) {
                        Log.e("Dashboard", "Failed to sync budget to Firebase", e)
                    }
                }

                loadDashboardData()
                Toast.makeText(this, getString(R.string.budget_set_for_month, monthName), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Fetches expense and income data for the current month and updates all dashboard UI components.
     * Cancels any previous in-flight load before starting a new one.
     */
    private fun loadDashboardData() {
        dashboardLoadJob?.cancel()

        dashboardLoadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH)
                val currentYear = cal.get(Calendar.YEAR)

                // Date formatting for ranges
                val startDate = "%04d-%02d-01".format(currentYear, currentMonth + 1)
                val endDate = "%04d-%02d-%02d".format(
                    currentYear, currentMonth + 1, cal.get(Calendar.DAY_OF_MONTH)
                )

                // 1. Fetch only the data we need (totals and limited list)
                val totalExpenses = db.expenseDao().getTotalByDateRange(userId, startDate, endDate)
                val totalIncome = try { db.incomeDao().getTotalIncomeByDateRange(userId, startDate, endDate) } catch (e: Exception) { 0.0 }

                // Using the optimized DAO queries (LIMIT 5)
                val recentExpenses = db.expenseDao().getRecentExpensesWithCategory(userId)
                val recentIncomes = db.incomeDao().getRecentIncome(userId)

                // 2. Process and Map Incomes to common format
                val convertedIncomesWithCategory = recentIncomes.map { income ->
                    ExpenseWithCategory(
                        expense = Expense(
                            expenseId = income.id,
                            userId = income.userId,
                            amount = income.amount,
                            // Fallback: Use income.description if present, otherwise source, otherwise "Income"
                            description = income.description ?: income.source.ifBlank { "Income" },
                            date = income.date,
                            startTime = "00:00",
                            endTime = "00:00",
                            categoryId = -1,
                            receiptPath = null,
                            imagePath = income.imagePath,
                            createdAt = income.createdAt
                        ),
                        categoryName = income.source.ifBlank { "Income" }
                    )
                }

                // 3. Process Expenses (Now fully handled by Room!)
                val processedExpenses = recentExpenses.map { item ->
                    // The Room query with JOIN already populates item.categoryName
                    // We just ensure the description fallback logic is applied
                    val displayDescription = if (item.expense.description.isBlank()) item.categoryName else item.expense.description

                    // Return a new object with the corrected description
                    ExpenseWithCategory(
                        expense = item.expense.copy(description = displayDescription),
                        categoryName = item.categoryName
                    )
                }

                // 4. Combine, sort, and slice the final list
                val masterFeedList = (processedExpenses + convertedIncomesWithCategory)
                    .sortedByDescending { it.expense.createdAt }
                    .take(5)

                // 5. Budget Calculation Logic
                val prefs = getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)
                val savedBudget = prefs.getFloat("monthly_budget_$currentMonth", prefs.getFloat("monthly_budget", 0f)).toDouble()
                val budget = if (savedBudget > 0) savedBudget else 0.0
                val remaining = (budget - totalExpenses) + totalIncome
                val progress = if (budget > 0) ((totalExpenses / budget) * 100).toInt().coerceIn(0, 100) else 0

                // 6. UI Updates on Main Thread
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tvTotalBudget)?.text = getString(R.string.amount_format, budget.toFloat())
                    findViewById<TextView>(R.id.tvTotalSpent)?.text = getString(R.string.amount_format, totalExpenses.toFloat())
                    findViewById<TextView>(R.id.tvRemaining)?.text = getString(R.string.amount_format, remaining.toFloat())
                    findViewById<TextView>(R.id.tvTotalIncome)?.text = getString(R.string.amount_format, totalIncome.toFloat())
                    findViewById<TextView>(R.id.tvLatestExpense)?.text = getString(R.string.latest_format, masterFeedList.firstOrNull()?.expense?.description ?: "None")

                    val progressBar = findViewById<ProgressBar>(R.id.progressBudget)
                    progressBar?.progress = progress

                    // Update the adapter with the processed list
                    transactionAdapter.updateData(masterFeedList)
                }

            } catch (e: Exception) {
                Log.e("DashboardActivity", "Error loading dashboard metrics: ${e.message}")
            }
        }
    }
}