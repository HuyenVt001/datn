package com.example.snapget.core.data

import android.util.Log
import com.example.snapget.core.constants.FirestoreConfig
import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.auth.AuthUser
import com.example.snapget.core.util.mapToResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Repository Firestore truc tiep — CHI CON user hien tai (tu Firebase Auth)
 * va settings (cau hinh UI cua app, khong thuoc domain server).
 *
 * DA DON GOD-REPO (2026-07-13): toan bo posts/friends/messages/notifications
 * doc Firestore truc tiep da xoa — data domain di qua API server NestJS
 * (PostRepository, FriendsRepository, MessageRepository...).
 */
@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
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

    suspend fun getAllSetting(): List<Setting> {
        val snap = firestore.collection(FirestoreConfig.SETTINGS)
            .limit(50)
            .get()
            .await()

        Log.d(tag, "Fetched ${snap.size()} setting documents")
        return mapToResponse(snap, Setting::fromDocument)
    }

    suspend fun updateSetting(setting: Setting): Setting {
        val ref = firestore.collection(FirestoreConfig.SETTINGS).document(setting.id)
        val data = mapOf(
            "title" to setting.title,
            "description" to setting.description,
            "icon" to setting.icon,
            "type" to setting.type.name,
            "isToggleable" to setting.isToggleable,
            "isToggled" to setting.isToggled,
        )
        ref.update(data).await()
        return Setting.fromDocument(ref.get().await())
    }
}
