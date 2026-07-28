package com.example.snapget.core.network.interceptor

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.snapget.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Xu ly response 401 tu server (them 2026-07-28 — truoc day app KHONG he xu ly 401).
 *
 * Truoc: token het han giua chung / bi server thu hoi => moi request deu 401,
 * UI chi hien loi chung chung, user ket o man hinh trong ma khong biet phai
 * dang nhap lai. Tai khoan bi admin khoa cung khong bi day ra.
 *
 * Nay, khi gap 401 tu host server:
 *  1. Lan dau  -> ep lam moi ID token (`getIdToken(true)`) roi thu lai request.
 *  2. Van 401  -> ket luan phien khong con hieu luc -> `signOut()`.
 *
 * [AuthViewModel] lang nghe `FirebaseAuth.AuthStateListener` nen khi signOut
 * xay ra o day, UI tu dieu huong ve man hinh dang nhap.
 *
 * OkHttp goi `authenticate()` tren thread nen (background) nen `Tasks.await`
 * la an toan — giong cach [AuthInterceptor] dang lam.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val auth: FirebaseAuth,
) : Authenticator {

    private val serverHost: String? = BuildConfig.SERVER_BASE_URL.toHttpUrlOrNull()?.host

    override fun authenticate(route: Route?, response: Response): Request? {
        // Chi can thiep vao host server cua minh (giong bo loc cua AuthInterceptor).
        if (serverHost == null || response.request.url.host != serverHost) return null

        // Da thu lai 1 lan ma van 401 => token khong the cuu duoc nua.
        if (priorRetryCount(response) >= 1) {
            forceSignOut("van 401 sau khi da lam moi token")
            return null
        }

        val user = auth.currentUser ?: return null

        val freshToken = try {
            Tasks.await(user.getIdToken(true)).token
        } catch (e: Exception) {
            // Refresh that bai = tai khoan bi xoa/khoa, hoac refresh token bi revoke
            // (server goi revokeRefreshTokens khi admin khoa user).
            forceSignOut("khong lam moi duoc ID token: ${e.message}")
            return null
        }

        if (freshToken.isNullOrBlank()) {
            forceSignOut("ID token lam moi ra rong")
            return null
        }

        // Token moi trung token cu => khong phai loi het han, ma server chu dong
        // tu choi token nay. Thu lai cung vo ich, dang xuat luon.
        val oldToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (freshToken == oldToken) {
            forceSignOut("server tu choi token con han")
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }

    /** So lan request nay da duoc thu lai truoc do (theo chuoi priorResponse). */
    private fun priorRetryCount(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    /**
     * signOut() phai chay tren main thread vi AuthStateListener duoc goi lai o do.
     * KHONG log token/email — chi log ly do.
     */
    private fun forceSignOut(reason: String) {
        Log.w(TAG, "Phien dang nhap het hieu luc ($reason) -> dang xuat")
        Handler(Looper.getMainLooper()).post { auth.signOut() }
    }

    private companion object {
        const val TAG = "TokenAuthenticator"
    }
}
