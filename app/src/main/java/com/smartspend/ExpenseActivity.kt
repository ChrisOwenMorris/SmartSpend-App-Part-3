package com.smartspend

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.entity.Category
import com.smartspend.data.entity.Expense
import com.smartspend.data.firebase.FirebaseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import java.io.File
import androidx.appcompat.app.AlertDialog

class ExpenseActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var etAmount: EditText
    private lateinit var etDate: EditText
    private lateinit var etDescription: EditText

    private lateinit var tvSummaryAmount: TextView
    private lateinit var tvSummaryDate: TextView

    private lateinit var btnExpense: TextView
    private lateinit var btnIncome: TextView
    private lateinit var btnSave: Button
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnCreateCategory: Button

    private lateinit var spCategory: Spinner

    private lateinit var ivImagePreview: ImageView
    private lateinit var btnUploadImage: Button
    private lateinit var btnRemoveImage: Button

    private var receiptPath: String? = null
    private var isExpense = true
    private var startTime = "00:00"
    private var endTime = "00:00"
    private var loadedCategories: List<Category> = emptyList()

    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null

    private val calendar = Calendar.getInstance()

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraCaptureIntent()
        } else {
            Toast.makeText(this, "Camera permission is required to snap receipt photos.", Toast.LENGTH_LONG).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { rawUri ->
            val savedInternalUri = copyUriToInternalStorage(rawUri)
            if (savedInternalUri != null) {
                selectedImageUri = savedInternalUri
                setImagePreview(savedInternalUri)
            } else {
                Toast.makeText(this, "Failed to process gallery image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { rawCameraUri ->
                val savedInternalUri = copyUriToInternalStorage(rawCameraUri)
                if (savedInternalUri != null) {
                    selectedImageUri = savedInternalUri
                    setImagePreview(savedInternalUri)

                    try {
                        val file = File(filesDir, rawCameraUri.lastPathSegment ?: "")
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.w("ExpenseActivity", "Could not delete temporary camera file", e)
                    }
                }
            }
        }
    }

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
        setContentView(R.layout.activity_expense)

        NavigationHelper.setupMenu(this)

        // 1. Always bind your XML layout views FIRST
        bindViews()

        // 2. NOW it is safe to extract intent data and modify UI view elements
        val incomingReceiptPath = intent.getStringExtra("receiptPath")
        val startInExpenseMode = intent.getBooleanExtra("isExpenseMode", true)

        if (!startInExpenseMode) {
            applyIncomeSelected()
        } else {
            applyExpenseSelected()
        }

        if (!incomingReceiptPath.isNullOrEmpty()) {
            receiptPath = incomingReceiptPath
            val receiptFile = File(incomingReceiptPath)
            if (receiptFile.exists()) {
                val fileUri = Uri.fromFile(receiptFile)
                selectedImageUri = fileUri

                ivImagePreview.setImageURI(fileUri)
                ivImagePreview.visibility = android.view.View.VISIBLE
                btnRemoveImage.visibility = android.view.View.VISIBLE
            }
        }

        // 3. Continue running the rest of your initializers
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
        etAmount           = findViewById(R.id.etAmount)
        etDate             = findViewById(R.id.etDate)
        etDescription      = findViewById(R.id.etDescription)
        tvSummaryAmount    = findViewById(R.id.tvSummaryAmount)
        tvSummaryDate      = findViewById(R.id.tvSummaryDate)
        btnExpense         = findViewById(R.id.btnExpense)
        btnIncome          = findViewById(R.id.btnIncome)
        btnSave            = findViewById(R.id.btnSave)
        btnStartTime       = findViewById(R.id.btnStartTime)
        btnEndTime         = findViewById(R.id.btnEndTime)
        btnCreateCategory  = findViewById(R.id.btnCreateCategory)
        spCategory         = findViewById(R.id.spCategory)
        ivImagePreview     = findViewById(R.id.ivImagePreview)
        btnUploadImage     = findViewById(R.id.btnUploadImage)
        btnRemoveImage     = findViewById(R.id.btnRemoveImage)
    }

    /* private fun setupToggleButtons() {
        applyExpenseSelected()
    }
     */

    private fun applyExpenseSelected() {
        isExpense = true
        btnExpense.background = AppCompatResources.getDrawable(this, R.drawable.bg_btn_expense)
        btnIncome.background = AppCompatResources.getDrawable(this, R.drawable.bg_btn_income_inactive)
        btnExpense.setTextColor(Color.WHITE)
        btnIncome.setTextColor(Color.WHITE)
        tvSummaryAmount.setTextColor(Color.parseColor("#E91E63"))
        btnSave.text = "Save Expense"
    }

    private fun applyIncomeSelected() {
        isExpense = false
        btnExpense.background = AppCompatResources.getDrawable(this, R.drawable.bg_btn_expense_inactive)
        btnIncome.background = AppCompatResources.getDrawable(this, R.drawable.bg_btn_income)
        btnExpense.setTextColor(Color.WHITE)
        btnIncome.setTextColor(Color.WHITE)
        tvSummaryAmount.setTextColor(Color.parseColor("#00C896"))
        btnSave.text = "Save Income"
    }

    private fun setupListeners() {

        btnExpense.setOnClickListener {
            applyExpenseSelected()
            updateSummary()
            Toast.makeText(this, "Expense selected", Toast.LENGTH_SHORT).show()
        }

        btnIncome.setOnClickListener {
            applyIncomeSelected()
            updateSummary()
            Toast.makeText(this, "Income Selected", Toast.LENGTH_SHORT).show()
        }

        etAmount.addTextChangedListener { updateSummary() }

        btnStartTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                startTime = "%02d:%02d".format(hour, minute)
                btnStartTime.text = "Start Time: $startTime"
                Log.d("ExpenseActivity", "Start time set: $startTime")
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        btnEndTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                endTime = "%02d:%02d".format(hour, minute)
                btnEndTime.text = "End Time: $endTime"
                Log.d("ExpenseActivity", "End time set: $endTime")
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        btnCreateCategory.setOnClickListener {
            showCreateCategoryBottomSheet()
        }

        val quickButtons = listOf(1200.0, 700.0, 35.0)
        val quickContainer = findViewById<LinearLayout>(R.id.quick_add_container)
        if (quickContainer != null) {
            for (i in 0 until quickContainer.childCount) {
                val button = quickContainer.getChildAt(i) as? Button ?: continue
                val amount = quickButtons.getOrNull(i) ?: 0.0
                button.setOnClickListener { etAmount.setText(amount.toString()) }
            }
        }

        btnUploadImage.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Choose Image Source")
                .setItems(arrayOf("Camera", "Gallery")) { _: android.content.DialogInterface, which: Int ->
                    if (which == 0) {
                        // Check if permission is already granted dynamically
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCameraCaptureIntent()
                        } else {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        galleryLauncher.launch("image/*")
                    }
                }
                .show()
        }

        btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            ivImagePreview.setImageURI(null)
            ivImagePreview.visibility = android.view.View.GONE
            btnRemoveImage.visibility = android.view.View.GONE
        }

        ivImagePreview.setOnClickListener {
            selectedImageUri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            }
        }
    }

    private fun launchCameraCaptureIntent() {
        val imageFile = File(filesDir, "temp_image_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            imageFile
        )
        cameraLauncher.launch(cameraImageUri!!)
    }

    private fun showCreateCategoryBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 48)
        }

        val title = TextView(this).apply {
            text = "Create New Category"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }

        val input = TextInputEditText(this).apply {
            hint = "Category name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val btnAdd = Button(this).apply {
            text = "Add Category"
            setTextColor(Color.WHITE)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#00C896"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 32
            layoutParams = params
        }

        btnAdd.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a category name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val newCategory = Category(userId = userId, categoryName = name)
                val newCategoryId = db.categoryDao().insert(newCategory)
                val savedCategory = newCategory.copy(categoryId = newCategoryId.toInt())
                Log.d("ExpenseActivity", "New category '$name' inserted with ID: $newCategoryId")

                val firebaseRepo = FirebaseRepository()
                firebaseRepo.saveCategory(savedCategory)

                reloadCategorySpinner(userId)

                runOnUiThread {
                    Toast.makeText(this@ExpenseActivity, "'$name' added!", Toast.LENGTH_SHORT).show()
                    bottomSheet.dismiss()
                }
            }
        }

        layout.addView(title)
        layout.addView(input)
        layout.addView(btnAdd)

        bottomSheet.setContentView(layout)
        bottomSheet.show()
    }

    private suspend fun reloadCategorySpinner(userId: String) {
        val categories = db.categoryDao().getAllCategories(userId)
        loadedCategories = categories
        val names = categories.map { it.categoryName }
        runOnUiThread {
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                names
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spCategory.adapter = adapter
            spCategory.setSelection(names.size - 1)
        }
    }

    private fun setupDatePicker() {
        etDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                updateDate()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun setupCategorySpinner() {
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
                Log.d("ExpenseActivity", "Seeded ${categories.size} default categories")

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
        val currentTimestamp = System.currentTimeMillis() // 🌟 CRITICAL: Dynamic sorting link

        lifecycleScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val firebaseRepo = FirebaseRepository()
                val imagePathString = selectedImageUri?.toString() ?: receiptPath

                if (isExpense) {
                    // ─── PATH A: SAVING AN EXPENSE ──────────────────────────
                    Log.d("ExpenseActivity", "Saving pure expense for userId: $userId")
                    val expense = Expense(
                        userId = userId,
                        amount = amount,
                        description = description.ifEmpty { "Expense" },
                        date = dbDate,
                        startTime = this@ExpenseActivity.startTime,
                        endTime = this@ExpenseActivity.endTime,
                        categoryId = categoryId,
                        receiptPath = imagePathString,
                        imagePath = imagePathString,
                        createdAt = currentTimestamp // Set explicit timestamp
                    )

                    // 1. Write locally to Room DB and capture generated ID
                    val newId = db.expenseDao().insert(expense)
                    val savedExpense = expense.copy(expenseId = newId.toInt())
                    Log.d("ExpenseActivity", "Expense saved with ID: $newId")

                    // 2. Sync to Firebase Cloud Repository with real ID
                    firebaseRepo.saveExpense(savedExpense, categoryName)

                } else {
                    // ─── PATH B: SAVING AN INCOME ──────────────────────────
                    Log.d("ExpenseActivity", "Saving income stream for userId: $userId")
                    val income = com.smartspend.data.entity.Income(
                        userId = userId,
                        source = description.ifEmpty { "Income Stream" },
                        amount = amount,
                        date = dbDate,
                        imagePath = imagePathString,
                        createdAt = currentTimestamp // Set explicit timestamp
                    )

                    // 1. Write locally to Room DB and capture generated ID
                    val newIncomeId = db.incomeDao().insert(income)
                    val savedIncome = income.copy(id = newIncomeId.toInt())
                    Log.d("ExpenseActivity", "Income saved with ID: $newIncomeId")

                    // 2. Sync to Firebase Cloud Repository with real ID
                    firebaseRepo.saveIncome(savedIncome)
                }

                // ─── SUCCESS HANDOFF ROUTING ────────────────────────────────
                runOnUiThread {
                    Toast.makeText(this@ExpenseActivity, "Transaction saved successfully!", Toast.LENGTH_SHORT).show()
                    // Redirect safely back to the Dashboard screen
                    NavigationHelper.goToDashboard(this@ExpenseActivity)
                    finish()
                }

            } catch (e: Exception) {
                Log.e("ExpenseActivity", "Error saving transaction entry", e)
                runOnUiThread {
                    Toast.makeText(this@ExpenseActivity, "Failed to save record", Toast.LENGTH_LONG).show()
                }
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
        selectedImageUri = null
        ivImagePreview.setImageURI(null)
        ivImagePreview.visibility = android.view.View.GONE
        btnRemoveImage.visibility = android.view.View.GONE
    }

    private fun setImagePreview(uri: Uri) {
        runOnUiThread {
            try {
                ivImagePreview.setImageURI(null)
                ivImagePreview.setImageURI(uri)
                ivImagePreview.visibility = android.view.View.VISIBLE
                btnRemoveImage.visibility = android.view.View.VISIBLE
            } catch (e: Exception) {
                Log.e("ExpenseActivity", "Failed to render preview layout stream", e)
                Toast.makeText(this, "Failed to load image preview window", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun copyUriToInternalStorage(uri: Uri): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val permanentFile = File(filesDir, "receipt_permanent_${System.currentTimeMillis()}.jpg")

            inputStream.use { input ->
                permanentFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(permanentFile)
        } catch (e: Exception) {
            Log.e("ExpenseActivity", "Failed to copy image to internal storage", e)
            null
        }
    }
}