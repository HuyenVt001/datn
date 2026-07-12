package com.example.snapget.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.snapget.core.util.displayDate
import com.google.firebase.firestore.DocumentSnapshot
import java.util.UUID

data class Notification(
    val id: String = UUID.randomUUID().toString(),
    val type: NotificationType,
    val title: String,
    val description: String,
    val time: String, // Formatted for display
    val isRead: Boolean,
    val icon: ImageVector,
    val iconColor: Color,
    val userId: String = "",
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): Notification = Notification(
            id = doc.id,
            type = runCatching { NotificationType.valueOf(doc.getString("type") ?: "") }
                .getOrDefault(NotificationType.SYSTEM_ALERT),
            title = doc.getString("title") ?: "",
            description = doc.getString("description") ?: "",
            time = doc.displayDate("createdAt"),
            isRead = doc.getBoolean("isRead") ?: false,
            icon = Icons.Default.Favorite,
            iconColor = Color(0xFFE91E63),
            userId = doc.getString("userId") ?: "",
        )
    }
}

enum class NotificationType {
    LIKE,
    COMMENT,
    FOLLOW,
    MESSAGE,
    FRIEND_REQUEST,
    SYSTEM_ALERT,
}
