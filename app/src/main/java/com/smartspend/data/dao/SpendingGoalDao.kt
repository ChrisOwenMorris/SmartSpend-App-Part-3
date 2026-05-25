package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartspend.data.entity.SpendingGoal

@Dao
interface SpendingGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SpendingGoal): Long

    @Query("SELECT * FROM spending_goals WHERE userId = :userId AND month = :month LIMIT 1")
    suspend fun getSpendingGoalForMonth(userId: String, month: String): SpendingGoal?

    @Query("SELECT * FROM spending_goals WHERE userId = :userId ORDER BY month DESC LIMIT 1")
    suspend fun getLatestGoal(userId: String): SpendingGoal?
}
