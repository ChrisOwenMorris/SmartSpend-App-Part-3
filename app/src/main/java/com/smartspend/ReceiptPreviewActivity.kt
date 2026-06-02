package com.smartspend

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ReceiptPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_preview)

        val imageView = findViewById<ImageView>(R.id.fullReceiptImage)
        val imagePath = intent.getStringExtra("receiptPath")

        Log.d("ReceiptPreviewActivity", "Received image path data link: $imagePath")

        if (!imagePath.isNullOrEmpty()) {
            try {
                // Clear out the view canvas to ensure fresh drawing
                imageView.setImageURI(null)

                // 🌟 FIXED: Added check for "file" to catch file:// formatted string paths cleanly
                if (imagePath.startsWith("http") || imagePath.startsWith("content") || imagePath.startsWith("file")) {
                    imageView.setImageURI(Uri.parse(imagePath))
                    Log.d("ReceiptPreviewActivity", "Successfully bound URI string to view component")
                } else {
                    // Fall back to local file path verification if it's a raw un-schemed absolute path string
                    val file = File(imagePath)
                    if (file.exists()) {
                        imageView.setImageURI(Uri.fromFile(file))
                        Log.d("ReceiptPreviewActivity", "Successfully bound local device file layout link")
                    } else {
                        Log.w("ReceiptPreviewActivity", "Local physical file does not exist on disk path space")
                        imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReceiptPreviewActivity", "Error displaying receipt preview payload stream", e)
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                Toast.makeText(this, "Failed to render full screen layout preview", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Placeholder fallback if string was empty
            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
        }
    }
}