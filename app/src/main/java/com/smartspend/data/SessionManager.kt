package com.smartspend.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

object SessionManager {

    // Gets the current user ID directly from Firebase Auth
    // Falls back to empty string if no user is logged in
    fun getUserId(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        Log.d("SessionManager", "getUserId called: $uid")
        return uid
    }

    fun setUserId(uid: String) {
        Log.d("SessionManager", "setUserId called: $uid (Firebase Auth manages session)")
    }

    fun isLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    fun clearSession() {
        FirebaseAuth.getInstance().signOut()
        Log.d("SessionManager", "Session cleared")
    }
}
