package com.example.snapget.core.fcm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.snapget.MainActivity
import com.example.snapget.R
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.FcmTokenRequest
import com.example.snapget.core.network.ensureSuccess
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives FCM pushes and displays a local notification. Also keeps the current
 * user's FCM token synced with the server when it rotates.
 *
 * onNewToken di qua POST /users/me/fcm-tokens (fix 2026-07-27) — truoc day ghi
 * THANG Firestore tu client (sai kien truc "server la cua ngo duy nhat", va rules
 * co the chan) -> token xoay vong la mat push (loi moi ket ban, tin nhan...).
 */
@AndroidEntryPoint
class SnapgetMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userApi: UserApi

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "snapget_default"
        private const val TAG = "SnapgetFCM"

        /**
         * Create the default notification channel (Android O+). Call once at app start.
         */
        fun createDefaultChannel(context: Context) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Snapget",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Snapget notifications (quests, streaks, messages)" }

            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token received")
        // Chua dang nhap -> bo qua; token se duoc dang ky o lan mo app sau
        // (AuthRepository.ensureFcmTokenRegistered goi moi lan startup)
        if (FirebaseAuth.getInstance().currentUser == null) return
        serviceScope.launch {
            try {
                userApi.addFcmToken(FcmTokenRequest(token)).ensureSuccess()
                Log.d(TAG, "Rotated FCM token registered with server")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register rotated token: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Snapget"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        // Android 13+ requires the POST_NOTIFICATIONS runtime permission.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping notification")
            return
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: android.content.Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
