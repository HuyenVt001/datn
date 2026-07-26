package com.example.snapget.core.model.auth

import com.google.firebase.auth.FirebaseUser

/**
 * Authentication user model that wraps a Firebase [FirebaseUser].
 */
data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    val avatar: String = "",
    val emailVerification: Boolean = false,
    val phoneVerification: Boolean = false,
    val prefs: Map<String, Any> = emptyMap(),
) {
    companion object {
        fun fromFirebaseUser(user: FirebaseUser): AuthUser = AuthUser(
            id = user.uid,
            email = user.email ?: "",
            name = user.displayName ?: user.email?.substringBefore("@") ?: "",
            avatar = user.photoUrl?.toString() ?: "",
            emailVerification = user.isEmailVerified,
            phoneVerification = !user.phoneNumber.isNullOrEmpty(),
        )
    }
}
