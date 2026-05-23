package com.smartspend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            when {
                name.isEmpty() -> etName.error = "Enter your name"
                email.isEmpty() -> etEmail.error = "Enter your email"
                password.isEmpty() -> etPassword.error = "Enter your password"
                confirmPassword.isEmpty() -> etConfirmPassword.error = "Confirm your password"
                password != confirmPassword -> Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                else -> {
                    Log.d("RegisterActivity", "Attempting Firebase registration for: $email")
                    btnRegister.isEnabled = false

                    lifecycleScope.launch {
                        val firebaseRepo = com.smartspend.data.firebase.FirebaseRepository()
                        val success = firebaseRepo.registerUser(email, password, name)

                        btnRegister.isEnabled = true

                        if (success) {
                            Log.d("RegisterActivity", "Registration successful for: $email")
                            Toast.makeText(
                                this@RegisterActivity,
                                "Account created successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                            finish()
                        } else {
                            Log.e("RegisterActivity", "Registration failed for: $email — email may already be in use")
                            Toast.makeText(
                                this@RegisterActivity,
                                "Registration failed. Email may already be in use.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }
}
