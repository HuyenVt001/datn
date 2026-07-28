package com.example.snapget.core.data

import android.content.Context
import android.util.Log
import coil3.SingletonImageLoader
import com.example.snapget.feature.friends.data.PendingInviteStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Xoa sach du lieu cuc bo cua phien dang nhap (them 2026-07-28).
 *
 * Truoc day dang xuat chi goi `auth.signOut()` + xoa snapshot widget, con lai
 * giu nguyen tren may:
 *  - Coil disk cache toi 512MB ANH/VIDEO RIENG TU cua tai khoan cu -> nguoi
 *    dung tiep theo tren cung thiet bi van xem duoc qua cache.
 *  - `FirestoreRepository.currentUserCache` (bien in-memory) khong bao gio reset
 *    -> co the tra ve thong tin tai khoan CU sau khi dang nhap tai khoan moi.
 *  - Ma moi ket ban dang cho trong SharedPreferences.
 *
 * Duoc goi o CA HAI duong dang xuat: user tu bam Sign Out, va phien bi thu hoi
 * (xem [com.example.snapget.core.network.interceptor.TokenAuthenticator]).
 *
 * Best-effort: loi khi don dep KHONG duoc lam hong luong dang xuat.
 */
@Singleton
class SessionCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestoreRepository: FirestoreRepository,
) {

    suspend fun clear() = withContext(Dispatchers.IO) {
        firestoreRepository.clearCache()

        runCatching { PendingInviteStore.consume(context) }
            .onFailure { Log.w(TAG, "Khong xoa duoc pending invite: ${it.message}") }

        runCatching {
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        }.onFailure { Log.w(TAG, "Khong xoa duoc cache anh: ${it.message}") }
    }

    private companion object {
        const val TAG = "SessionCleaner"
    }
}
