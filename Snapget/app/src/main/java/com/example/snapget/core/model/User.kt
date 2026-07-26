package com.example.snapget.core.model

import com.example.snapget.core.model.auth.AuthUser
import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val email: String = "",
    // Rong = chua co avatar that -> noi hien thi dung avatarOrDefault() (initials),
    // KHONG default anh stock nua (truoc la Unsplash -> avatar gia, lech giua man)
    val avatar: String = "",
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
