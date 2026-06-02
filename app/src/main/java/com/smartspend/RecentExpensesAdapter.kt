package com.smartspend

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.smartspend.data.entity.Expense
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import java.io.File

class RecentExpensesAdapter(private val expenses: MutableList<Expense> = mutableListOf()) :
    RecyclerView.Adapter<RecentExpensesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDescription: TextView = view.findViewById(R.id.tvExpenseDescription)
        val tvAmount: TextView = view.findViewById(R.id.tvExpenseAmount)
        val tvDate: TextView = view.findViewById(R.id.tvExpenseDate)
        val ivImage: ImageView = view.findViewById(R.id.ivTransactionImage)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newList: List<Expense>) {
        expenses.clear()
        expenses.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val expense = expenses[position]
        val isIncome = expense.description == "Income" || expense.description.startsWith("+")
        holder.tvDescription.text = expense.description

        if (isIncome) {
            holder.tvAmount.text = "+R %.2f".format(expense.amount)
            holder.tvAmount.setTextColor("#2E7D32".toColorInt())
        } else {
            holder.tvAmount.text = "-R %.2f".format(expense.amount)
            holder.tvAmount.setTextColor("#C62828".toColorInt())
        }
        holder.tvDate.text = expense.date

        if (!expense.imagePath.isNullOrEmpty()) {
            try {
                holder.ivImage.visibility = View.VISIBLE

                // CASE 1: The image path is a remote HTTP/HTTPS cloud URL (Restored from Firestore)
                if (expense.imagePath.startsWith("http://") || expense.imagePath.startsWith("https://")) {
                    val targetUrl = expense.imagePath
                    // Set a default temporary placeholder icon while the image streams
                    holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)

                    // Spawn a lightweight background thread to pull image bytes without freezing the UI
                    java.lang.Thread {
                        try {
                            val url = java.net.URL(targetUrl)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.doInput = true
                            connection.connect()
                            val input = connection.inputStream
                            val bitmap = android.graphics.BitmapFactory.decodeStream(input)

                            // Post back to the Main thread pool to safely bind the bitmap
                            holder.ivImage.post {
                                holder.ivImage.setImageBitmap(bitmap)
                            }
                        } catch (e: Exception) {
                            Log.e("RecentExpensesAdapter", "Failed to stream cloud image bytes", e)
                        }
                    }.start()

                } else {
                    // CASE 2: The image path is an internal local storage file URI (Newly created)
                    val purePath = expense.imagePath.replace("file://", "").replace("file:/", "")
                    val imageFile = File(purePath)

                    if (imageFile.exists()) {
                        holder.ivImage.setImagePathCustom(imageFile)
                    } else {
                        holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }

                // Unified Click preview handling routine
                holder.ivImage.setOnClickListener {
                    try {
                        val context = holder.itemView.context
                        if (expense.imagePath.startsWith("http://") || expense.imagePath.startsWith("https://")) {
                            // View web URL externally
                            val intent = Intent(Intent.ACTION_VIEW, expense.imagePath.toUri())
                            context.startActivity(intent)
                        } else {
                            // Securely view local file via FileProvider content rules
                            val purePath = expense.imagePath.replace("file://", "").replace("file:/", "")
                            val imageFile = File(purePath)

                            if (imageFile.exists()) {
                                val secureContentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    imageFile
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(secureContentUri, "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Image file not found.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RecentExpensesAdapter", "Failed to open image previewer", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("RecentExpensesAdapter", "Error rendering layout item resource", e)
                holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            holder.ivImage.visibility = View.GONE
            holder.ivImage.setOnClickListener(null)
        }
    }

    override fun getItemCount() = expenses.size
}

fun ImageView.setImagePathCustom(file: File) {
    this.setImageURI(android.net.Uri.fromFile(file))
}