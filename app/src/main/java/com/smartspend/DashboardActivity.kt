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
import com.smartspend.data.firebase.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var recentExpensesAdapter: RecentExpensesAdapter

    // TRACKER: Holds a reference to the active loading task
    private var dashboardLoadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        NavigationHelper.setupMenu(this)

        val rvRecentExpenses = findViewById<RecyclerView>(R.id.rvRecentExpenses)
        rvRecentExpenses.layoutManager = LinearLayoutManager(this)

        recentExpensesAdapter = RecentExpensesAdapter()
        rvRecentExpenses.adapter = recentExpensesAdapter

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
                    Toast.makeText(
                        this,
                        getString(R.string.budget_empty_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val budget = budgetString.toFloatOrNull()
                if (budget == null || budget <= 0f) {
                    Toast.makeText(
                        this,
                        getString(R.string.budget_invalid_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val editor = prefs.edit()
                editor.putFloat("monthly_budget_$monthIndex", budget)
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                if (monthIndex == currentMonth) {
                    editor.putFloat("monthly_budget", budget)
                }
                editor.apply()

                // 🌟 MINE: Preserve your Firebase budget cloud sync here!
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
                Toast.makeText(
                    this,
                    getString(R.string.budget_set_for_month, monthName),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadDashboardData() {
        // 🌟 THEIRS: Cancel any identical data loading tasks that are already running for stability
        dashboardLoadJob?.cancel()

        // 🌟 THEIRS: Assign the optimized async thread task
        dashboardLoadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH)
                val currentYear = cal.get(Calendar.YEAR)

                val startDate = "%04d-%02d-01".format(currentYear, currentMonth + 1)
                val endDate = "%04d-%02d-%02d".format(
                    currentYear, currentMonth + 1, cal.get(Calendar.DAY_OF_MONTH)
                )

                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                Log.d("Dashboard", "Loading combined transactional data for user: $userId")

                // 1. Load calculated metrics based on date boundaries
                val totalExpenses = db.expenseDao().getTotalByDateRange(userId, startDate, endDate)

                val totalIncome = try {
                    db.incomeDao().getTotalIncomeByDateRange(userId, startDate, endDate)
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error calling incomeDao stream calculation", e)
                    0.0
                }

                // 2. Load lists from both tables to merge into a single statement feed
                val rawExpensesWithCategory = db.expenseDao().getAllExpensesWithCategoryNames(userId)

                val rawIncomes = try {
                    db.incomeDao().getAllIncome(userId)
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error loading row list from income Dao collection structure", e)
                    emptyList()
                }

                // 3. Map Income list entities into general compatible shapes matching adapter item expectations
                val convertedIncomes = rawIncomes.map { income: com.smartspend.data.entity.Income ->
                    Expense(
                        userId = income.userId,
                        amount = income.amount,
                        description = if (income.source.isNullOrEmpty()) "Income" else income.source,
                        date = income.date,
                        startTime = "00:00",
                        endTime = "00:00",
                        categoryId = -1,
                        receiptPath = null,
                        imagePath = income.imagePath,
                        createdAt = income.createdAt,
                    )
                }

                val processedExpenses = rawExpensesWithCategory.map { item ->
                    val rawExpense = item.expense
                    val catName = item.categoryName

                    // Format output title as: "Category Name (Custom Note Details)" or just "Category Name"
                    val displayDescription = if (rawExpense.description.isEmpty() || rawExpense.description == "Expense") {
                        catName
                    } else {
                        "$catName (${rawExpense.description})"
                    }

                    rawExpense.copy(description = displayDescription)
                }

                // 4. Merge streams, sort, and slice to extract the newest 5 items
                val masterFeedList = processedExpenses + convertedIncomes
                val sortedTransactions = masterFeedList.sortedByDescending { it.createdAt }
                val recentTransactions = sortedTransactions.take(5)
                val totalTransactionsCount = masterFeedList.size

                // 5. Shared preferences budget math limits calculations
                val prefs = getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)
                val savedBudget = prefs.getFloat(
                    "monthly_budget_$currentMonth",
                    prefs.getFloat("monthly_budget", 0f)
                ).toDouble()

                val budget = if (savedBudget > 0) savedBudget else 0.0

                // INTEGRATED BALANCE MATH FORMULA
                val remaining = if (budget > 0) {
                    (budget - totalExpenses) + totalIncome
                } else {
                    0.0 + totalIncome
                }

                val progress = if (budget > 0) {
                    ((totalExpenses / budget) * 100).toInt().coerceIn(0, 100)
                } else 0

                // Context-switch cleanly back to the Main thread thread-pool for layout operations
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tvTotalBudget)?.text =
                        getString(R.string.amount_format, budget.toFloat())

                    findViewById<TextView>(R.id.tvTotalSpent)?.text =
                        getString(R.string.amount_format, totalExpenses.toFloat())

                    findViewById<TextView>(R.id.tvRemaining)?.text =
                        getString(R.string.amount_format, remaining.toFloat())

                    findViewById<TextView>(R.id.tvTransactionCount)?.text =
                        getString(R.string.transaction_count, totalTransactionsCount)

                    val latestDesc = sortedTransactions.firstOrNull()?.description
                        ?: getString(R.string.none_label)
                    findViewById<TextView>(R.id.tvLatestExpense)?.text =
                        getString(R.string.latest_format, latestDesc)

                    findViewById<TextView>(R.id.tvTotalIncome)?.apply {
                        text = getString(R.string.amount_format, totalIncome.toFloat())
                        setTextColor(getColor(R.color.status_success))
                    }

                    val progressBar = findViewById<ProgressBar>(R.id.progressBudget)
                    progressBar?.progress = progress
                    val progressDrawable = when {
                        progress >= 100 -> R.drawable.progress_bar_danger
                        progress >= 80  -> R.drawable.progress_bar_warning
                        else            -> R.drawable.card_gradient_background
                    }
                    progressBar?.progressDrawable =
                        androidx.core.content.ContextCompat.getDrawable(
                            this@DashboardActivity,
                            progressDrawable
                        )

                    recentExpensesAdapter.updateData(recentTransactions)
                    Log.d("Dashboard", "Successfully rendered historical update pass containing ${recentTransactions.size} blended transactions.")
                }

            } catch (e: Exception) {
                Log.e("DashboardActivity", "Error loading dashboard metrics: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}