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
    val thumbnailUrl: String = "https://picsum.photos/400/300?random=${(0..1000).random()}",
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
