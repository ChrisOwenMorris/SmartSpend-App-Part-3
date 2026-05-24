package com.smartspend.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val expenseId: Int = 0,
    val userId: String = "",
    val amount: Double,
    val description: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val categoryId: Int,
    val receiptPath: String?
)