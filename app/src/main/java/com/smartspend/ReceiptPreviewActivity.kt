package com.smartspend

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ReceiptPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_preview)

        val imageView = findViewById<ImageView>(R.id.fullReceiptImage)
        val imagePath = intent.getStringExtra("receiptPath")

        if (!imagePath.isNullOrEmpty()) {
            val file = File(imagePath)
            if (file.exists()) {

                imageView.setImageURI(Uri.fromFile(file))
            }
        }
    }
}//Preview Functionality