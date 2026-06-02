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

class FirebaseRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ─── AUTH ─────────────────────────────────────────────────────────────────

    suspend fun registerUser(email: String, password: String, name: String): Boolean {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return false

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

    fun logoutUser() {
        auth.signOut()
        Log.d("FirebaseRepo", "User signed out")
    }

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    // ─── USERNAME HELPER ──────────────────────────────────────────────────────

    private suspend fun getCurrentUserName(): String {
        val uid = currentUserId ?: return "Unknown"
        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            doc.getString("name") ?: "Unknown"
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to get username: ${e.message}")
            "Unknown"
        }
    }

    // ─── EXPENSES ─────────────────────────────────────────────────────────────

    suspend fun saveExpense(expense: Expense, categoryName: String = ""): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val userName = getCurrentUserName()
            val desc = expense.description.replace(" ", "_").take(30)
            val docName = "${userName.replace(" ", "_")}_${desc}_${expense.expenseId}"

            val expenseMap = hashMapOf(
                "expenseId" to expense.expenseId,
                "amount" to expense.amount,
                "description" to expense.description,
                "date" to expense.date,
                "startTime" to expense.startTime,
                "endTime" to expense.endTime,
                "category" to categoryName,
                "receiptPath" to (expense.receiptPath ?: ""),
                "imagePath" to (expense.imagePath ?: ""),
                "userName" to userName,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("expenses")
                .document(docName)
                .set(expenseMap)
                .await()
            Log.d("FirebaseRepo", "Expense saved to Firestore: $docName")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save expense: ${e.message}")
            false
        }
    }

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

    suspend fun saveCategory(category: Category): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val userName = getCurrentUserName()
            val docName = "${userName.replace(" ", "_")}_${category.categoryName.replace(" ", "_")}_${category.categoryId}"

            val categoryMap = hashMapOf(
                "categoryId" to category.categoryId,
                "categoryName" to category.categoryName,
                "userName" to userName,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("categories")
                .document(docName)
                .set(categoryMap)
                .await()
            Log.d("FirebaseRepo", "Category saved: $docName")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save category: ${e.message}")
            false
        }
    }

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

    suspend fun saveGoal(goal: Goal): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val userName = getCurrentUserName()
            val docName = "${userName.replace(" ", "_")}_${goal.goalName.replace(" ", "_").take(30)}_${goal.goalId}"

            val goalMap = hashMapOf(
                "goalId" to goal.goalId,
                "userId" to goal.userId,
                "goalName" to goal.goalName,
                "targetAmount" to goal.targetAmount,
                "currentAmount" to goal.currentAmount,
                "targetDate" to goal.targetDate,
                "imagePath" to (goal.imagePath ?: ""),
                "isCompleted" to goal.isCompleted,
                "userName" to userName,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("goals")
                .document(docName)
                .set(goalMap)
                .await()
            Log.d("FirebaseRepo", "Goal saved: $docName")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save goal: ${e.message}")
            false
        }
    }

    // ─── INCOME ────────────────────────────────────────────────────────────────

    suspend fun saveIncome(income: Income): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val userName = getCurrentUserName()
            val desc = (income.description ?: income.source).replace(" ", "_").take(30)
            val docName = "${userName.replace(" ", "_")}_${desc}_${income.id}"

            val incomeMap = hashMapOf(
                "id" to income.id,
                "source" to income.source,
                "amount" to income.amount,
                "date" to income.date,
                "description" to (income.description ?: ""),
                "userName" to userName,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("income")
                .document(docName)
                .set(incomeMap)
                .await()
            Log.d("FirebaseRepo", "Income saved: $docName")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save income: ${e.message}")
            false
        }
    }

    // ─── SPENDING GOALS ────────────────────────────────────────────────────────

    suspend fun saveSpendingGoal(goal: SpendingGoal): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val userName = getCurrentUserName()
            val docName = "${userName.replace(" ", "_")}_spending_goal_${goal.month}"

            val goalMap = hashMapOf(
                "id" to goal.id,
                "minMonthlySpend" to goal.minMonthlySpend,
                "maxMonthlySpend" to goal.maxMonthlySpend,
                "month" to goal.month,
                "userName" to userName,
                "syncedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .collection("spending_goals")
                .document(docName)
                .set(goalMap)
                .await()
            Log.d("FirebaseRepo", "Spending goal saved: $docName")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save spending goal: ${e.message}")
            false
        }
    }

    // ─── BUDGETS ───────────────────────────────────────────────────────────────

    suspend fun saveBudget(month: Int, amount: Double): Boolean {
        val uid = currentUserId ?: return false
        return try {
            val userName = getCurrentUserName()
            val budgetMap = hashMapOf(
                "month" to month,
                "amount" to amount,
                "userName" to userName,
                "syncedAt" to System.currentTimeMillis()
            )
            val docName = "${userName.replace(" ", "_")}_budget_month_$month"
            firestore.collection("users")
                .document(uid)
                .collection("budgets")
                .document(docName)
                .set(budgetMap)
                .await()
            Log.d("FirebaseRepo", "Budget saved for month $month: $amount")
            true
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to save budget: ${e.message}")
            false
        }
    }

    // ─── STORAGE ───────────────────────────────────────────────────────────────

    suspend fun uploadExpenseImage(
        localUri: android.net.Uri,
        expenseId: Int
    ): String? {
        val uid = currentUserId ?: return null
        return try {
            val ref = storage.reference
                .child("users/$uid/expense_images/$expenseId.jpg")
            ref.putFile(localUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d("FirebaseRepo", "Image uploaded to Firebase Storage: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Image upload failed: ${e.message}")
            null
        }
    }

    suspend fun uploadPdfReport(
        localUri: android.net.Uri,
        fileName: String
    ): String? {
        val uid = currentUserId ?: return null
        return try {
            val userName = getCurrentUserName().replace(" ", "_")
            val ref = storage.reference
                .child("users/$uid/reports/${userName}_$fileName")
            ref.putFile(localUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d("FirebaseRepo", "PDF uploaded to Firebase Storage: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "PDF upload failed: ${e.message}")
            null
        }
    }
}
