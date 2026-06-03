package com.smartspend

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.SessionManager
import kotlinx.coroutines.launch
import androidx.core.content.edit

/**
 * Login screen for SmartSpend.
 * Handles email/password login, biometric login, and first-time Firestore data sync on success.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnTouchId: Button
    private lateinit var btnFaceId: Button
    private lateinit var tvSignUp: TextView
    private lateinit var ibTogglePassword: ImageButton
    private var isPasswordVisible = false

    private val sharedPrefs by lazy {
        getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = getSharedPreferences("smartspend_prefs", MODE_PRIVATE)
        val savedTheme = themePrefs.getString("theme_colour", "blue") ?: "blue"
        val themeRes = when (savedTheme) {
            "green"  -> R.style.Theme_SmartSpend_Green
            "purple" -> R.style.Theme_SmartSpend_Purple
            "orange" -> R.style.Theme_SmartSpend_Orange
            else     -> R.style.Theme_SmartSpend_Blue
        }
        setTheme(themeRes)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnTouchId = findViewById(R.id.btnTouchId)
        btnFaceId = findViewById(R.id.btnFaceId)
        tvSignUp = findViewById(R.id.tvSignUp)
        ibTogglePassword = findViewById(R.id.ibTogglePassword)

        ibTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                ibTogglePassword.contentDescription = "Hide password"
            } else {
                etPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                ibTogglePassword.contentDescription = "Show password"
            }
            etPassword.setSelection(etPassword.text.length)
        }

        /** Validates credentials, calls FirebaseRepository.loginUser, syncs Firestore data on first login, then navigates to Dashboard. */
        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("LoginActivity", "Attempting Firebase login for: $email")
            btnSignIn.isEnabled = false

            lifecycleScope.launch {
                val firebaseRepo = com.smartspend.data.firebase.FirebaseRepository()
                val success = firebaseRepo.loginUser(email, password)

                btnSignIn.isEnabled = true

                if (success) {
                    Log.d("LoginActivity", "Login successful for: $email")
                    sharedPrefs.edit { putBoolean("normal_login_done", true) }

                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    if (uid.isNotEmpty()) {
                        val isFirstSyncDone = sharedPrefs.getBoolean("data_synced_for_$uid", false)

                        if (!isFirstSyncDone) {
                            val db = (application as SmartSpendApp).database

                        // ─── SYNC PASS 1: EXPENSES ───────────────────────────────────
                        val expenses = firebaseRepo.getExpenses()
                        for (expenseMap in expenses) {
                            try {
                                val parsedId = when (val rawId = expenseMap["expenseId"]) {
                                    is Long -> rawId.toInt()
                                    is Int -> rawId
                                    is String -> rawId.toIntOrNull() ?: 0
                                    else -> 0
                                }

                                val rawCreatedAt = expenseMap["syncedAt"] ?: expenseMap["createdAt"]
                                val parsedCreatedAt = when (rawCreatedAt) {
                                    is Long -> rawCreatedAt
                                    is Double -> rawCreatedAt.toLong()
                                    else -> System.currentTimeMillis()
                                }

                                val expense = com.smartspend.data.entity.Expense(
                                    expenseId = parsedId,
                                    userId = uid,
                                    amount = (expenseMap["amount"] as? Double) ?: 0.0,
                                    description = (expenseMap["description"] as? String) ?: "",
                                    date = (expenseMap["date"] as? String) ?: "",
                                    startTime = (expenseMap["startTime"] as? String) ?: "00:00",
                                    endTime = (expenseMap["endTime"] as? String) ?: "00:00",
                                    categoryId = ((expenseMap["categoryId"] as? Long)?.toInt()) ?: 1,
                                    receiptPath = expenseMap["receiptPath"] as? String,
                                    imagePath = expenseMap["imagePath"] as? String,
                                    createdAt = parsedCreatedAt
                                )
                                db.expenseDao().insert(expense)
                            } catch (e: Exception) {
                                Log.e("LoginActivity", "Failed to restore expense: ${e.message}")
                            }
                        }
                        Log.d("LoginActivity", "Expenses data restored from Firestore to Room")

                        try {
                            val incomes = firebaseRepo.getIncomes()
                            for (incomeMap in incomes) {
                                try {
                                    val parsedIncomeId = when (val rawId = incomeMap["id"]) {
                                        is Long -> rawId.toInt()
                                        is Int -> rawId
                                        is String -> rawId.toIntOrNull() ?: 0
                                        else -> 0
                                    }

                                    val rawCreatedAt = incomeMap["syncedAt"] ?: incomeMap["createdAt"]
                                    val parsedCreatedAt = when (rawCreatedAt) {
                                        is Long -> rawCreatedAt
                                        is Double -> rawCreatedAt.toLong()
                                        else -> System.currentTimeMillis()
                                    }

                                    val income = com.smartspend.data.entity.Income(
                                        id = parsedIncomeId,
                                        userId = uid,
                                        source = (incomeMap["source"] as? String) ?: "Income",
                                        amount = (incomeMap["amount"] as? Double) ?: 0.0,
                                        date = (incomeMap["date"] as? String) ?: "",
                                        description = incomeMap["description"] as? String,
                                        createdAt = parsedCreatedAt,
                                        imagePath = incomeMap["imagePath"] as? String // Restores custom attachment URL references
                                    )
                                    db.incomeDao().insert(income)
                                } catch (e: Exception) {
                                    Log.e("LoginActivity", "Failed to parse individual income entry: ${e.message}")
                                }
                            }
                            Log.d("LoginActivity", "Income history restored from Firestore to Room")
                        } catch (e: Exception) {
                            Log.e("LoginActivity", "Failed to complete income table restoration loop: ${e.message}")
                        }
                            sharedPrefs.edit { putBoolean("data_synced_for_$uid", true) }
                            Log.d("LoginActivity", "Initial sync complete for user: $uid")
                        } else {
                            // 3. LOG THAT WE ARE SKIPPING
                            Log.d("LoginActivity", "Data already synced, skipping Firestore restore.")
                        }
                    }

                    goToDashboard()
                } else {
                    Log.e("LoginActivity", "Login failed for: $email — invalid credentials")
                    Toast.makeText(
                        this@LoginActivity,
                        "Invalid email or password",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnTouchId.setOnClickListener {
            startBiometricLogin()
        }

        btnFaceId.setOnClickListener {
            startBiometricLogin()
        }
    }

    private fun startBiometricLogin() {
        val biometricManager = BiometricManager.from(this)

        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt()
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(this, "No biometric hardware found", Toast.LENGTH_SHORT).show()
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(this, "Biometric hardware unavailable", Toast.LENGTH_SHORT).show()
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(this, "No fingerprint or face unlock is set up", Toast.LENGTH_SHORT).show()
            }

            else -> {
                Toast.makeText(this, "Biometric login is not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    val normalLoginDone = sharedPrefs.getBoolean("normal_login_done", false)

                    if (normalLoginDone) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Biometric login successful",
                            Toast.LENGTH_SHORT
                        ).show()

                        goToDashboard()
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Please login normally first before using biometrics",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    Toast.makeText(
                        this@LoginActivity,
                        errString.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()

                    Toast.makeText(
                        this@LoginActivity,
                        "Biometric authentication failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("SmartSpend Login")
            .setSubtitle("Use fingerprint or face unlock")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun goToDashboard() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (uid.isNotEmpty()) SessionManager.setUserId(uid)
        Log.d("LoginActivity", "Session started for: $uid")
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}