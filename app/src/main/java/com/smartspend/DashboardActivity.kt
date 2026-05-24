package com.smartspend

import android.content.Intent
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
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        NavigationHelper.setupMenu(this)

        val rvRecentExpenses = findViewById<RecyclerView>(R.id.rvRecentExpenses)
        rvRecentExpenses.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnQuickAddExpense).setOnClickListener {
            startActivity(Intent(this, ExpenseActivity::class.java))
        }

        findViewById<Button>(R.id.btnSetBudget).setOnClickListener {
            showSetBudgetDialog()
        }

        loadDashboardData()

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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        loadDashboardData()
    }

    private fun showSetBudgetDialog() {
        val prefs = getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)

        //Month selection for budget
        // Build list of all 12 months for user to pick from
        val months = arrayOf(
            "January", "February", "March", "April",
            "May", "June", "July", "August",
            "September", "October", "November", "December"
        )

        // Default to current month
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        var selectedMonth = currentMonth

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_monthly_budget))
            .setSingleChoiceItems(months, currentMonth) { _, which ->
                selectedMonth = which
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                //Check if budget already set for selected month
                val budgetKey = "monthly_budget_$selectedMonth"
                val existingBudget = prefs.getFloat(budgetKey, 0f)

                if (existingBudget > 0f) {
                    // Budget already exists for this month — warn user
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.budget_already_set_title))
                        .setMessage(
                            "A budget of R%.2f is already set for %s. ".format(
                                existingBudget, months[selectedMonth]
                            ) + "If you made a mistake, please add the correction as an Income entry. " +
                                    "Do you still want to update this budget?"
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
        prefs: android.content.SharedPreferences,
        monthIndex: Int,
        monthName: String
    ) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.budget_hint)
        }

        // padding around the input field
        val container = LinearLayout(this).apply {
            setPadding(48, 16, 48, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Budget for $monthName")
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

                // Save budget under the specific month key

                prefs.edit().apply {
                    putFloat("monthly_budget_$monthIndex", budget)
                    // Also save as current budget if it matches current month
                    val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                    if (monthIndex == currentMonth) {
                        putFloat("monthly_budget", budget)
                    }
                    apply()
                }

                loadDashboardData()
                Toast.makeText(
                    this,
                    "Budget set for $monthName!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH)
                val currentYear = cal.get(Calendar.YEAR)

                // Current-month date range for totals
                val startDate = "%04d-%02d-01".format(currentYear, currentMonth + 1)
                val endDate = "%04d-%02d-%02d".format(
                    currentYear, currentMonth + 1, cal.get(Calendar.DAY_OF_MONTH)
                )

                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                Log.d("Dashboard", "Loading data for user: $userId")

                val totalExpenses = db.expenseDao().getTotalByDateRange(userId, startDate, endDate)
                val totalIncome = db.incomeDao().getTotalIncomeByDateRange(userId, startDate, endDate)
                Log.d("DashboardActivity", "Totals — expenses: $totalExpenses, income: $totalIncome (range: $startDate to $endDate)")

                // All expense-table rows for the recent list and count
                val allTransactions = db.expenseDao().getAllExpenses(userId)
                Log.d("Dashboard", "Expense count: ${allTransactions.size}")
                Log.d("Dashboard", "Total spent: $totalExpenses")
                val count = allTransactions.size
                val sorted = allTransactions.sortedByDescending { it.date }
                val recentTransactions = sorted.take(5)

                // Get budget for current month
                val prefs = getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)
                val savedBudget = prefs.getFloat(
                    "monthly_budget_$currentMonth",
                    prefs.getFloat("monthly_budget", 0f)
                ).toDouble()

                val budget = if (savedBudget > 0) savedBudget else 0.0
                val remaining = if (budget > 0) budget - totalExpenses else 0.0
                val progress = if (budget > 0) {
                    ((totalExpenses / budget) * 100).toInt().coerceIn(0, 100)
                } else 0

                runOnUiThread {
                    findViewById<TextView>(R.id.tvTotalBudget)?.text =
                        getString(R.string.amount_format, budget)

                    findViewById<TextView>(R.id.tvTotalSpent)?.text =
                        getString(R.string.amount_format, totalExpenses)

                    findViewById<TextView>(R.id.tvRemaining)?.text =
                        getString(R.string.amount_format, remaining)

                    findViewById<TextView>(R.id.tvTransactionCount)?.text =
                        getString(R.string.transaction_count, count)

                    val latestDesc = sorted.firstOrNull()?.description ?: "None"
                    findViewById<TextView>(R.id.tvLatestExpense)?.text = "Latest: $latestDesc"

                    // Show income total from the proper income table
                    findViewById<TextView>(R.id.tvTotalIncome)?.apply {
                        text = getString(R.string.amount_format, totalIncome)
                        setTextColor(getColor(R.color.status_success))
                    }

                    val progressBar = findViewById<ProgressBar>(R.id.progressBudget)
                    progressBar?.progress = progress
                    progressBar?.progressTintList =
                        android.content.res.ColorStateList.valueOf(
                            when {
                                progress >= 100 -> getColor(R.color.status_danger)
                                progress >= 80  -> getColor(R.color.status_warning)
                                else            -> getColor(R.color.status_success)
                            }
                        )

                    val rvRecentExpenses = findViewById<RecyclerView>(R.id.rvRecentExpenses)
                    rvRecentExpenses.adapter = RecentExpensesAdapter(recentTransactions)
                }

            } catch (e: Exception) {
                Log.e("DashboardActivity", "Error loading dashboard data: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}