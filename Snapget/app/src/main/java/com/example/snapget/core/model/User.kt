package com.example.snapget.core.model

import com.example.snapget.core.data.SampleData
import com.example.snapget.core.model.auth.AuthUser
import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val email: String = "Unknown Email",
    val avatar: String = SampleData.IMAGE_NOT_AVAILABLE,
) {
    companion object {
        fun mapToUser(source: AuthUser): User = User(
            id = source.id,
            username = source.name,
            email = source.email,
            avatar = source.avatar,
        )
    }
}
