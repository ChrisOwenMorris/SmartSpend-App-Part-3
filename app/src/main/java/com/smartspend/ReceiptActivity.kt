package com.smartspend

import android.Manifest
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ReceiptActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var previewImage: ImageView
    private lateinit var container: LinearLayout
    private lateinit var searchBox: EditText

    private var imageUri: Uri? = null

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

        val btnGallery = findViewById<Button>(R.id.btnGallery)
        val cameraCard = findViewById<LinearLayout>(R.id.topCardContainer)

        previewImage = findViewById(R.id.ivReceiptPreview)
        container = findViewById(R.id.recentReceiptsContainer)
        searchBox = findViewById(R.id.etSearchReceipts)

        cameraCard.setOnClickListener {
            openCamera()
        }

        btnGallery.setOnClickListener {
            openGallery()
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                loadReceipts(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadReceipts()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@ReceiptActivity)
            }
        })
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

    private fun loadReceipts(search: String = "") {
        lifecycleScope.launch {
            val expenses = db.expenseDao().getAllExpenses(FirebaseAuth.getInstance().currentUser?.uid ?: "")

            runOnUiThread {
                container.removeAllViews()

                val filteredExpenses = expenses.filter {
                    it.description.contains(search, ignoreCase = true)
                }.reversed()

                for (expense in filteredExpenses) {
                    if (expense.receiptPath.isNullOrEmpty()) continue

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
                        layoutParams = LinearLayout.LayoutParams(140, 140)
                        scaleType = ImageView.ScaleType.CENTER_CROP

                        val file = File(expense.receiptPath)
                        if (file.exists()) {
                            setImageURI(Uri.fromFile(file))
                        }

                        setOnClickListener {
                            val intent = Intent(
                                this@ReceiptActivity,
                                ReceiptPreviewActivity::class.java
                            )
                            intent.putExtra("receiptPath", expense.receiptPath)
                            startActivity(intent)
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

                    val title = TextView(this@ReceiptActivity).apply {
                        text = if (expense.description.isBlank()) "Expense" else expense.description
                        textSize = 16f
                    }

                    val date = TextView(this@ReceiptActivity).apply {
                        text = expense.date
                        textSize = 12f
                    }

                    val amount = TextView(this@ReceiptActivity).apply {
                        text = "R %.2f".format(expense.amount)
                        textSize = 16f
                    }

                    textContainer.addView(title)
                    textContainer.addView(date)

                    card.addView(image)
                    card.addView(textContainer)
                    card.addView(amount)

                    container.addView(card)
                }
            }
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
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
        val intent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
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
