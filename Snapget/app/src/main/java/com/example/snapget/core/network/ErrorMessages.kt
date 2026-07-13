package com.example.snapget.core.network

import com.google.gson.Gson
import retrofit2.HttpException

/**
 * Boc message tieng Viet cua server ra khoi Throwable bat ky.
 *
 * Retrofit nem HttpException voi MOI response 4xx/5xx — `message` cua no chi la
 * "HTTP 400 Bad Request" (vo nghia voi user). Message nghiep vu that cua server
 * ("Loi moi da het han (qua 24 gio).", "Nhom chat toi da 20 thanh vien."...)
 * nam trong errorBody theo envelope { success, statusCode, message, data }.
 * Moi catch hien loi cho user PHAI dung ham nay thay vi `e.message ?:`.
 */
fun Throwable.serverMessage(fallback: String): String {
    if (this is HttpException) {
        try {
            val body = response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                val envelope = Gson().fromJson(body, ApiResponse::class.java)
                envelope?.message?.takeIf { it.isNotBlank() }?.let { return it }
            }
        } catch (_: Exception) {
            // errorBody khong phai JSON envelope -> dung fallback
        }
        return fallback // "HTTP 4xx ..." khong dang hien cho user
    }
    return message?.takeIf { it.isNotBlank() } ?: fallback
}
