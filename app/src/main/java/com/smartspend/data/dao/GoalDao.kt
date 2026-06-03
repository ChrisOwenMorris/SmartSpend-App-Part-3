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

    /** Returns all goals for the user as a Flow, ordered by goalId ascending. */
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY goalId ASC")
    fun getAllGoals(userId: String): Flow<List<Goal>>

    /** Returns the most recently created goal for the user as a Flow (used as the featured goal card). */
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY goalId DESC LIMIT 1")
    fun getFeaturedGoal(userId: String): Flow<Goal?>

    /** Returns all incomplete goals for the user as a Flow, ordered by goalId ascending. */
    @Query("SELECT * FROM goals WHERE userId = :userId AND isCompleted = 0 ORDER BY goalId ASC")
    fun getActiveGoals(userId: String): Flow<List<Goal>>

    /** Updates the currentAmount saved towards a goal for the given goalId. */
    @Query("UPDATE goals SET currentAmount = :amount WHERE goalId = :goalId")
    suspend fun updateCurrentAmount(goalId: Int, amount: Double)

    /** Marks a goal as completed by setting isCompleted to 1 for the given goalId. */
    @Query("UPDATE goals SET isCompleted = 1 WHERE goalId = :goalId")
    suspend fun markAsCompleted(goalId: Int)
}