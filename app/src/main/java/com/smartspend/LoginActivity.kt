package com.smartspend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnTouchId: Button
    private lateinit var btnFaceId: Button
    private lateinit var tvSignUp: TextView

    private val sharedPrefs by lazy {
        getSharedPreferences("SmartSpendPrefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnTouchId = findViewById(R.id.btnTouchId)
        btnFaceId = findViewById(R.id.btnFaceId)
        tvSignUp = findViewById(R.id.tvSignUp)

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
                    sharedPrefs.edit().putBoolean("normal_login_done", true).apply()
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
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
