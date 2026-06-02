package com.smartspend

import android.Manifest
import android.app.NotificationChannel
import android.util.Log
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val db by lazy {
        (application as SmartSpendApp).database
    }

    private lateinit var prefs: SharedPreferences

    // --- NOTIFICATION PERMISSION LAUNCHER --- //
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Notification permission denied. Enable it in device Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // --- EXISTING NAVIGATION CODE --- //
        NavigationHelper.setupMenu(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@SettingsActivity)
            }
        })

        // --- SHARED PREFERENCES --- //
        prefs = getSharedPreferences("smartspend_prefs", MODE_PRIVATE)

        // --- VIEWS --- //
        val switchTheme = findViewById<SwitchMaterial>(R.id.switchTheme)
        val switchBiometric = findViewById<SwitchMaterial>(R.id.switchBiometric)
        val switchSpendingAlerts = findViewById<SwitchMaterial>(R.id.switchSpendingAlerts)
        val switchReminders = findViewById<SwitchMaterial>(R.id.switchReminders)
        val switchGoalUpdates = findViewById<SwitchMaterial>(R.id.switchGoalUpdates)
        val btnChangePassword = findViewById<Button>(R.id.btnChangePassword)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)

        // --- LOAD SAVED PREFERENCES --- //
        switchTheme.isChecked = prefs.getBoolean("dark_mode", false)
        switchBiometric.isChecked = prefs.getBoolean("biometric_enabled", true)
        switchSpendingAlerts.isChecked = prefs.getBoolean("spending_alerts", true)
        switchReminders.isChecked = prefs.getBoolean("reminders", false)
        switchGoalUpdates.isChecked = prefs.getBoolean("goal_updates", true)

        // --- THEME SWITCH --- //
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // --- NOTIFICATION SWITCHES — request permission when turning on --- //
        switchSpendingAlerts.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestNotificationPermissionIfNeeded()
        }
        switchReminders.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestNotificationPermissionIfNeeded()
        }
        switchGoalUpdates.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestNotificationPermissionIfNeeded()
        }

        // --- CHANGE PASSWORD --- //
        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // --- SAVE SETTINGS --- //
        btnSaveSettings.setOnClickListener {

            // KTX edit extension — fixes "Use KTX extension" warning
            prefs.edit {
                putBoolean("dark_mode", switchTheme.isChecked)
                putBoolean("biometric_enabled", switchBiometric.isChecked)
                putBoolean("spending_alerts", switchSpendingAlerts.isChecked)
                putBoolean("reminders", switchReminders.isChecked)
                putBoolean("goal_updates", switchGoalUpdates.isChecked)
            }

            if (switchSpendingAlerts.isChecked) {
                sendNotification(
                    "Spending Alert",
                    "You are being notified about your spending activity.",
                    1
                )
            }
            if (switchReminders.isChecked) {
                sendNotification(
                    "Bill Reminder",
                    "You have upcoming bill and payment reminders.",
                    2
                )
            }
            if (switchGoalUpdates.isChecked) {
                sendNotification(
                    "Goal Update",
                    "Check your progress on your savings goals.",
                    3
                )
            }

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }
    }

    // --- REQUEST NOTIFICATION PERMISSION (Android 13+) --- //
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )) {
                PackageManager.PERMISSION_GRANTED -> {
                    // Already granted — nothing to do
                }
                else -> {
                    requestNotificationPermission.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }
        }
    }

    // --- CHANGE PASSWORD DIALOG --- //
    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmPassword)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val current = etCurrentPassword.text.toString()
                val new = etNewPassword.text.toString()
                val confirm = etConfirmPassword.text.toString()

                when {
                    current.isEmpty() || new.isEmpty() || confirm.isEmpty() -> {
                        Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    }
                    new != confirm -> {
                        Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                    }
                    new.length < 6 -> {
                        Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    }
                    else -> updatePassword(current, new)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- UPDATE PASSWORD VIA FIREBASE AUTH --- //
    private fun updatePassword(currentPassword: String, newPassword: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email

        if (user == null || email == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = com.google.firebase.auth.EmailAuthProvider
            .getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Toast.makeText(this,
                            "Password updated successfully",
                            Toast.LENGTH_SHORT).show()
                        Log.d("Settings", "Password updated via Firebase Auth")
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this,
                            "Failed to update: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this,
                    "Current password is incorrect",
                    Toast.LENGTH_SHORT).show()
            }
    }

    // --- SEND NOTIFICATION --- //
    private fun sendNotification(title: String, message: String, notificationId: Int) {
        val channelId = "smartspend_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SmartSpend Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    "Please enable notifications for SmartSpend in settings",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        // Check if notifications are enabled at the app level
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(
                this,
                "Please enable notifications for SmartSpend in settings",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "Please enable notifications for SmartSpend in settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
