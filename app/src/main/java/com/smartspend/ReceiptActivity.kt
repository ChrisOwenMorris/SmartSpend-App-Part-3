package com.smartspend

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.entity.Category
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import android.graphics.Color
import android.util.Log

class ReceiptActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var previewImage: ImageView
    private lateinit var container: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var spFilterCategory: Spinner
    private lateinit var etFilterDate: EditText
    private lateinit var btnClearDate: Button

    private var imageUri: Uri? = null
    private var selectedFilterDate: String = ""     // "" means no date filter
    private var loadedCategories: List<Category> = emptyList()
    private var selectedCategoryFilterId: Int = -1  // -1 means "All"

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
            else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val photo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.extras?.getParcelable("data", Bitmap::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.extras?.getParcelable("data")
                }

                photo?.let { bitmap ->
                    val fileName = "camera_receipt_${System.currentTimeMillis()}.jpg"
                    val file = File(filesDir, fileName)
                    try {
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        val permanentPath = file.absolutePath

                        previewImage.setImageBitmap(bitmap)
                        previewImage.visibility = ImageView.VISIBLE

                        // 🌟 FIXED: Ask the user where they want to link this receipt
                        showTransactionTypeDialog(permanentPath)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "Failed to save camera photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                imageUri = result.data?.data
                imageUri?.let { uri ->
                    val permanentPath = saveImageToInternalStorage(uri)
                    previewImage.setImageURI(uri)
                    previewImage.visibility = ImageView.VISIBLE

                    // 🌟 FIXED: Ask the user where they want to link this receipt
                    showTransactionTypeDialog(permanentPath)
                }
            }
        }

    // 🌟 NEW HELPER: Let the user choose the transaction type path
    private fun showTransactionTypeDialog(imagePath: String) {
        val options = arrayOf("Link to New Expense", "Link to New Income")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Link Receipt To:")
            .setItems(options) { _, which ->
                val intent = Intent(this, ExpenseActivity::class.java).apply {
                    putExtra("receiptPath", imagePath)
                    // Pass a flag to tell ExpenseActivity which mode to open automatically
                    putExtra("isExpenseMode", which == 0)
                }
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)

        NavigationHelper.setupMenu(this)

        previewImage     = findViewById(R.id.ivReceiptPreview)
        container        = findViewById(R.id.recentReceiptsContainer)
        searchBox        = findViewById(R.id.etSearchReceipts)
        spFilterCategory = findViewById(R.id.spFilterCategory)
        etFilterDate     = findViewById(R.id.etFilterDate)
        btnClearDate     = findViewById(R.id.btnClearDate)

        val btnGallery = findViewById<Button>(R.id.btnGallery)
        val cameraCard = findViewById<LinearLayout>(R.id.topCardContainer)

        cameraCard.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        btnGallery.setOnClickListener { openGallery() }

        setupCategoryFilterSpinner()
        setupDateFilter()

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilters() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnClearDate.setOnClickListener {
            selectedFilterDate = ""
            etFilterDate.setText("")
            applyFilters()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@ReceiptActivity)
            }
        })
    }

    private fun setupCategoryFilterSpinner() {
        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val categories = db.categoryDao().getAllCategories(userId)
            loadedCategories = categories

            runOnUiThread {
                val names = mutableListOf("All Categories") + categories.map { it.categoryName }
                val adapter = ArrayAdapter(
                    this@ReceiptActivity,
                    android.R.layout.simple_spinner_item,
                    names
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

                // FIX: Set the adapter FIRST with no listener attached.
                // Android fires onItemSelected automatically during setAdapter() —
                // since no listener is attached yet, it hits nothing and no load fires.
                spFilterCategory.adapter = adapter

                // FIX: Attach the listener AFTER setAdapter() so only real
                // user-driven selections trigger applyFilters().
                spFilterCategory.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: android.view.View?,
                            pos: Int,
                            id: Long
                        ) {
                            selectedCategoryFilterId =
                                if (pos == 0) -1 else loadedCategories[pos - 1].categoryId
                            applyFilters()
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }

                // Single clean initial load after everything is wired up.
                applyFilters()
            }
        }
    }

    private fun setupDateFilter() {
        etFilterDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                selectedFilterDate = "%04d-%02d-%02d".format(year, month + 1, day)
                etFilterDate.setText(selectedFilterDate)
                applyFilters()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun applyFilters() {
        val searchText = searchBox.text.toString()
        loadReceipts(searchText, selectedCategoryFilterId, selectedFilterDate)
    }

    private fun saveImageToInternalStorage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val fileName = "receipt_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return file.absolutePath
    }

    /**
     * Loads ALL expenses, then filters by description, category, and date.
     * The most recent expense that has a receiptPath is auto-shown in the top preview.
     * All matching expenses are listed below, whether or not they have a receipt image.
     */
    private fun loadReceipts(
        search: String = "",
        categoryId: Int = -1,
        date: String = ""
    ) {
        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

            // 1. Fetch raw datasets from Room
            val rawExpenses = db.expenseDao().getAllExpenses(userId)
            val rawIncomes = try {
                db.incomeDao().getAllIncome(userId)
            } catch (e: Exception) {
                emptyList()
            }

            // 2. Convert Incomes safely. If an item has an expense footprint, respect its type!
            val convertedIncomes = rawIncomes.map { income ->
                // Check if the source name or description indicates it's actually an expense
                val representsExpense = income.source.contains("Forex", ignoreCase = true) || income.amount < 0

                com.smartspend.data.entity.Expense(
                    expenseId = income.id,
                    userId = income.userId,
                    amount = kotlin.math.abs(income.amount), // Keep absolute value for formatting
                    description = if (income.source.isNullOrEmpty()) "Income Stream" else income.source,
                    date = income.date,
                    startTime = "00:00",
                    endTime = "00:00",
                    // Use -1 ONLY for pure incomes. If it's an expense like Forex, treat it as one!
                    categoryId = if (representsExpense) 999 else -1,
                    receiptPath = income.imagePath,
                    imagePath = income.imagePath,
                    createdAt = income.createdAt
                )
            }

            runOnUiThread {
                // Find and clear the dynamic list layout container
                val container = findViewById<LinearLayout>(R.id.recentReceiptsContainer)
                container.removeAllViews()

                // 🌟 FIX: The global 'previewImage' initialized in onCreate() is used directly here!

                // 3. Combine and sort strictly by creation timestamp
                val masterFeedList = rawExpenses + convertedIncomes
                val filtered = masterFeedList
                    .filter { transaction ->
                        val matchesSearch = search.isEmpty() ||
                                transaction.description.contains(search, ignoreCase = true)
                        val matchesCategory = categoryId == -1 || transaction.categoryId == categoryId
                        val matchesDate = date.isEmpty() || transaction.date == date

                        matchesSearch && matchesCategory && matchesDate
                    }
                    .sortedByDescending { it.createdAt }

                // 🌟 Update Hero Preview Window at the top of the layout
                val mostRecentWithImage = filtered.firstOrNull { !it.imagePath.isNullOrEmpty() || !it.receiptPath.isNullOrEmpty() }

                if (mostRecentWithImage != null) {
                    val path = mostRecentWithImage.imagePath ?: mostRecentWithImage.receiptPath ?: ""
                    Log.d("ReceiptHeroDebug", "Found most recent transaction with image! Path: '$path'")

                    if (path.isNotEmpty()) {
                        try {
                            previewImage.setImageURI(null)

                            // 🌟 FIX: Support 'file://' scheme prefixes here just like in the list items!
                            if (path.startsWith("http") || path.startsWith("content") || path.startsWith("file")) {
                                Log.d("ReceiptHeroDebug", "Loading via Uri.parse...")
                                previewImage.setImageURI(Uri.parse(path))
                            } else {
                                Log.d("ReceiptHeroDebug", "Loading via absolute File path...")
                                val file = File(path)
                                if (file.exists()) {
                                    previewImage.setImageURI(Uri.fromFile(file))
                                } else {
                                    Log.w("ReceiptHeroDebug", "File path string does not exist on disk storage!")
                                }
                            }
                            previewImage.visibility = android.view.View.VISIBLE
                            Log.d("ReceiptHeroDebug", "Hero preview visibility set to VISIBLE")
                        } catch (e: Exception) {
                            Log.e("ReceiptHeroDebug", "CRASH in Hero Image Rendering logic!", e)
                            previewImage.visibility = android.view.View.GONE
                        }
                    } else {
                        Log.w("ReceiptHeroDebug", "Path string is empty.")
                        previewImage.visibility = android.view.View.GONE
                    }
                } else {
                    Log.w("ReceiptHeroDebug", "No transaction with an image path was found in the database list.")
                    previewImage.visibility = android.view.View.GONE
                }

                // 4. Generate transaction item cards dynamically
                for (transaction in filtered) {
                    val card = LinearLayout(this@ReceiptActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(32, 24, 32, 24)
                        gravity = Gravity.CENTER_VERTICAL
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 0, 0, 24)
                        layoutParams = params
                        setBackgroundResource(R.drawable.card_background)
                    }

                    // 🌟 FIX: Exact Dashboard Image Engine Replication
                    val image = ImageView(this@ReceiptActivity).apply {
                        val size = 130
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        scaleType = ImageView.ScaleType.CENTER_CROP

                        val path = transaction.imagePath ?: transaction.receiptPath
                        if (!path.isNullOrEmpty()) {
                            try {
                                setImageURI(null) // Reset rendering canvas cache

                                // Direct URI parsing handles web URLs, content URIs, and local paths seamlessly
                                if (path.startsWith("http") || path.startsWith("content") || path.startsWith("file")) {
                                    setImageURI(Uri.parse(path))
                                } else {
                                    // If it's a raw absolute path string, handle it directly via file conversion
                                    val file = File(path)
                                    setImageURI(Uri.fromFile(file))
                                }
                            } catch (e: Exception) {
                                Log.e("ReceiptActivity", "Image binding error for path: $path", e)
                                setImageResource(android.R.drawable.ic_menu_report_image)
                            }

                            // Passes the valid path string straight to the preview panel
                            setOnClickListener {
                                val intent = Intent(this@ReceiptActivity, ReceiptPreviewActivity::class.java)
                                intent.putExtra("receiptPath", path)
                                startActivity(intent)
                            }
                        } else {
                            // True fallback if no path data exists at all
                            setImageResource(android.R.drawable.ic_menu_report_image)
                            alpha = 0.3f
                        }
                    }

                    val textContainer = LinearLayout(this@ReceiptActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        params.setMargins(32, 0, 0, 0)
                        layoutParams = params
                    }

                    // Pure income items are explicitly marked with categoryId == -1
                    val isIncomeItem = transaction.categoryId == -1

                    val title = TextView(this@ReceiptActivity).apply {
                        text = transaction.description
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.parseColor("#333333"))
                    }
                    val dateTv = TextView(this@ReceiptActivity).apply {
                        text = transaction.date
                        textSize = 12f
                        setTextColor(Color.GRAY)
                    }
                    val categoryTv = TextView(this@ReceiptActivity).apply {
                        text = if (isIncomeItem) "Income Stream" else "Expense"
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.ITALIC)
                        setTextColor(Color.parseColor(if (isIncomeItem) "#00C896" else "#E91E63"))
                    }

                    // 🌟 Color Logic: Green for Income (+), Vibrant Crimson Red for Expenses (-)
                    val amount = TextView(this@ReceiptActivity).apply {
                        text = if (isIncomeItem) "+R %.2f".format(transaction.amount) else "-R %.2f".format(transaction.amount)
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.parseColor(if (isIncomeItem) "#00C896" else "#E91E63"))
                    }

                    textContainer.addView(title)
                    textContainer.addView(dateTv)
                    textContainer.addView(categoryTv)

                    card.addView(image)
                    card.addView(textContainer)
                    card.addView(amount)

                    container.addView(card)
                }

                if (filtered.isEmpty()) {
                    val empty = TextView(this@ReceiptActivity).apply {
                        text = "No records found matching filters"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(0, 48, 0, 0)
                    }
                    container.addView(empty)
                }
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

}



