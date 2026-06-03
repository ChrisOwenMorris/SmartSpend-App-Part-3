package com.smartspend.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Manages user session state by delegating to Firebase Auth.
 * Provides helpers to get, set, and clear the active user session.
 */
object SessionManager {

    /**
     * Returns the current user's Firebase UID, or an empty string if not logged in.
     */
    fun getUserId(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        Log.d("SessionManager", "getUserId called: $uid")
        return uid
    }

    /** No-op — Firebase Auth manages the session automatically; provided for call-site compatibility. */
    fun setUserId(uid: String) {
        Log.d("SessionManager", "setUserId called: $uid (Firebase Auth manages session)")
    }

    /** Returns true if a user is currently authenticated with Firebase. */
    fun isLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    /** Signs the current user out of Firebase and clears the local session. */
    fun clearSession() {
        FirebaseAuth.getInstance().signOut()
        Log.d("SessionManager", "Session cleared")
    }
}
