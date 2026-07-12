package com.example.snapget.core.model

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.snapget.core.util.isoDateTime
import com.google.firebase.firestore.DocumentSnapshot
import java.time.LocalDateTime
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.O)
data class Message
@RequiresApi(Build.VERSION_CODES.O)
constructor(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val recipientId: String,
    val previewContent: String = "", // one line preview of the message content
    var content: String = "", // full message content, can be empty for preview
    val timeSent: String = LocalDateTime.now().toString(), // ISO 8601 format
    val isRead: Boolean = false,
) {
    companion object {
        @RequiresApi(Build.VERSION_CODES.O)
        fun fromDocument(doc: DocumentSnapshot): Message = Message(
            id = doc.id,
            senderId = doc.getString("senderId") ?: "",
            recipientId = doc.getString("recipientId") ?: "",
            previewContent = doc.getString("previewContent") ?: "",
            content = doc.getString("content") ?: "",
            timeSent = doc.isoDateTime("createdAt").ifEmpty { LocalDateTime.now().toString() },
            isRead = doc.getBoolean("isRead") ?: false,
        )
    }
}
