package com.smartspend.data.entity

import androidx.room.Embedded

data class ExpenseWithCategory(
    @Embedded val expense: Expense,
    val categoryName: String
)