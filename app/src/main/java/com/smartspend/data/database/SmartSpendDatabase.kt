package com.smartspend.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartspend.data.dao.CategoryDao
import com.smartspend.data.dao.ExpenseDao
import com.smartspend.data.dao.GoalDao
import com.smartspend.data.dao.IncomeDao
import com.smartspend.data.dao.SpendingGoalDao
import com.smartspend.data.dao.UserDao
import com.smartspend.data.entity.Category
import com.smartspend.data.entity.Expense
import com.smartspend.data.entity.Goal
import com.smartspend.data.entity.Income
import com.smartspend.data.entity.SpendingGoal
import com.smartspend.data.entity.User

@Database(
    entities = [Expense::class, Category::class, Income::class, User::class, Goal::class, SpendingGoal::class],
    version = 4,
    exportSchema = false
)
abstract class SmartSpendDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun userDao(): UserDao
    abstract fun goalDao(): GoalDao
    abstract fun incomeDao(): IncomeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun spendingGoalDao(): SpendingGoalDao

    companion object {
        @Volatile
        private var INSTANCE: SmartSpendDatabase? = null

        fun getDatabase(context: Context): SmartSpendDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartSpendDatabase::class.java,
                    "smartspend_db"
                )
                    .fallbackToDestructiveMigration() // Useful during development when changing schema
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}