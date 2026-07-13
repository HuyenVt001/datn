package com.example.snapget.feature.post

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.PostType
import com.example.snapget.core.model.User
import com.example.snapget.core.network.dto.MomentDto

/**
 * Map MomentDto (tu API server) -> Post (model hien thi cua UI cu).
 * Thong tin nguoi dang resolve tu currentUser/danh sach ban be da tai san
 * (server chi tra userId, khong nhung profile vao moment).
 */
@RequiresApi(Build.VERSION_CODES.O)
fun MomentDto.toPost(currentUser: User?, friends: List<User>): Post {
    val author = when (userId) {
        currentUser?.id -> currentUser
        else -> friends.find { it.id == userId }
    } ?: User(id = userId, username = "Snapget user", email = "", avatar = "")

    return Post(
        id = momentId,
        user = author,
        postType = if (contentType == "VIDEO") PostType.VIDEO else PostType.IMAGE,
        caption = caption,
        thumbnailUrl = mediaUrl,
        frameId = frameId,
        createdAt = postTime,
    )
}
