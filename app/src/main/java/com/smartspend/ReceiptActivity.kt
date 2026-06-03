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
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.entity.Category
import com.smartspend.data.entity.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import kotlinx.coroutines.flow.first
import com.smartspend.data.entity.Income
import com.smartspend.data.entity.ExpenseWithCategory

class ReceiptActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var previewImage: ImageView
    private lateinit var searchBox: EditText
    private lateinit var spFilterCategory: Spinner
    private lateinit var etFilterDate: EditText
    private lateinit var btnClearDate: Button

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyStateIndicator: TextView
    private lateinit var transactionAdapter: TransactionAdapter

    private var imageUri: Uri? = null
    private var selectedFilterDate: String = ""
    private var loadedCategories: List<Category> = emptyList()
    private var selectedCategoryFilterId: Int = -1

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                previewImage.setImageBitmap(bitmap)
                previewImage.visibility = View.VISIBLE
                showTransactionTypeDialog(file.absolutePath)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            imageUri = result.data?.data
            imageUri?.let { uri ->
                val permanentPath = saveImageToInternalStorage(uri)
                previewImage.setImageURI(uri)
                previewImage.visibility = View.VISIBLE
                showTransactionTypeDialog(permanentPath)
            }
        }
    }

    private fun showTransactionTypeDialog(imagePath: String) {
        val options = arrayOf(
            "Link to New Expense",
            "Link to New Income",
            "Add to Existing Expense",
            "Add to Existing Income"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Link Receipt To:")
            .setItems(options) { _, which ->
                when (which) {
                    0, 1 -> {
                        val intent = Intent(this, ExpenseActivity::class.java).apply {
                            putExtra("receiptPath", imagePath)
                            putExtra("isExpenseMode", which == 0)
                        }
                        startActivity(intent)
                    }

                    2, 3 -> {
                        openExistingTransactionPicker(imagePath, isExpense = (which == 2))
                    }
                }
            }.setCancelable(false).show()
    }

    private fun openPreview(path: String?) {
        if (!path.isNullOrEmpty()) {
            val intent = Intent(this, ReceiptPreviewActivity::class.java)
            intent.putExtra("imagePath", path)
            startActivity(intent)
        }else {
            Toast.makeText(this, "No image found for this transaction", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)

        NavigationHelper.setupMenu(this)

        previewImage          = findViewById(R.id.ivReceiptPreview)
        searchBox             = findViewById(R.id.etSearchReceipts)
        spFilterCategory      = findViewById(R.id.spFilterCategory)
        etFilterDate          = findViewById(R.id.etFilterDate)
        btnClearDate          = findViewById(R.id.btnClearDate)
        recyclerView          = findViewById(R.id.rvReceiptsList)
        tvEmptyStateIndicator = findViewById(R.id.tvEmptyStateIndicator)

        recyclerView.layoutManager = LinearLayoutManager(this)

        transactionAdapter = TransactionAdapter(
            transactions = emptyList(),
            onImageClick = { receiptPath, imagePath ->
                val hasReceipt = !receiptPath.isNullOrEmpty()
                val hasImage = !imagePath.isNullOrEmpty()

                if (hasReceipt && hasImage){
                    val options = arrayOf("View Receipt", "View Original Image")
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Select Image to View")
                        .setItems(options) { _, which ->
                            val pathToOpen = if (which == 0) receiptPath else imagePath
                            openPreview(pathToOpen)
                        }.show()
                }else {
                    openPreview(receiptPath ?: imagePath)
                }
            },
        )
        recyclerView.adapter = transactionAdapter

        val btnGallery = findViewById<Button>(R.id.btnGallery)
        val cameraCard = findViewById<LinearLayout>(R.id.topCardContainer)

        cameraCard.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            override fun handleOnBackPressed() { NavigationHelper.goToDashboard(this@ReceiptActivity) }
        })
    }

    private fun setupCategoryFilterSpinner() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            db.categoryDao().getAllCategories(userId).collect { categories ->
                loadedCategories = categories
                withContext(Dispatchers.Main) {
                    val names = mutableListOf("All Categories") + categories.map { it.categoryName }
                    val adapter = ArrayAdapter(this@ReceiptActivity, android.R.layout.simple_spinner_item, names)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spFilterCategory.adapter = adapter
                    spFilterCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                            selectedCategoryFilterId = if (pos == 0) -1 else loadedCategories[pos - 1].categoryId
                            applyFilters()
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    applyFilters()
                }
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

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun loadReceipts(search: String = "", categoryId: Int = -1, date: String = "") {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

            // Let the database handle the filtering/sorting
            val filtered = db.expenseDao().getFilteredExpenses(userId, search, categoryId, date)

            // Only do the mapping to ExpenseWithCategory here
            val wrappedTransactions = filtered.map { expense ->
                val matchingCategoryName = loadedCategories.firstOrNull { it.categoryId == expense.categoryId }?.categoryName ?: "Expense"
                ExpenseWithCategory(expense, matchingCategoryName)
            }

            withContext(Dispatchers.Main) {
                transactionAdapter.updateData(wrappedTransactions)
                tvEmptyStateIndicator.visibility = if (wrappedTransactions.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (wrappedTransactions.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
    private fun openExistingTransactionPicker(imagePath: String, isExpense: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvPickerList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 1. Create the dialogue first so we can reference it inside the listener
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select ${if (isExpense) "Expense" else "Income"} to Link")
            .setView(dialogView)
            .create()

        // 2. Initialize the adapter with the click logic
        val pickerAdapter = TransactionAdapter(
            transactions = emptyList(),
            onImageClick = { _, _ -> },
            onItemClick = { selected ->
                lifecycleScope.launch(Dispatchers.IO) {
                    if (isExpense) {
                        db.expenseDao().updateReceiptPath(selected.expense.expenseId, imagePath)
                    } else {
                        db.incomeDao().updateImagePath(selected.expense.expenseId, imagePath)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ReceiptActivity, "Linked successfully!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss() // Now we have access to the dialogue reference!
                    }
                }
            }
        )
        recyclerView.adapter = pickerAdapter

        // 3. Load data
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val data = if (isExpense) {
                db.expenseDao().getAllExpenses(userId).map { ExpenseWithCategory(it, "Expense") }
            } else {
                db.incomeDao().getAllIncome(userId).first().map { income ->
                    val expense = Expense(income.id, income.userId, income.amount, income.source, income.date, "", "", -1, income.imagePath, income.imagePath, income.createdAt)
                    ExpenseWithCategory(expense, "Income")
                }
            }

            withContext(Dispatchers.Main) {
                pickerAdapter.updateData(data)
            }
        }

        dialog.show()
    }
}