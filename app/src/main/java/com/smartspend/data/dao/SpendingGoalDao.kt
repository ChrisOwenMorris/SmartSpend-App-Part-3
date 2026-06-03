package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartspend.data.entity.SpendingGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendingGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SpendingGoal): Long

    // OPTIMIZED: Switched to Flow so changes to the current month's budget limit
    // update progress bars on the Dashboard layout automatically in real-time.
    @Query("SELECT * FROM spending_goals WHERE userId = :userId AND month = :month LIMIT 1")
    fun getSpendingGoalForMonth(userId: String, month: String): Flow<SpendingGoal?>

    // OPTIMIZED: Switched to Flow to track the newest financial goal state asynchronously.
    @Query("SELECT * FROM spending_goals WHERE userId = :userId ORDER BY month DESC LIMIT 1")
    fun getLatestGoal(userId: String): Flow<SpendingGoal?>
}