package com.smartspend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.smartspend.data.entity.Goal

class GoalsAdapter(
    private var goals: List<Goal>,
    private val onGoalClick: (Goal) -> Unit
) : RecyclerView.Adapter<GoalsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Linked to the ImageView in item_goal.xml
        val ivGoalImage: ImageView = view.findViewById(R.id.ivGoalImage)
        val tvGoalName: TextView = view.findViewById(R.id.tvGoalName)
        val tvGoalDate: TextView = view.findViewById(R.id.tvGoalDate)
        val progressGoalItem: ProgressBar = view.findViewById(R.id.progressGoalItem)
        val tvGoalCurrentAmount: TextView = view.findViewById(R.id.tvGoalCurrentAmount)
        val tvGoalPercentage: TextView = view.findViewById(R.id.tvGoalPercentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_goal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val goal = goals[position]
        val ctx = holder.itemView.context

        val percentage = if (goal.targetAmount > 0)
            ((goal.currentAmount / goal.targetAmount) * 100).toInt()
        else 0

        // Set Basic Text Info
        holder.tvGoalName.text = goal.goalName
        holder.tvGoalDate.text = ctx.getString(R.string.target_date_format, goal.targetDate)

        // Set Progress Bar
        holder.progressGoalItem.progress = percentage

        // Set Amounts (Ensure R.string.goal_amount_format exists in strings.xml)
        val current = goal.currentAmount.toString().toDoubleOrNull() ?: 0.0
        val target = goal.targetAmount.toString().toDoubleOrNull() ?: 0.0

        holder.tvGoalCurrentAmount.text = ctx.getString(
            R.string.goal_amount_format,
            current,
            target
        )

        // Set Percentage Text
        holder.tvGoalPercentage.text = ctx.getString(R.string.percentage_format, percentage)
        holder.tvGoalPercentage.setTextColor(android.graphics.Color.BLACK)

        // Set the Goal Image
        if (!goal.imagePath.isNullOrEmpty()) {
            holder.ivGoalImage.setImageURI(goal.imagePath.toUri())
        } else {
            // Fallback to a default icon if no image is selected
            holder.ivGoalImage.setImageResource(R.mipmap.ic_launcher)
        }

        holder.itemView.setOnClickListener { onGoalClick(goal) }
    }

    override fun getItemCount() = goals.size

    fun updateGoals(newGoals: List<Goal>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = goals.size
            override fun getNewListSize(): Int = newGoals.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                goals[oldPos].goalId == newGoals[newPos].goalId
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                goals[oldPos] == newGoals[newPos]
        })
        goals = newGoals
        diffResult.dispatchUpdatesTo(this)
    }
}