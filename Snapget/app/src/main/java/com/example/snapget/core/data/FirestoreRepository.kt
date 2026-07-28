package com.example.snapget.core.data

import android.util.Log
import com.example.snapget.core.model.auth.AuthUser
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository Firestore truc tiep — CHI CON user hien tai (tu Firebase Auth).
 *
 * DA DON GOD-REPO (2026-07-13): toan bo posts/friends/messages/notifications
 * doc Firestore truc tiep da xoa — data domain di qua API server NestJS
 * (PostRepository, FriendsRepository, MessageRepository...).
 * Settings cung da roi khoi day (2026-07-26): la config UI tinh, xem
 * [SettingDefaults] + [SettingsPreferences].
 */
@Singleton
class FirestoreRepository @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val tag = "FirestoreRepository"

    private var currentUserCache: AuthUser? = null

    suspend fun getCurrentUser(): AuthUser? {
        currentUserCache?.let { return it }

        return try {
            val fbUser = auth.currentUser ?: return null
            val authUser = AuthUser.fromFirebaseUser(fbUser)
            currentUserCache = authUser
            authUser
        } catch (e: Exception) {
            Log.e(tag, "Error fetching current user: ${e.message}")
            null
        }
    }

    /**
     * Xoa cache user trong bo nho — BAT BUOC goi khi dang xuat (2026-07-28).
     * Day la singleton song suot vong doi process: khong reset thi sau khi dang
     * xuat roi dang nhap tai khoan khac, [getCurrentUser] van tra ve nguoi cu.
     * Goi qua [SessionCleaner].
     */
    fun clearCache() {
        currentUserCache = null
    }
}
