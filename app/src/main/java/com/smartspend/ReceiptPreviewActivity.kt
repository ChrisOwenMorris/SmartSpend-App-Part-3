package com.smartspend

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ReceiptPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_preview)

        val imageView = findViewById<ImageView>(R.id.fullReceiptImage)

        val receiptPath = intent.getStringExtra("receiptPath")
        val imagePath = intent.getStringExtra("imagePath")

        Log.d("ReceiptPreviewActivity", "Received paths: Receipt=$receiptPath, Image=$imagePath")

        // Check if both exist to trigger a choice
        if (!receiptPath.isNullOrEmpty() && !imagePath.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Select Image to View")
                .setItems(arrayOf("View Receipt", "View Original Image")) { _, which ->
                    val selectedPath = if (which == 0) receiptPath else imagePath
                    loadSelectedImage(selectedPath, imageView)
                }
                .setCancelable(false)
                .show()
        } else {
            // Only one or none exist, just load the one that does
            val finalPath = receiptPath ?: imagePath
            if (!finalPath.isNullOrEmpty()) {
                loadSelectedImage(finalPath, imageView)
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        }
    }

    private fun loadSelectedImage(path: String, imageView: ImageView) {
        try {
            val cleanPath = path.replace("file://", "")
            val file = File(cleanPath)

            if (file.exists()) {
                imageView.setImageURI(Uri.fromFile(file))
                Log.d("ReceiptPreviewActivity", "Successfully loaded image: $cleanPath")
            } else {
                Log.w("ReceiptPreviewActivity", "File not found at: $cleanPath")
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } catch (e: Exception) {
            Log.e("ReceiptPreviewActivity", "Error loading image", e)
            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }
}