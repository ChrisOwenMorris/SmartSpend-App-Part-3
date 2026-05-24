package com.smartspend.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.smartspend.data.entity.Expense
import com.smartspend.data.entity.Category
import com.smartspend.data.entity.Goal
import com.smartspend.data.entity.Income
import com.smartspend.data.entity.SpendingGoal
import kotlinx.coroutines.tasks.await

/**
 * FirebaseRepository handles all communication with Firebase services.
 * This includes Firebase Authentication, Firestore (online database),
 * and Firebase Storage (for receipt images).
 *
 * Reference: Firebase Android documentation - https://firebase.google.com/docs/android/setup
 */
class FirebaseRepository {

    // Firebase service instances
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    // The currently logged-in user's UID — used to scope all data per user
    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ─── AUTH ─────────────────────────────────────────────────────────────────

    /**
     * Register a new user with email and password using Firebase Authentication.
     * Returns true if successful, false otherwise.
     */
    suspend fun registerUser(email: String, password: String, name: String): Boolean {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return false

            // Save the user profile to Firestore users collection
            val userMap = hashMapOf(
                "uid" to uid,
                "name" to name,
                "email" to email,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(uid).set(userMap).await()
            Log.d("FirebaseRepo", "User registered successfully: $uid")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Registration failed: ${e.message}")
            false
        }
    }

    /**
     * Sign in an existing user with email and password.
     * Returns true if successful, false otherwise.
     */
    suspend fun loginUser(email: String, password: String): Boolean {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Log.d("FirebaseRepo", "Login successful for: $email")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Login failed: ${e.message}")
            false
        }
    }

    /**
     * Sign out the current user from Firebase.
     */
    fun logoutUser() {
        auth.signOut()
        Log.d("FirebaseRepo", "User signed out")
    }

    /**
     * Check if a user is currently logged in.
     */
    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    // ─── EXPENSES ─────────────────────────────────────────────────────────────

    /**
     * Save a single expense to Firestore under the current user's expenses subcollection.
     * Path: users/{userId}/expenses/{expenseId}
     */
    suspend fun saveExpense(expense: Expense, categoryName: String = ""): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val expenseMap = hashMapOf(
                "expenseId" to expense.expenseId,
                "amount" to expense.amount,
                "description" to expense.description,
                "date" to expense.date,
                "startTime" to expense.startTime,
                "endTime" to expense.endTime,
                "category" to categoryName,
                "receiptPath" to (expense.receiptPath ?: ""),
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("expenses")
                .document(expense.expenseId.toString())
                .set(expenseMap)
                .await()
            Log.d("FirebaseRepo", "Expense saved to Firestore: ${expense.expenseId}")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save expense: ${e.message}")
            false
        }
    }

    /**
     * Fetch all expenses for the current user from Firestore.
     * Returns a list of expense maps, or empty list on failure.
     */
    suspend fun getExpenses(): List<Map<String, Any>> {
        val uid = currentUserId ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("expenses")
                .get()
                .await()
            Log.d("FirebaseRepo", "Fetched ${snapshot.size()} expenses from Firestore")
            snapshot.documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to fetch expenses: ${e.message}")
            emptyList()
        }
    }

    // ─── CATEGORIES ────────────────────────────────────────────────────────────

    /**
     * Save a category to Firestore under the current user's categories subcollection.
     * Path: users/{userId}/categories/{categoryId}
     */
    suspend fun saveCategory(category: Category): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val categoryMap = hashMapOf(
                "categoryId" to category.categoryId,
                "categoryName" to category.categoryName,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("categories")
                .document(category.categoryId.toString())
                .set(categoryMap)
                .await()
            Log.d("FirebaseRepo", "Category saved: ${category.categoryName}")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save category: ${e.message}")
            false
        }
    }

    /**
     * Fetch all categories for the current user from Firestore.
     */
    suspend fun getCategories(): List<Map<String, Any>> {
        val uid = currentUserId ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("categories")
                .get()
                .await()
            snapshot.documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to fetch categories: ${e.message}")
            emptyList()
        }
    }

    // ─── GOALS ─────────────────────────────────────────────────────────────────

    /**
     * Save a spending goal to Firestore.
     * Path: users/{userId}/goals/{goalId}
     */
    suspend fun saveGoal(goal: Goal): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val goalMap = hashMapOf(
                "goalId" to goal.goalId,
                "goalName" to goal.goalName,
                "targetAmount" to goal.targetAmount,
                "currentAmount" to goal.currentAmount,
                "targetDate" to goal.targetDate,
                "imagePath" to (goal.imagePath ?: ""),
                "isCompleted" to goal.isCompleted,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("goals")
                .document(goal.goalId.toString())
                .set(goalMap)
                .await()
            Log.d("FirebaseRepo", "Goal saved: ${goal.goalName}")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save goal: ${e.message}")
            false
        }
    }

    // ─── INCOME ────────────────────────────────────────────────────────────────

    // ─── SPENDING GOALS ────────────────────────────────────────────────────────

    /**
     * Save a spending goal (min/max monthly) to Firestore.
     * Path: users/{userId}/spending_goals/{month}
     */
    suspend fun saveSpendingGoal(goal: SpendingGoal): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val goalMap = hashMapOf(
                "id" to goal.id,
                "minMonthlySpend" to goal.minMonthlySpend,
                "maxMonthlySpend" to goal.maxMonthlySpend,
                "month" to goal.month,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("spending_goals")
                .document(goal.month)
                .set(goalMap)
                .await()
            Log.d("FirebaseRepo", "Spending goal saved for: ${goal.month}")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save spending goal: ${e.message}")
            false
        }
    }

    /**
     * Save an income entry to Firestore.
     * Path: users/{userId}/income/{id}
     */
    suspend fun saveIncome(income: Income): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val incomeMap = hashMapOf(
                "id" to income.id,
                "source" to income.source,
                "amount" to income.amount,
                "date" to income.date,
                "description" to (income.description ?: ""),
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("income")
                .document(income.id.toString())
                .set(incomeMap)
                .await()
            Log.d("FirebaseRepo", "Income saved: ${income.id}")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save income: ${e.message}")
            false
        }
    }
}
