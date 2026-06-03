package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.smartspend.data.entity.Goal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    // OPTIMIZED: Switched to Flow so the goals view matches any additions instantly
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY goalId ASC")
    fun getAllGoals(userId: String): Flow<List<Goal>>

    // OPTIMIZED: Switched to Flow so your Dashboard's "Featured Goal" card updates
    // reactively the exact moment a user allocates money towards it
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY goalId DESC LIMIT 1")
    fun getFeaturedGoal(userId: String): Flow<Goal?>

    // OPTIMIZED: Switched to Flow for real-time tracking of active milestones
    @Query("SELECT * FROM goals WHERE userId = :userId AND isCompleted = 0 ORDER BY goalId ASC")
    fun getActiveGoals(userId: String): Flow<List<Goal>>

    @Query("UPDATE goals SET currentAmount = :amount WHERE goalId = :goalId")
    suspend fun updateCurrentAmount(goalId: Int, amount: Double)

    @Query("UPDATE goals SET isCompleted = 1 WHERE goalId = :goalId")
    suspend fun markAsCompleted(goalId: Int)
}