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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.entity.Category
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

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

    companion object {
        private const val CAMERA_PERMISSION_CODE = 300
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
                photo?.let {
                    previewImage.setImageBitmap(it)
                    previewImage.visibility = ImageView.VISIBLE
                    Toast.makeText(this, "Camera image captured", Toast.LENGTH_SHORT).show()
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
                    val intent = Intent(this, ExpenseActivity::class.java)
                    intent.putExtra("receiptPath", permanentPath)
                    startActivity(intent)
                }
            }
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

        cameraCard.setOnClickListener { openCamera() }
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
            val expenses = db.expenseDao().getAllExpenses(userId)

            runOnUiThread {
                container.removeAllViews()

                val filtered = expenses
                    .filter { expense ->
                        val matchesSearch = search.isEmpty() ||
                                expense.description.contains(search, ignoreCase = true)
                        val matchesCategory = categoryId == -1 || expense.categoryId == categoryId
                        val matchesDate = date.isEmpty() || expense.date == date
                        matchesSearch && matchesCategory && matchesDate
                    }
                    .sortedByDescending { it.date }

                // Auto-preview the most recent receipt image
                val mostRecentWithImage = filtered.firstOrNull { !it.receiptPath.isNullOrEmpty() }
                if (mostRecentWithImage != null) {
                    val file = File(mostRecentWithImage.receiptPath!!)
                    if (file.exists()) {
                        previewImage.setImageURI(Uri.fromFile(file))
                        previewImage.visibility = ImageView.VISIBLE
                    }
                } else {
                    previewImage.visibility = ImageView.GONE
                }

                // List ALL filtered expenses
                for (expense in filtered) {
                    val card = LinearLayout(this@ReceiptActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(24, 24, 24, 24)
                        gravity = Gravity.CENTER_VERTICAL
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 0, 0, 24)
                        layoutParams = params
                        setBackgroundResource(R.drawable.card_background)
                    }

                    val image = ImageView(this@ReceiptActivity).apply {
                        val size = 140
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        scaleType = ImageView.ScaleType.CENTER_CROP

                        val hasImage = !expense.receiptPath.isNullOrEmpty()
                        if (hasImage) {
                            val file = File(expense.receiptPath!!)
                            if (file.exists()) {
                                setImageURI(Uri.fromFile(file))
                            } else {
                                setImageResource(android.R.drawable.ic_menu_report_image)
                            }
                            setOnClickListener {
                                val intent = Intent(
                                    this@ReceiptActivity,
                                    ReceiptPreviewActivity::class.java
                                )
                                intent.putExtra("receiptPath", expense.receiptPath)
                                startActivity(intent)
                            }
                        } else {
                            setImageResource(android.R.drawable.ic_menu_report_image)
                            alpha = 0.3f
                        }
                    }

                    val textContainer = LinearLayout(this@ReceiptActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        val params = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        params.setMargins(24, 0, 0, 0)
                        layoutParams = params
                    }

                    val categoryName = loadedCategories
                        .firstOrNull { it.categoryId == expense.categoryId }
                        ?.categoryName ?: "Uncategorised"

                    val title = TextView(this@ReceiptActivity).apply {
                        text = if (expense.description.isBlank()) "Expense" else expense.description
                        textSize = 16f
                    }
                    val dateTv = TextView(this@ReceiptActivity).apply {
                        text = expense.date
                        textSize = 12f
                    }
                    val categoryTv = TextView(this@ReceiptActivity).apply {
                        text = categoryName
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#00C896"))
                    }
                    val amount = TextView(this@ReceiptActivity).apply {
                        text = "R %.2f".format(expense.amount)
                        textSize = 16f
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
                        text = "No transactions found"
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(0, 32, 0, 0)
                    }
                    container.addView(empty)
                }
            }
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}


