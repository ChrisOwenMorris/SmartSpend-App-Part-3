package com.smartspend.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spending_goals")
data class SpendingGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "",
    val minMonthlySpend: Double,
    val maxMonthlySpend: Double,
    val month: String
)
