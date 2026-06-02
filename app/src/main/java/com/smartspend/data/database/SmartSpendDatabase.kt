package com.smartspend.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    entities = [
        Expense::class,
        Category::class,
        Income::class,
        User::class,
        Goal::class,
        SpendingGoal::class
    ],
    version = 5,
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

        // Safe migration strategy to preserve existing database records
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN imagePath TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): SmartSpendDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartSpendDatabase::class.java,
                    "smartspend_db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}