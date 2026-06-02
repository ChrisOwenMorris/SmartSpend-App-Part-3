package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Embedded
import com.smartspend.data.entity.Expense

@Dao
interface ExpenseDao {

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Query("SELECT * FROM expenses WHERE userId = :userId")
    suspend fun getAllExpenses(userId: String): List<Expense>

    /**
     * Fetches all expenses for a user and embeds the corresponding category name
     * by performing an INNER JOIN on the categories table. Used to prevent generic
     * "Expense" descriptions on the UI feed.
     */
    @Query("""
        SELECT e.*, c.categoryName 
        FROM expenses e
        INNER JOIN categories c ON e.categoryId = c.categoryId
        WHERE e.userId = :userId
    """)
    suspend fun getAllExpensesWithCategoryNames(userId: String): List<ExpenseWithCategory>

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

/**
 * Data wrapper class combining the raw Expense entity with its relational Category Name string.
 * Used by [ExpenseDao.getAllExpensesWithCategoryNames].
 */
data class ExpenseWithCategory(
    @Embedded val expense: Expense,
    val categoryName: String
)

data class CategoryTotal(
    val categoryId: Int,
    val total: Double
)

data class CategoryWithTotal(
    val categoryName: String,
    val total: Double
)

data class CategorySummary(
    val categoryName: String,
    val total: Double
)

data class TrendSummary(
    val month: String,
    val total: Double
)