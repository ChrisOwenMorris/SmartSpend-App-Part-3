package com.smartspend.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartspend.data.entity.Category
@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    /** Returns all categories for the given user, ordered alphabetically. */
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY categoryName ASC")
    suspend fun getAllCategories(userId: String): List<Category>
}