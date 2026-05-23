package com.smartspend

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.smartspend.data.entity.Expense
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ExpenseActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var etAmount: EditText
    private lateinit var etDate: EditText
    private lateinit var etDescription: EditText

    private lateinit var tvSummaryAmount: TextView
    private lateinit var tvSummaryDate: TextView

    private lateinit var btnExpense: Button
    private lateinit var btnIncome: Button
    private lateinit var btnSave: Button

    private lateinit var spCategory: Spinner

    private var receiptPath: String? = null

    private var isExpense = true

    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_expense)

        NavigationHelper.setupMenu(this)

        receiptPath = intent.getStringExtra("receiptPath")

        bindViews()

        setupDatePicker()

        setupListeners()

        setupCategorySpinner()

        updateDate()

        updateSummary()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    NavigationHelper.goToDashboard(this@ExpenseActivity)
                }
            }
        )

        btnSave.setOnClickListener {
            saveExpense()
        }
    }

    private fun bindViews() {

        etAmount = findViewById(R.id.etAmount)
        etDate = findViewById(R.id.etDate)
        etDescription = findViewById(R.id.etDescription)

        tvSummaryAmount = findViewById(R.id.tvSummaryAmount)
        tvSummaryDate = findViewById(R.id.tvSummaryDate)

        btnExpense = findViewById(R.id.btnExpense)
        btnIncome = findViewById(R.id.btnIncome)

        btnSave = findViewById(R.id.btnSave)

        spCategory = findViewById(R.id.spCategory)
    }

    private fun setupListeners() {

        // EXPENSE BUTTON
        btnExpense.setOnClickListener {

            isExpense = true

            updateSummary()

            tvSummaryAmount.setTextColor(
                Color.parseColor("#E91E63")
            )

            btnExpense.setBackgroundColor(
                Color.parseColor("#00C896")
            )

            btnIncome.setBackgroundColor(
                Color.parseColor("#DDDDDD")
            )
        }

        // INCOME BUTTON
        btnIncome.setOnClickListener {

            isExpense = false

            updateSummary()

            tvSummaryAmount.setTextColor(
                Color.parseColor("#00C896")
            )

            btnIncome.setBackgroundColor(
                Color.parseColor("#00C896")
            )

            btnExpense.setBackgroundColor(
                Color.parseColor("#DDDDDD")
            )

            Toast.makeText(
                this,
                "Income selected",
                Toast.LENGTH_SHORT
            ).show()
        }

        // LIVE AMOUNT UPDATE
        etAmount.addTextChangedListener {
            updateSummary()
        }

        // QUICK BUTTONS
        val quickButtons = listOf(
            1200.0,
            700.0,
            35.0
        )

        val quickContainer =
            findViewById<LinearLayout>(R.id.quick_add_container)

        if (quickContainer != null) {

            for (i in 0 until quickContainer.childCount) {

                val button =
                    quickContainer.getChildAt(i) as? Button ?: continue

                val amount =
                    quickButtons.getOrNull(i) ?: 0.0

                button.setOnClickListener {

                    etAmount.setText(amount.toString())
                }
            }
        }
    }

    private fun setupDatePicker() {

        etDate.setOnClickListener {

            DatePickerDialog(
                this,
                { _, year, month, day ->

                    calendar.set(year, month, day)

                    updateDate()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun updateDate() {

        val displayFormat =
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val formattedDate =
            displayFormat.format(calendar.time)

        etDate.setText(formattedDate)

        tvSummaryDate.text = formattedDate
    }

    private fun updateSummary() {

        val amount =
            etAmount.text.toString().toDoubleOrNull() ?: 0.0

        val summaryText =
            if (isExpense) {
                "-R %.2f".format(amount)
            } else {
                "+R %.2f".format(amount)
            }

        tvSummaryAmount.text = summaryText
    }

    private fun setupCategorySpinner() {

        val categoryNames = listOf(
            "Food",
            "Transport",
            "Shopping",
            "Bills",
            "Entertainment",
            "Other"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categoryNames
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spCategory.adapter = adapter
    }

    private fun saveExpense() {

        val amountText =
            etAmount.text.toString().trim()

        val description =
            etDescription.text.toString().trim()

        if (amountText.isEmpty()) {

            Toast.makeText(
                this,
                "Please enter an amount",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val amount =
            amountText.toDoubleOrNull()

        if (amount == null || amount <= 0) {

            Toast.makeText(
                this,
                "Enter a valid amount",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val categoryId =
            spCategory.selectedItemPosition + 1

        val dbDate =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            ).format(calendar.time)

        lifecycleScope.launch {

            try {

                val expense = Expense(
                    amount = amount,
                    description = description.ifEmpty {
                        if (isExpense) "Expense" else "Income"
                    },
                    date = dbDate,
                    startTime = "00:00",
                    endTime = "23:59",
                    categoryId = categoryId,
                    receiptPath = receiptPath
                )

                db.expenseDao().insert(expense)

                runOnUiThread {

                    Toast.makeText(
                        this@ExpenseActivity,
                        if (isExpense)
                            "Expense saved successfully!"
                        else
                            "Income saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    clearForm()

                    val intent = Intent(
                        this@ExpenseActivity,
                        DashboardActivity::class.java
                    )

                    intent.flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP

                    startActivity(intent)

                    finish()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    Toast.makeText(
                        this@ExpenseActivity,
                        "Error saving transaction",
                        Toast.LENGTH_LONG
                    ).show()
                }

                e.printStackTrace()
            }
        }
    }

    private fun clearForm() {

        etAmount.text.clear()

        etDescription.text.clear()

        spCategory.setSelection(0)

        updateSummary()
    }
}