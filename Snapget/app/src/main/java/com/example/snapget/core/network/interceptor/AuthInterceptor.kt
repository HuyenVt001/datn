package com.example.snapget.core.network.interceptor

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Gan Firebase ID token vao header `Authorization: Bearer <token>` cho MOI request
 * toi server NestJS. Server verify token nay bang Firebase Admin SDK.
 *
 * getIdToken(false): SDK tu cache va tu refresh khi het han — khong tu cache tay.
 * Chay tren thread OkHttp (background) nen Tasks.await la an toan.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val auth: FirebaseAuth,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = try {
            auth.currentUser?.let { Tasks.await(it.getIdToken(false)).token }
        } catch (e: Exception) {
            Log.w("AuthInterceptor", "Khong lay duoc ID token: ${e.message}")
            null
        }

        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
