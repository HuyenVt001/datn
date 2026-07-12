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
import com.example.snapget.core.constants.FirestoreConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM pushes and displays a local notification. Also keeps the current
 * user's FCM token synced into their `users` document (field `fcmTokens`).
 *
 * NOTE: to SEND pushes you need a server/Cloud Function using the Admin SDK or the
 * FCM HTTP v1 API — the client only receives them.
 */
class SnapgetMessagingService : FirebaseMessagingService() {

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

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection(FirestoreConfig.USERS)
            .document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
            .addOnFailureListener { Log.e(TAG, "Failed to store token: ${it.message}") }
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
