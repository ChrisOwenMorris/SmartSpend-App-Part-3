package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.smartspend.data.entity.Income

@Dao
interface IncomeDao {

    /** Returns the 5 most recent income entries for the user ordered by createdAt descending. */
    @Query("SELECT * FROM income WHERE userId = :userId ORDER BY createdAt DESC LIMIT 5")
    suspend fun getRecentIncome(userId: String): List<Income>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(income: com.smartspend.data.entity.Income): Long

    /** Updates the imagePath for a given income entry ID. */
    @Query("UPDATE income SET imagePath = :path WHERE id = :id")
    suspend fun updateImagePath(id: Int, path: String)

    /** Returns all income entries for the user as a Flow, limited to 100 entries, ordered by date descending. */
    @Query("SELECT * FROM income WHERE userId = :userId ORDER BY date DESC, createdAt DESC LIMIT 100")
    fun getAllIncome(userId: String): Flow<List<com.smartspend.data.entity.Income>>

    @Delete
    suspend fun delete(income: com.smartspend.data.entity.Income)

    /** Returns the sum of income amounts for the given inclusive date range. */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM income WHERE userId = :userId AND date >= :startDate AND date <= :endDate")
    suspend fun getTotalIncomeByDateRange(userId: String, startDate: String, endDate: String): Double

    /** Returns income grouped and summed by source for the given date range. */
    @Query("SELECT source, SUM(amount) as total FROM income WHERE userId = :userId AND date BETWEEN :startDate AND :endDate GROUP BY source")
    suspend fun getIncomeBySource(userId: String, startDate: String, endDate: String): List<SourceSummary>
}

data class SourceSummary(
    val source: String,
    val total: Double
)