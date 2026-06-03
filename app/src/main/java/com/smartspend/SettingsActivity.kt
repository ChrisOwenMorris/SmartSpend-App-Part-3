package com.smartspend

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.R.id.etProfileName

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // --- Theme colour chips ---
    private lateinit var themeBlue: FrameLayout
    private lateinit var themeGreen: FrameLayout
    private lateinit var themePurple: FrameLayout
    private lateinit var themeOrange: FrameLayout
    private var selectedThemeColour = "blue"

    // --- Currency options ---
    private val currencies = arrayOf(
        "ZAR (R) – South African Rand",
        "USD ($) – US Dollar",
        "EUR (€) – Euro",
        "GBP (£) – British Pound",
        "KES (Ksh) – Kenyan Shilling",
        "NGN (₦) – Nigerian Naira"
    )
    private val currencyCodes = arrayOf("ZAR", "USD", "EUR", "GBP", "KES", "NGN")
    private val currencySymbols = arrayOf("R", "$", "€", "£", "Ksh", "₦")

    // --- Notification permission launcher ---
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Notification permission denied. Enable in device Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
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
        setContentView(R.layout.activity_settings)

        NavigationHelper.setupMenu(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.goToDashboard(this@SettingsActivity)
            }
        })

        prefs = getSharedPreferences("smartspend_prefs", MODE_PRIVATE)

        // ── Bind views ──────────────────────────────────────────────────────────
        val switchTheme = findViewById<SwitchMaterial>(R.id.switchTheme)
        val tvThemeLabel = findViewById<TextView>(R.id.tvThemeLabel)
        val tvThemeSubLabel = findViewById<TextView>(R.id.tvThemeSubLabel)
        val switchBiometric = findViewById<SwitchMaterial>(R.id.switchBiometric)
        val switchSpendingAlerts = findViewById<SwitchMaterial>(R.id.switchSpendingAlerts)
        val switchReminders = findViewById<SwitchMaterial>(R.id.switchReminders)
        val switchGoalUpdates = findViewById<SwitchMaterial>(R.id.switchGoalUpdates)
        val switchAutoSaveReceipts = findViewById<SwitchMaterial>(R.id.switchAutoSaveReceipts)
        val rowChangePassword = findViewById<android.view.View>(R.id.rowChangePassword)
        val btnSaveSettings = findViewById<android.widget.Button>(R.id.btnSaveSettings)
        val btnEditProfile = findViewById<android.widget.Button>(R.id.btnEditProfile)
        val tvProfileName = findViewById<TextView>(R.id.tvProfileName)
        val tvProfileEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val tvAvatarInitial = findViewById<TextView>(R.id.tvAvatarInitial)
        val tvCurrencyValue = this.findViewById<TextView>(R.id.tvCurrencyValue)
        val rowManageReceipts = findViewById<android.view.View>(R.id.rowManageReceipts)

        // Theme colour chips
        themeBlue = findViewById(R.id.themeBlue)
        themeGreen = findViewById(R.id.themeGreen)
        themePurple = findViewById(R.id.themePurple)
        themeOrange = findViewById(R.id.themeOrange)

        // ── Load saved preferences ───────────────────────────────────────────────
        switchTheme.isChecked = prefs.getBoolean("dark_mode", false)
        switchBiometric.isChecked = prefs.getBoolean("biometric_enabled", true)
        switchSpendingAlerts.isChecked = prefs.getBoolean("spending_alerts", true)
        switchReminders.isChecked = prefs.getBoolean("reminders", false)
        switchGoalUpdates.isChecked = prefs.getBoolean("goal_updates", true)
        switchAutoSaveReceipts.isChecked = prefs.getBoolean("auto_save_receipts", true)
        selectedThemeColour = prefs.getString("theme_colour", "blue") ?: "blue"

        // Load profile from Firebase + prefs
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val savedName = prefs.getString("profile_name", firebaseUser?.displayName ?: "SmartSpend User") ?: "SmartSpend User"
        val email = firebaseUser?.email ?: prefs.getString("profile_email", "user@email.com") ?: "user@email.com"
        tvProfileName.text = savedName
        tvProfileEmail.text = email
        tvAvatarInitial.text = savedName.firstOrNull()?.uppercaseChar()?.toString() ?: "S"

        // Currency
        val savedCurrencyIdx = prefs.getInt("currency_index", 0)
        tvCurrencyValue.text = getString(
            R.string.currency_display_format,
            currencyCodes[savedCurrencyIdx],
            currencySymbols[savedCurrencyIdx]
        )
        // Theme label state and colour restore
        updateThemeLabel(tvThemeLabel, tvThemeSubLabel, switchTheme.isChecked)
        selectThemeColour(selectedThemeColour)

        // ── Theme switch ─────────────────────────────────────────────────────────
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("dark_mode", isChecked) }
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            updateThemeLabel(tvThemeLabel, tvThemeSubLabel, isChecked)
            recreate()
        }

        // ── Theme colour chips ───────────────────────────────────────────────────
        themeBlue.setOnClickListener   { selectThemeColour("blue") }
        themeGreen.setOnClickListener  { selectThemeColour("green") }
        themePurple.setOnClickListener { selectThemeColour("purple") }
        themeOrange.setOnClickListener { selectThemeColour("orange") }

        // ── Notification switches ────────────────────────────────────────────────
        switchSpendingAlerts.setOnCheckedChangeListener { _, _ -> }
        switchReminders.setOnCheckedChangeListener { _, _ -> }
        switchGoalUpdates.setOnCheckedChangeListener { _, _ -> }

        // ── Change Password ──────────────────────────────────────────────────────
        rowChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // ── Edit Profile ─────────────────────────────────────────────────────────
        btnEditProfile.setOnClickListener {
            showEditProfileDialog(tvProfileName, tvAvatarInitial)
        }

        // ── Currency selection ───────────────────────────────────────────────────
        // make the arrow clickable
        findViewById<android.widget.ImageView>(R.id.ivCurrencyArrow).setOnClickListener {
            showCurrencyDialog(tvCurrencyValue)
        }

        // ── Manage Receipts ──────────────────────────────────────────────────────
        rowManageReceipts.setOnClickListener {
            Toast.makeText(this, "Receipt management coming soon", Toast.LENGTH_SHORT).show()
        }

        // ── Save Settings ────────────────────────────────────────────────────────
        btnSaveSettings.setOnClickListener {
            if (switchSpendingAlerts.isChecked ||
                switchReminders.isChecked ||
                switchGoalUpdates.isChecked) {
                requestNotificationPermissionIfNeeded()
            }

            prefs.edit {
                putBoolean("dark_mode", switchTheme.isChecked)
                putBoolean("biometric_enabled", switchBiometric.isChecked)
                putBoolean("spending_alerts", switchSpendingAlerts.isChecked)
                putBoolean("reminders", switchReminders.isChecked)
                putBoolean("goal_updates", switchGoalUpdates.isChecked)
                putBoolean("auto_save_receipts", switchAutoSaveReceipts.isChecked)
                putString("theme_colour", selectedThemeColour)
            }

            AppCompatDelegate.setDefaultNightMode(
                if (switchTheme.isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )

            prefs.edit { putString("theme_colour", selectedThemeColour) }
            Toast.makeText(this,
                "Settings saved — restart the app to apply theme color",
                Toast.LENGTH_SHORT).show()
            recreate()
            return@setOnClickListener
        }
    }

    // ── Theme label helper ────────────────────────────────────────────────────────
    private fun updateThemeLabel(label: TextView, sub: TextView, isDark: Boolean) {
        label.text = if (isDark) "Dark Mode" else "Light Mode"
        sub.text = if (isDark) "Dark theme active" else "Light theme active"
    }

    // ── Theme colour chip selection ───────────────────────────────────────────────
    private fun selectThemeColour(colour: String) {
        selectedThemeColour = colour
        updateThemeChips(colour)
    }

    private fun updateThemeChips(selected: String) {
        val checks = mapOf(
            "blue" to (themeBlue to R.id.checkBlue),
            "green" to (themeGreen to R.id.checkGreen),
            "purple" to (themePurple to R.id.checkPurple),
            "orange" to (themeOrange to R.id.checkOrange)
        )
        checks.forEach { (colour, pair) ->
            val (frame, checkId) = pair
            frame.findViewById<TextView>(checkId).visibility =
                if (colour == selected) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    // ── Currency dialog ───────────────────────────────────────────────────────────
    private fun showCurrencyDialog(tvCurrencyValue: TextView) {
        val savedIdx = prefs.getInt("currency_index", 0)
        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setSingleChoiceItems(currencies, savedIdx) { dialog, which ->
                prefs.edit { putInt("currency_index", which) }
                tvCurrencyValue.text = getString(
                    R.string.currency_display_format,
                    currencyCodes[which],
                    currencySymbols[which]
                )
                dialog.dismiss()
                Toast.makeText(this, "Currency set to ${currencyCodes[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Edit profile dialog ────────────────────────────────────────────────────────
    private fun showEditProfileDialog(tvProfileName: TextView, tvAvatarInitial: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(etProfileName)
        etName.setText(tvProfileName.text)

        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                tvProfileName.text = newName
                tvAvatarInitial.text = newName.firstOrNull()?.uppercaseChar()?.toString() ?: "S"
                prefs.edit { putString("profile_name", newName) }
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Change password dialog ────────────────────────────────────────────────────
    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<android.widget.EditText>(R.id.etCurrentPassword)
        val etNewPassword     = dialogView.findViewById<android.widget.EditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<android.widget.EditText>(R.id.etConfirmPassword)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)

            .setPositiveButton("Update") { _, _ ->
                val current = etCurrentPassword.text.toString()
                val new     = etNewPassword.text.toString()
                val confirm = etConfirmPassword.text.toString()
                when {
                    current.isEmpty() || new.isEmpty() || confirm.isEmpty() ->
                        Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    new != confirm ->
                        Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                    new.length < 6 ->
                        Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    else -> updatePassword(current, new)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    // ── Firebase password  ──────────────────────────────────────────────────
    private fun updatePassword(currentPassword: String, newPassword: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (user == null || email == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                        Log.d("Settings", "Password updated via Firebase Auth")
                    }
                    .addOnFailureListener { e: Exception ->
                        Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Notification permission ────────────────────────────────────────────────────
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── Send notification ─────────────────────────────────────────────────────────
    private fun sendNotification(title: String, message: String, notificationId: Int) {
        val channelId = "smartspend_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "SmartSpend Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Enable notifications in device Settings", Toast.LENGTH_LONG).show()
            return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(this, "Enable notifications for SmartSpend in Settings", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Enable notifications for SmartSpend in Settings", Toast.LENGTH_LONG).show()
        }
    }
}
