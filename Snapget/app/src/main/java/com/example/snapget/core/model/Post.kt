package com.example.snapget.core.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.O)
data class Post(
    val id: String = UUID.randomUUID().toString(),
    val user: User,
    val postType: PostType,
    val caption: String?,
    // Rong = khong co anh (moment that LUON co mediaUrl tu server) —
    // truoc day default picsum ngau nhien -> o anh gia tren feed
    val thumbnailUrl: String = "",
    // Khung anh phan thuong ap len moment (null = khong khung)
    val frameId: String? = null,
    val isArchived: Boolean = false,
    val createdAt: String = LocalDateTime.now().toString(),
    // "PUBLIC", "FRIEND", "PRIVATE"
    val visibility: String = Visibility.FRIEND.toString(),
    // Quick boolean check
    val friendsOnly: Boolean = false,
    // Tags for categorization
    val tags: List<String> = emptyList(),
    // Track modifications
    val updatedAt: String? = null,
)

enum class PostType {
    IMAGE,
    VIDEO,
    TEXT,
}

enum class Visibility {
    PUBLIC,
    FRIEND,
    PRIVATE,
}
