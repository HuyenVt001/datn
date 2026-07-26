package com.example.snapget.core.network.interceptor

import android.util.Log
import com.example.snapget.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Gan Firebase ID token vao header `Authorization: Bearer <token>` cho request
 * toi server NestJS. Server verify token nay bang Firebase Admin SDK.
 *
 * CHI gan cho host cua server minh (fix 2026-07-26): OkHttpClient nay duoc Coil
 * va widget dung chung de tai anh — khong loc host thi token bi gui sang ca
 * Cloudinary/DiceBear/Twemoji (ro ri credential + ton 1 lan Tasks.await moi anh).
 *
 * getIdToken(false): SDK tu cache va tu refresh khi het han — khong tu cache tay.
 * Chay tren thread OkHttp (background) nen Tasks.await la an toan.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val auth: FirebaseAuth,
) : Interceptor {

    private val serverHost: String? = BuildConfig.SERVER_BASE_URL.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (serverHost == null || request.url.host != serverHost) {
            return chain.proceed(request) // anh/CDN ben ngoai — khong dinh token
        }

        val token = try {
            auth.currentUser?.let { Tasks.await(it.getIdToken(false)).token }
        } catch (e: Exception) {
            Log.w("AuthInterceptor", "Khong lay duoc ID token: ${e.message}")
            null
        }

        val authedRequest = if (token != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        return chain.proceed(authedRequest)
    }
}
