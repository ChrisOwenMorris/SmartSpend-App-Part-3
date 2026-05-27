package com.smartspend

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartspend.data.entity.Expense

class RecentExpensesAdapter(private val expenses: MutableList<Expense> = mutableListOf()) :
    RecyclerView.Adapter<RecentExpensesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDescription: TextView = view.findViewById(R.id.tvExpenseDescription)
        val tvAmount: TextView = view.findViewById(R.id.tvExpenseAmount)
        val tvDate: TextView = view.findViewById(R.id.tvExpenseDate)
        val ivImage: ImageView = view.findViewById(R.id.ivTransactionImage)
    }

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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val expense = expenses[position]
        val isIncome = expense.description == "Income" || expense.description.startsWith("+")
        holder.tvDescription.text = expense.description
        if (isIncome) {
            holder.tvAmount.text = "+R %.2f".format(expense.amount)
            holder.tvAmount.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
        } else {
            holder.tvAmount.text = "-R %.2f".format(expense.amount)
            holder.tvAmount.setTextColor(android.graphics.Color.parseColor("#C62828"))
        }
        holder.tvDate.text = expense.date

        if (!expense.imagePath.isNullOrEmpty()) {
            val uri = Uri.parse(expense.imagePath)
            holder.ivImage.setImageURI(uri)
            holder.ivImage.visibility = View.VISIBLE
            holder.ivImage.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                holder.itemView.context.startActivity(intent)
            }
        } else {
            holder.ivImage.visibility = View.GONE
            holder.ivImage.setOnClickListener(null)
        }
    }

    override fun getItemCount() = expenses.size
}