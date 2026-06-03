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

    /** Returns expenses for the user matching the given search text, category, and date filters. */
    @Query("""
    SELECT * FROM expenses
    WHERE userId = :userId
    AND (:categoryId = -1 OR categoryId = :categoryId)
    AND (:date = '' OR date = :date)
    AND (description LIKE '%' || :search || '%')
    ORDER BY createdAt DESC
""")
    suspend fun getFilteredExpenses(userId: String, search: String, categoryId: Int, date: String): List<Expense>

    /** Returns the 5 most recent expenses with their category name joined from the categories table. */
    @Query("""
    SELECT e.*, COALESCE(c.categoryName, 'Income') as categoryName
    FROM expenses e
    LEFT JOIN categories c ON e.categoryId = c.categoryId
    WHERE e.userId = :userId
    ORDER BY e.createdAt DESC
    LIMIT 5
""")
    suspend fun getRecentExpensesWithCategory(userId: String): List<ExpenseWithCategory>

    /** Updates the receiptPath for a given expense ID. */
    @Query("UPDATE expenses SET receiptPath = :path WHERE expenseId = :expenseId")
    suspend fun updateReceiptPath(expenseId: Int, path: String)

    /** Updates the original imagePath for a given expense ID. */
    @Query("UPDATE expenses SET imagePath = :path WHERE expenseId = :expenseId")
    suspend fun updateOriginalImagePath(expenseId: Int, path: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    /** Returns up to 100 expenses for the user ordered by date then createdAt descending. */
    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC, createdAt DESC LIMIT 100")
    suspend fun getAllExpenses(userId: String): List<Expense>

    /** Returns all expenses with category names as a Flow, using LEFT JOIN so income entries (categoryId=-1) are included. */
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

    /** Returns all expenses for the user within the given inclusive date range. */
    @Query("SELECT * FROM expenses WHERE userId = :userId AND date >= :startDate AND date <= :endDate")
    suspend fun getExpensesByDateRange(userId: String, startDate: String, endDate: String): List<Expense>

    /** Returns the sum of expense amounts for the given date range, excluding income entries (categoryId != -1). */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE userId = :userId AND date >= :startDate AND date <= :endDate AND categoryId != -1")
    suspend fun getTotalByDateRange(userId: String, startDate: String, endDate: String): Double

    /** Returns the top 4 category IDs and their total spend for the given date range. */
    @Query("SELECT categoryId, SUM(amount) as total FROM expenses WHERE userId = :userId AND date >= :startDate AND date <= :endDate GROUP BY categoryId ORDER BY total DESC LIMIT 4")
    suspend fun getTopCategoriesByDateRange(userId: String, startDate: String, endDate: String): List<CategoryTotal>

    /** Returns the top 4 categories by total spend with their names joined for the given date range. */
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

    /** Returns expenses grouped and summed by category name for the given date range (used for pie/y-axis charts). */
    @Query("""
        SELECT c.categoryName, SUM(e.amount) as total
        FROM expenses e
        INNER JOIN categories c ON e.categoryId = c.categoryId
        WHERE e.userId = :userId AND e.date BETWEEN :startDate AND :endDate
        GROUP BY c.categoryName
    """)
    suspend fun getExpensesGroupedByCategory(userId: String, startDate: String, endDate: String): List<CategorySummary>

    /** Returns monthly expense totals from the given start date onwards, for trend chart rendering. */
    @Query("""
        SELECT strftime('%m', date) as month, SUM(amount) as total
        FROM expenses
        WHERE userId = :userId AND date >= :sixMonthsAgo
        GROUP BY month
        ORDER BY date ASC
    """)
    suspend fun getMonthlyTrends(userId: String, sixMonthsAgo: String): List<TrendSummary>

    /** Updates the imagePath for a given expense ID after a successful Firebase Storage upload. */
    @Query("UPDATE expenses SET imagePath = :imagePath WHERE expenseId = :expenseId")
    suspend fun updateImagePath(expenseId: Int, imagePath: String)
}


data class CategoryTotal(val categoryId: Int, val total: Double)
data class CategoryWithTotal(val categoryName: String, val total: Double)
data class CategorySummary(val categoryName: String, val total: Double)
data class TrendSummary(val month: String, val total: Double)