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
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var recentExpensesAdapter: RecentExpensesAdapter

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

    override fun onNewIntent(intent: android.content.Intent) {
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
        lifecycleScope.launch {
            try {
                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH)
                val currentYear = cal.get(Calendar.YEAR)

                val startDate = "%04d-%02d-01".format(currentYear, currentMonth + 1)
                val endDate = "%04d-%02d-%02d".format(
                    currentYear, currentMonth + 1, cal.get(Calendar.DAY_OF_MONTH)
                )

                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                Log.d("Dashboard", "Loading data for user: $userId")

                val totalExpenses = db.expenseDao()
                    .getTotalByDateRange(userId, startDate, endDate)
                val totalIncome = db.incomeDao()
                    .getTotalIncomeByDateRange(userId, startDate, endDate)

                val allTransactions = db.expenseDao().getAllExpenses(userId)
                val sorted = allTransactions.sortedByDescending { it.date }
                val recentTransactions = sorted.take(5)
                val count = allTransactions.size

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
                        getString(R.string.amount_format, budget.toFloat())

                    findViewById<TextView>(R.id.tvTotalSpent)?.text =
                        getString(R.string.amount_format, totalExpenses.toFloat())

                    findViewById<TextView>(R.id.tvRemaining)?.text =
                        getString(R.string.amount_format, remaining.toFloat())

                    findViewById<TextView>(R.id.tvTransactionCount)?.text =
                        getString(R.string.transaction_count, count)

                    val latestDesc = sorted.firstOrNull()?.description
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
                }

            } catch (e: Exception) {
                Log.e("DashboardActivity", "Error loading dashboard: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}