package com.smartspend

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.smartspend.data.database.SmartSpendDatabase

class SmartSpendApp : Application() {
    val database: SmartSpendDatabase by lazy {
        SmartSpendDatabase.getDatabase(this)
    }

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