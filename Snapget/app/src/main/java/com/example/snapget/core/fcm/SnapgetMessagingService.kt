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
import com.example.snapget.core.data.SettingIds
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.FcmTokenRequest
import com.example.snapget.core.network.ensureSuccess
import com.example.snapget.feature.widget.SnapgetWidget
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

        /** data.type cua push nhac giu streak (server: UsersService.sendStreakReminders). */
        private const val TYPE_STREAK_REMINDER = "STREAK_REMINDER"

        /** Route gia "mo sheet ban be" — MainActivity/Navigation xu ly (FriendsSheetLauncher). */
        const val ROUTE_FRIENDS = "friends"

        /** Route gia "mo the loi moi chup chung" — Navigation dua ve Camera (the nam o do). */
        const val ROUTE_COOP_INVITE = "coop_invite"

        /** Trung PREFS_NAME cua SettingsPreferences (toggle luu theo setting id). */
        private const val SETTINGS_PREFS = "snapget_settings"

        /**
         * Create the default notification channel (Android O+). Call once at app start.
         *
         * ⚠️ Phai rao SDK: class `NotificationChannel` chi ton tai tu API 26.
         * Thieu rao la app SAP NGAY LUC MO (NoClassDefFoundError) tren Android
         * 7.0/7.1 — minSdk 24 van ho tro 2 ban nay. Duoi 26 khong co khai niem
         * channel, notification van hien binh thuong.
         */
        fun createDefaultChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return

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

        // Nhac giu streak (2026-11-15): server gui DATA-ONLY de app tu quyet hien hay
        // khong theo toggle "Streak reminders" (push co `notification` thi Android tu
        // hien khi app o nen, khong loc duoc). Bam -> mo thang Camera.
        if (message.data["type"] == TYPE_STREAK_REMINDER) {
            val enabled = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                .getBoolean(SettingIds.STREAK_REMINDERS, true)
            if (enabled) showNotification(title, body, route = "camera")
            return
        }

        // Loi moi ket ban / duoc chap nhan: bao VM dang song tai lai (app dang mo thi
        // banner Feed + badge Camera cap nhat ngay); bam thong bao -> mo sheet ban be
        val type = message.data["type"]
        if (FriendPushEvents.isFriendType(type)) {
            FriendPushEvents.emit(type!!)
            showNotification(title, body, route = ROUTE_FRIENDS)
            return
        }

        // Chup chung (2026-11-16): bao VM dang song cap nhat ngay (the loi moi / man
        // chup coop). COOP_CANCELLED la data-only -> chi cap nhat, KHONG hien thong bao.
        // Bam thong bao loi moi -> ve Camera (noi hien the Accept/Decline).
        if (CoopPushEvents.isCoopType(type)) {
            CoopPushEvents.emit(type!!, message.data["inviteId"])
            when (type) {
                CoopPushEvents.TYPE_COOP_CANCELLED -> Unit
                CoopPushEvents.TYPE_COOP_INVITE -> showNotification(title, body, route = ROUTE_COOP_INVITE)
                else -> showNotification(title, body)
            }
            return
        }

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String, route: String? = null) {
        // Android 13+ requires the POST_NOTIFICATIONS runtime permission.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping notification")
            return
        }

        val intent = (
            packageManager.getLaunchIntentForPackage(packageName)
                ?: android.content.Intent(this, MainActivity::class.java)
            ).apply {
            // Dung chung co che route cua widget (MainActivity.handleWidgetRoute)
            route?.let { putExtra(SnapgetWidget.EXTRA_WIDGET_ROUTE, it) }
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            // requestCode rieng theo route: UPDATE_CURRENT khong de extra cua thong bao khac
            route?.hashCode() ?: 0,
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
