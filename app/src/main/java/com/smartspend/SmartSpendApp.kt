package com.smartspend

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.smartspend.data.database.SmartSpendDatabase

/**
 * Application entry point for SmartSpend.
 * Applies the user's saved dark mode preference on every launch.
 */
class SmartSpendApp : Application() {
    val database: SmartSpendDatabase by lazy {
        SmartSpendDatabase.getDatabase(this)
    }

    /**
     * Initialises the app and applies the saved dark/light mode preference globally.
     */
    override fun onCreate() {
        super.onCreate()
        // Apply saved dark mode preference globally on every app start/restart
        val prefs = getSharedPreferences("smartspend_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}