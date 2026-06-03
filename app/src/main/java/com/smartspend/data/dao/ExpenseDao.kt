package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartspend.data.entity.ExpenseWithCategory
import com.smartspend.data.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // 🌟 FIX: Added OnConflictStrategy.REPLACE to instantly kill the login duplicate bug!
    @Query("""
    SELECT * FROM expenses 
    WHERE userId = :userId 
    AND (:categoryId = -1 OR categoryId = :categoryId)
    AND (:date = '' OR date = :date)
    AND (description LIKE '%' || :search || '%')
    ORDER BY createdAt DESC
""")
    suspend fun getFilteredExpenses(userId: String, search: String, categoryId: Int, date: String): List<Expense>
    @Query("""
    SELECT e.*, COALESCE(c.categoryName, 'Income') as categoryName 
    FROM expenses e
    LEFT JOIN categories c ON e.categoryId = c.categoryId
    WHERE e.userId = :userId 
    ORDER BY e.createdAt DESC 
    LIMIT 5
""")
    suspend fun getRecentExpensesWithCategory(userId: String): List<ExpenseWithCategory>
    @Query("UPDATE expenses SET receiptPath = :path WHERE expenseId = :expenseId")
    suspend fun updateReceiptPath(expenseId: Int, path: String)

    @Query("UPDATE expenses SET imagePath = :path WHERE expenseId = :expenseId")
    suspend fun updateOriginalImagePath(expenseId: Int, path: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC, createdAt DESC LIMIT 100")
    suspend fun getAllExpenses(userId: String): List<Expense>

    /**
     * 🌟 FIX: Changed INNER JOIN to LEFT JOIN & added COALESCE.
     * This ensures Income streams (categoryId = -1) are not deleted/ignored by the query.
     */
    @Query("""
        SELECT e.*, COALESCE(c.categoryName, 'Income') as categoryName 
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.categoryId
        WHERE e.userId = :userId
        ORDER BY e.date DESC, e.createdAt DESC
        LIMIT 100
    """)
    fun getAllExpensesWithCategoryNames(userId: String): Flow<List<ExpenseWithCategory>>

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses WHERE userId = :userId AND date >= :startDate AND date <= :endDate")
    suspend fun getExpensesByDateRange(userId: String, startDate: String, endDate: String): List<Expense>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE userId = :userId AND date >= :startDate AND date <= :endDate AND categoryId != -1")
    suspend fun getTotalByDateRange(userId: String, startDate: String, endDate: String): Double

    @Query("SELECT categoryId, SUM(amount) as total FROM expenses WHERE userId = :userId AND date >= :startDate AND date <= :endDate GROUP BY categoryId ORDER BY total DESC LIMIT 4")
    suspend fun getTopCategoriesByDateRange(userId: String, startDate: String, endDate: String): List<CategoryTotal>

    @Query("""
        SELECT c.categoryName, SUM(e.amount) as total
        FROM expenses e
        INNER JOIN categories c ON e.categoryId = c.categoryId
        WHERE e.userId = :userId AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY e.categoryId
        ORDER BY total DESC
        LIMIT 4
    """)
    suspend fun getTopCategoriesWithNames(userId: String, startDate: String, endDate: String): List<CategoryWithTotal>

    @Query("""
        SELECT c.categoryName, SUM(e.amount) as total
        FROM expenses e
        INNER JOIN categories c ON e.categoryId = c.categoryId
        WHERE e.userId = :userId AND e.date BETWEEN :startDate AND :endDate
        GROUP BY c.categoryName
    """)
    suspend fun getExpensesGroupedByCategory(userId: String, startDate: String, endDate: String): List<CategorySummary>

    @Query("""
        SELECT strftime('%m', date) as month, SUM(amount) as total
        FROM expenses
        WHERE userId = :userId AND date >= :sixMonthsAgo
        GROUP BY month
        ORDER BY date ASC
    """)
    suspend fun getMonthlyTrends(userId: String, sixMonthsAgo: String): List<TrendSummary>

    @Query("UPDATE expenses SET imagePath = :imagePath WHERE expenseId = :expenseId")
    suspend fun updateImagePath(expenseId: Int, imagePath: String)
}


data class CategoryTotal(val categoryId: Int, val total: Double)
data class CategoryWithTotal(val categoryName: String, val total: Double)
data class CategorySummary(val categoryName: String, val total: Double)
data class TrendSummary(val month: String, val total: Double)