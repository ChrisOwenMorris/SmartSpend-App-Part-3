package com.smartspend

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.entity.Category
import com.smartspend.data.entity.Expense
import com.smartspend.data.firebase.FirebaseRepository
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
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button

    private lateinit var spCategory: Spinner

    private var receiptPath: String? = null
    private var isExpense = true
    private var startTime = "00:00"
    private var endTime = "00:00"
    private var loadedCategories: List<Category> = emptyList()

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@ExpenseActivity)
            }
        })

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
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)

        spCategory = findViewById(R.id.spCategory)
    }

    private fun setupListeners() {

        // EXPENSE BUTTON
        btnExpense.setOnClickListener {
            isExpense = true
            updateSummary()
            tvSummaryAmount.setTextColor(Color.parseColor("#E91E63"))
            btnExpense.setBackgroundColor(Color.parseColor("#00C896"))
            btnIncome.setBackgroundColor(Color.parseColor("#DDDDDD"))
        }

        // INCOME BUTTON
        btnIncome.setOnClickListener {
            isExpense = false
            updateSummary()
            tvSummaryAmount.setTextColor(Color.parseColor("#00C896"))
            btnIncome.setBackgroundColor(Color.parseColor("#00C896"))
            btnExpense.setBackgroundColor(Color.parseColor("#DDDDDD"))
            Toast.makeText(this, "Income selected", Toast.LENGTH_SHORT).show()
        }

        // LIVE AMOUNT UPDATE
        etAmount.addTextChangedListener { updateSummary() }

        // START TIME PICKER
        btnStartTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    startTime = "%02d:%02d".format(hour, minute)
                    btnStartTime.text = "Start Time: $startTime"
                    Log.d("ExpenseActivity", "Start time set: $startTime")
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        }

        // END TIME PICKER
        btnEndTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    endTime = "%02d:%02d".format(hour, minute)
                    btnEndTime.text = "End Time: $endTime"
                    Log.d("ExpenseActivity", "End time set: $endTime")
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        }

        // QUICK BUTTONS
        val quickButtons = listOf(1200.0, 700.0, 35.0)
        val quickContainer = findViewById<LinearLayout>(R.id.quick_add_container)

        if (quickContainer != null) {
            for (i in 0 until quickContainer.childCount) {
                val button = quickContainer.getChildAt(i) as? Button ?: continue
                val amount = quickButtons.getOrNull(i) ?: 0.0
                button.setOnClickListener { etAmount.setText(amount.toString()) }
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

    private fun setupCategorySpinner() {
        // Disable save while categories load so the user cannot save a categoryId=0 record
        btnSave.isEnabled = false
        Log.d("ExpenseActivity", "Save button disabled — waiting for categories to load")

        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            Log.d("ExpenseActivity", "Loading categories for userId: $userId")
            var categories = db.categoryDao().getAllCategories(userId)

            if (categories.isEmpty()) {
                val defaults = listOf("Bills", "Entertainment", "Food", "Other", "Shopping", "Transport")
                defaults.forEach { name ->
                    db.categoryDao().insert(Category(userId = userId, categoryName = name))
                }
                categories = db.categoryDao().getAllCategories(userId)
                Log.d("ExpenseActivity", "Seeded ${categories.size} default categories into Room DB")

                // Sync seeded categories to Firebase
                val firebaseRepo = FirebaseRepository()
                categories.forEach { cat -> firebaseRepo.saveCategory(cat) }
            }

            loadedCategories = categories
            val categoryNames = categories.map { it.categoryName }

            runOnUiThread {
                val adapter = ArrayAdapter(
                    this@ExpenseActivity,
                    android.R.layout.simple_spinner_item,
                    categoryNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spCategory.adapter = adapter
                btnSave.isEnabled = true
                Log.d("ExpenseActivity", "Spinner loaded with ${categories.size} categories — save button re-enabled")
            }
        }
    }

    private fun updateDate() {
        val displayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedDate = displayFormat.format(calendar.time)
        etDate.setText(formattedDate)
        tvSummaryDate.text = formattedDate
    }

    private fun updateSummary() {
        val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
        tvSummaryAmount.text = if (isExpense) "-R %.2f".format(amount) else "+R %.2f".format(amount)
    }

    private fun saveExpense() {
        if (loadedCategories.isEmpty()) {
            Toast.makeText(this, "Categories not loaded yet, please wait", Toast.LENGTH_SHORT).show()
            Log.w("ExpenseActivity", "Save attempted before categories loaded — blocked")
            return
        }

        val amountText = etAmount.text.toString().trim()
        val description = etDescription.text.toString().trim()

        if (amountText.isEmpty()) {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedPos = spCategory.selectedItemPosition
        val selectedCategory = loadedCategories.getOrNull(selectedPos)
        val categoryId = selectedCategory?.categoryId ?: (selectedPos + 1)
        val categoryName = selectedCategory?.categoryName ?: ""

        val dbDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        lifecycleScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                Log.d("ExpenseActivity", "Saving expense for userId: $userId")
                val expense = Expense(
                    userId = userId,
                    amount = amount,
                    description = description.ifEmpty { if (isExpense) "Expense" else "Income" },
                    date = dbDate,
                    startTime = this@ExpenseActivity.startTime,
                    endTime = this@ExpenseActivity.endTime,
                    categoryId = categoryId,
                    receiptPath = receiptPath
                )

                // Insert into Room and capture the generated ID
                val newId = db.expenseDao().insert(expense)
                val savedExpense = expense.copy(expenseId = newId.toInt())
                Log.d("ExpenseActivity", "Expense inserted into Room DB with id=$newId: $description")

                val firebaseRepo = FirebaseRepository()

                // If income is selected, also save to the income table
                if (!isExpense) {
                    val income = com.smartspend.data.entity.Income(
                        userId = userId,
                        source = description.ifEmpty { "Income" },
                        amount = amount,
                        date = dbDate,
                        description = description.ifEmpty { null }
                    )
                    val incomeId = db.incomeDao().insert(income)
                    val savedIncome = income.copy(id = incomeId.toInt())
                    firebaseRepo.saveIncome(savedIncome)
                    Log.d("ExpenseActivity", "Income inserted into Room and Firebase with id=$incomeId")
                }

                // Sync expense to Firebase using the real Room ID and category name
                val synced = firebaseRepo.saveExpense(savedExpense, categoryName)
                if (synced) {
                    Log.d("ExpenseActivity", "Expense synced to Firebase: $description")
                } else {
                    Log.w("ExpenseActivity", "Firebase sync failed, saved locally only")
                }

                runOnUiThread {
                    Toast.makeText(
                        this@ExpenseActivity,
                        if (isExpense) "Expense saved successfully!" else "Income saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    clearForm()

                    val intent = Intent(this@ExpenseActivity, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ExpenseActivity, "Error saving transaction", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun clearForm() {
        etAmount.text.clear()
        etDescription.text.clear()
        spCategory.setSelection(0)
        startTime = "00:00"
        endTime = "00:00"
        btnStartTime.text = "Start Time: 00:00"
        btnEndTime.text = "End Time: 00:00"
        updateSummary()
    }
}
