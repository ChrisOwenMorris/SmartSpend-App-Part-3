package com.smartspend.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val goalId: Int = 0,
    val userId: String = "",
    val goalName: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetDate: String,
    val imagePath: String? = null,
    val isCompleted: Boolean = false
)