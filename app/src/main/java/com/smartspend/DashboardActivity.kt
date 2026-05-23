package com.smartspend

import android.content.Intent
import android.os.Bundle
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
                val allTransactions = db.expenseDao().getAllExpenses()

                //Separate incomes from expenses

                val expenses = allTransactions.filter { transaction ->
                    transaction.description != "Income" &&
                            !transaction.description.startsWith("+")
                }

                val incomes = allTransactions.filter { transaction ->
                    transaction.description == "Income" ||
                            transaction.description.startsWith("+")
                }

                val totalExpenses = expenses.sumOf { it.amount }
                val totalIncome = incomes.sumOf { it.amount }
                val count = allTransactions.size

                // Get budget for current month
                val prefs = getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                val savedBudget = prefs.getFloat(
                    "monthly_budget_$currentMonth",
                    prefs.getFloat("monthly_budget", 0f)
                ).toDouble()

                val goal = db.goalDao().getFeaturedGoal()
                val budget = if (savedBudget > 0) savedBudget
                else (goal?.targetAmount ?: 0.0)

                val remaining = budget - totalExpenses
                val progress = if (budget > 0) {
                    ((totalExpenses / budget) * 100).toInt().coerceIn(0, 100)
                } else 0

                //Combine all transactions for the recent list

                val recentTransactions = allTransactions.takeLast(5).reversed()

                runOnUiThread {
                    findViewById<TextView>(R.id.tvTotalBudget)?.text =
                        getString(R.string.amount_format, budget)

                    findViewById<TextView>(R.id.tvTotalSpent)?.text =
                        getString(R.string.amount_format, totalExpenses)

                    findViewById<TextView>(R.id.tvRemaining)?.text =
                        getString(R.string.amount_format, remaining)

                    findViewById<TextView>(R.id.tvTransactionCount)?.text =
                        getString(R.string.transaction_count, count)

                    // Show income total separately in green
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

                    // Pass both income flag and transactions to adapter
                    // so adapter can colour incomes green and expenses red
                    val rvRecentExpenses = findViewById<RecyclerView>(R.id.rvRecentExpenses)
                    rvRecentExpenses.adapter = RecentExpensesAdapter(recentTransactions)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}