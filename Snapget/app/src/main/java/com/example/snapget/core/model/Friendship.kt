package com.example.snapget.core.model

import java.util.UUID

data class Friendship(
    val id: String = UUID.randomUUID().toString(),
    val user1Id: String = UUID.randomUUID().toString(),
    val user2Id: String = UUID.randomUUID().toString(),
    val status: FriendshipStatus,
    val requesterId: String = "",
    val addresseeId: String = "",
    // For easier querying
    val combinedUserIds: List<String> = listOf(user1Id, user2Id),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

enum class FriendshipStatus {
    PENDING,
    ACCEPTED,
    BLOCKED,
    DECLINED,
}
