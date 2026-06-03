package com.smartspend

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Singleton helper that attaches the hamburger popup menu to any activity and provides navigation utilities.
 */
object NavigationHelper {

    /**
     * Attaches the popup navigation menu to the activity's btnMenu view.
     * Handles navigation to all screens and logout with Room data clearing.
     */
    fun setupMenu(activity: Activity) {
        val menuButton = activity.findViewById<View>(R.id.btnMenu)

        menuButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(activity, view)

            popupMenu.menu.add("Dashboard")
            popupMenu.menu.add("Expenses")
            popupMenu.menu.add("Receipts")
            popupMenu.menu.add("Goals")
            popupMenu.menu.add("Reports")
            popupMenu.menu.add("Settings")
            popupMenu.menu.add("Logout")

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Dashboard" -> openScreen(activity, DashboardActivity::class.java)
                    "Expenses" -> openScreen(activity, ExpenseActivity::class.java)
                    "Receipts" -> openScreen(activity, ReceiptActivity::class.java)
                    "Goals" -> openScreen(activity, GoalsActivity::class.java)
                    "Reports" -> openScreen(activity, ReportsActivity::class.java)
                    "Settings" -> openScreen(activity, SettingsActivity::class.java)

                    "Logout" -> {
                        val db = (activity.application as SmartSpendApp).database
                        CoroutineScope(Dispatchers.IO).launch {
                            db.clearAllTables()
                            Log.d("Logout", "All local Room data cleared")
                        }
                        SessionManager.clearSession()
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(activity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        activity.startActivity(intent)
                        activity.finish()
                    }
                }
                true
            }

            popupMenu.show()
        }
    }

    /** Navigates to the target screen; skips the transition if the activity is already on that screen. */
    private fun openScreen(activity: Activity, screen: Class<*>) {
        if (activity::class.java == screen) return

        val intent = Intent(activity, screen)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        activity.startActivity(intent)
    }

    /**
     * Navigates to DashboardActivity, finishing the entire back stack if already on the Dashboard.
     */
    fun goToDashboard(activity: Activity) {
        if (activity is DashboardActivity) {
            activity.finishAffinity()
        } else {
            val intent = Intent(activity, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            activity.startActivity(intent)
            activity.finish()
        }
    }
}