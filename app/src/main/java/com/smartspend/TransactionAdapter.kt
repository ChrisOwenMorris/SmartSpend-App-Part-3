package com.smartspend

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartspend.data.entity.ExpenseWithCategory
import coil.load
import coil.transform.RoundedCornersTransformation

class TransactionAdapter(
    private var transactions: List<ExpenseWithCategory>,
    private val onImageClick: (String?, String?) -> Unit,
    // 🌟 ADDED: Optional callback for linking to existing transactions
    private val onItemClick: ((ExpenseWithCategory) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivTransactionImage)
        val tvTitle: TextView = view.findViewById(R.id.tvTransactionTitle)
        val tvDate: TextView = view.findViewById(R.id.tvTransactionDate)
        val tvCategory: TextView = view.findViewById(R.id.tvTransactionCategory)
        val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val item = transactions[position]
        val expense = item.expense

        // 1. Click Listeners
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }

        // 2. Styling
        val isIncome = expense.categoryId == -1
        val color = if (isIncome) R.color.status_success else R.color.status_danger
        val symbol = if (isIncome) "+" else "-"

        holder.tvTitle.text = expense.description
        holder.tvDate.text = expense.date
        holder.tvCategory.text = (if (isIncome) "" else "$symbol ") + item.categoryName
        holder.tvAmount.text = "$symbol R%.2f".format(kotlin.math.abs(expense.amount))
        holder.tvCategory.setTextColor(holder.itemView.context.getColor(color))
        holder.tvAmount.setTextColor(holder.itemView.context.getColor(color))

        // 3. Image Logic with Coil (Replaces all the old manual File/Bitmap code)
        val pathToDisplay = if (!expense.receiptPath.isNullOrEmpty()) {
            expense.receiptPath
        } else{
            expense.imagePath
        }

        if (!pathToDisplay.isNullOrEmpty()) {
            holder.ivImage.load(pathToDisplay) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_report_image)
                error(android.R.drawable.ic_menu_report_image)
                transformations(RoundedCornersTransformation(8f))
            }
            holder.ivImage.alpha = 1.0f

            holder.ivImage.setOnClickListener { onImageClick(expense.receiptPath, expense.imagePath) }
        } else {
            // Fallback for when no image exists
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_report_image)
            holder.ivImage.alpha = 1.0f
            holder.ivImage.setOnClickListener(null)
        }
    }

    private fun setPlaceholder(holder: TransactionViewHolder) {
        holder.ivImage.setImageResource(android.R.drawable.ic_menu_report_image)
        holder.ivImage.alpha = 0.3f
        // Only disable image click, keep row click for linking
        holder.ivImage.setOnClickListener(null)
    }

    override fun getItemCount(): Int = transactions.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newTransactions: List<ExpenseWithCategory>) {
        this.transactions = newTransactions
        notifyDataSetChanged()
    }
}