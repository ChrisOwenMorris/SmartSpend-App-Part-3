package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartspend.data.entity.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    // Changed from suspend fun to returning a Flow list.
    // This allows real-time, lightweight updates without blocking the UI thread.
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY categoryName ASC")
    fun getAllCategories(userId: String): Flow<List<Category>>
}