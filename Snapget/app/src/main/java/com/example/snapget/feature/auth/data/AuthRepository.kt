package com.example.snapget.feature.auth.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.snapget.core.model.auth.AuthUser
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.FcmTokenRequest
import com.example.snapget.core.network.dto.UpdateUserRequest
import com.example.snapget.core.network.ensureSuccess
import com.example.snapget.core.network.unwrap
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository xac thuc: dang nhap/dang ky van qua Firebase Auth (client SDK),
 * nhung DU LIEU user di qua server NestJS (khong ghi Firestore truc tiep nua):
 * - GET /users/me: server tu tao user doc khi dang nhap lan dau
 * - PATCH /users/me: dong bo ten hien thi / avatar
 * - POST /users/me/fcm-tokens: dang ky token nhan push
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val userApi: UserApi,
) {
    private val tag = "AuthRepository"

    /**
     * Check if a user is currently authenticated.
     */
    suspend fun isAuthenticated(): Boolean = auth.currentUser != null

    /**
     * Get the current authenticated user, or null if signed out.
     */
    suspend fun getCurrentUser(): AuthUser? = auth.currentUser?.let { AuthUser.fromFirebaseUser(it) }

    /**
     * Register a new user with email and password, set the display name and
     * sync the profile to the server.
     */
    suspend fun register(email: String, password: String, name: String): AuthUser? = try {
        // KHONG log email (PII) — xem SECURITY.md muc 8.6
        Log.d(tag, "Registering new user")
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val fbUser = result.user ?: return null

        fbUser.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build(),
        ).await()

        syncWithServer(fullName = name)

        AuthUser.fromFirebaseUser(fbUser).copy(name = name)
    } catch (e: Exception) {
        Log.e(tag, "Registration failed: ${e.message}", e)
        throw e
    }

    /**
     * Login with email and password.
     */
    suspend fun login(email: String, password: String): AuthUser? = try {
        Log.d(tag, "Logging in user")
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user?.let {
            syncWithServer()
            AuthUser.fromFirebaseUser(it)
        }
    } catch (e: Exception) {
        Log.e(tag, "Login failed: ${e.message}", e)
        throw e
    }

    /**
     * Google Sign-In using Credential Manager.
     *
     * Requires `R.string.default_web_client_id`, which the `google-services`
     * Gradle plugin generates from `google-services.json` once Google sign-in
     * is enabled in the Firebase console. [activity] must be an Activity context.
     */
    suspend fun signInWithGoogle(activity: Context): AuthUser? {
        Log.d(tag, "Starting Google sign-in via Credential Manager")

        // Resolved at runtime so the app still compiles before Google sign-in is
        // enabled in the Firebase console (which is what generates this resource).
        val clientIdRes = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName,
        )
        if (clientIdRes == 0) {
            Log.e(tag, "default_web_client_id missing — enable Google sign-in in the Firebase console")
            return null
        }

        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(clientIdRes))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = credentialManager.getCredential(activity, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val fbUser = authResult.user ?: return null

            // CHI day ten/avatar Google len server o LAN DANG NHAP DAU (tao tai khoan
            // — fix 2026-08-03). Truoc day MOI lan re-login deu PATCH fullName/avatar
            // theo profile Google/Firebase Auth -> ten (va avatar) user da doi trong
            // app bi ghi de nguoc ve gia tri cu sau khi dang xuat + dang nhap lai.
            // Ho so tren server la nguon chuan; cac lan login sau chi sync FCM token.
            if (authResult.additionalUserInfo?.isNewUser == true) {
                syncWithServer(
                    fullName = fbUser.displayName ?: googleIdTokenCredential.displayName,
                    avatar = fbUser.photoUrl?.toString(),
                )
            } else {
                syncWithServer()
            }

            return AuthUser.fromFirebaseUser(fbUser)
        }

        Log.e(tag, "Unexpected credential type: ${credential.type}")
        return null
    }

    /**
     * Logout the current user (go FCM token khoi server truoc).
     */
    suspend fun logout(): Boolean = try {
        removeFcmTokenFromServer()
        auth.signOut()
        true
    } catch (e: Exception) {
        Log.e(tag, "Logout failed: ${e.message}", e)
        false
    }

    /**
     * ⚠️ KHONG phai "dang xuat khoi moi thiet bi" theo dung nghia.
     *
     * Firebase client SDK khong the thu hoi phien tren may khac — chi Admin SDK
     * (`revokeRefreshTokens`) o server lam duoc, va server hien CHUA co endpoint
     * do cho user thuong (chi dung khi admin khoa tai khoan).
     *
     * Vi vay ham nay chi dang xuat tren MAY NAY. Giu ten cu de khong pha API,
     * nhung UI TUYET DOI khong duoc hua "sign out from all devices" voi user
     * cho toi khi server co endpoint that. Xem SECURITY.md muc 14.3.
     */
    suspend fun logoutFromAllDevices(): Boolean = logout()

    /**
     * Phat su kien moi khi Firebase Auth chuyen sang trang thai CHUA dang nhap.
     *
     * Dung de UI phan ung khi phien bi thu hoi tu ben ngoai (admin khoa tai khoan,
     * doi mat khau tren thiet bi khac, hoac [TokenAuthenticator] tu dang xuat sau
     * khi gap 401 lien tiep) — truoc day nhung truong hop nay khong day user ve
     * man hinh dang nhap, ho ket lai voi mot man hinh loi.
     */
    fun signedOutEvents(): Flow<Unit> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) {
                trySend(Unit)
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Send a password reset email.
     */
    suspend fun resetPassword(email: String): Boolean = try {
        Log.d(tag, "Sending password reset email")
        auth.sendPasswordResetEmail(email).await()
        true
    } catch (e: Exception) {
        Log.e(tag, "Password reset failed: ${e.message}", e)
        false
    }

    /**
     * Dong bo user voi server sau khi dang nhap/dang ky:
     * 1. GET /users/me — server TU TAO user doc neu chua co (dang nhap lan dau).
     * 2. Co fullName/avatar moi (register / Google) -> PATCH /users/me.
     * 3. Dang ky FCM token cua thiet bi de nhan push.
     *
     * Loi mang/server KHONG chan luong dang nhap — chi log warn, lan dang nhap
     * sau se sync lai.
     */
    private suspend fun syncWithServer(fullName: String? = null, avatar: String? = null) {
        try {
            val me = userApi.getMe().unwrap()
            Log.d(tag, "Synced user with server: ${me.uid}")

            if ((fullName != null && fullName != me.fullName) ||
                (avatar != null && avatar != me.avatar)
            ) {
                userApi.updateMe(UpdateUserRequest(fullName = fullName, avatar = avatar))
                    .ensureSuccess()
            }

            val token = FirebaseMessaging.getInstance().token.await()
            userApi.addFcmToken(FcmTokenRequest(token)).ensureSuccess()
            Log.d(tag, "FCM token registered with server")
        } catch (e: Exception) {
            Log.w(tag, "Sync with server failed (se thu lai lan dang nhap sau): ${e.message}")
        }
    }

    /**
     * Dang ky FCM token cua thiet bi voi server — goi MOI lan mo app da dang nhap.
     * Truoc day chi dang ky luc login (syncWithServer): lan do fail (mat mang/server
     * chua chay) la user KHONG BAO GIO nhan push (loi moi ket ban, tin nhan...)
     * vi phien dang nhap giu nguyen, khong login lai. Best-effort, loi chi log.
     */
    suspend fun ensureFcmTokenRegistered() {
        if (auth.currentUser == null) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            userApi.addFcmToken(FcmTokenRequest(token)).ensureSuccess()
            Log.d(tag, "FCM token registered with server")
        } catch (e: Exception) {
            Log.w(tag, "Khong dang ky duoc FCM token: ${e.message}")
        }
    }

    /** Go FCM token cua thiet bi nay khoi server truoc khi logout. */
    private suspend fun removeFcmTokenFromServer() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            userApi.removeFcmToken(token).ensureSuccess()
        } catch (e: Exception) {
            Log.w(tag, "Khong go duoc FCM token: ${e.message}")
        }
    }
}
