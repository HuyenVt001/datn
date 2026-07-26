package com.example.snapget.core.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.snapget.core.model.User
import com.example.snapget.core.model.auth.AuthUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

inline fun <reified T> mapToResponse(
    source: QuerySnapshot,
    crossinline mapper: (DocumentSnapshot) -> T,
): List<T> = source.documents.map { document ->
    mapper(document)
}

/**
 * Avatar hien thi: uu tien URL that (server tra ve); rong -> avatar chu cai dau
 * sinh tu [seed] (ten user) qua DiceBear — CUNG seed = CUNG anh o MOI man hinh,
 * thay cho cac fallback pravatar/Unsplash ngau nhien cu (moi cho mot kieu).
 */
fun avatarOrDefault(avatar: String?, seed: String): String = if (!avatar.isNullOrBlank()) {
    avatar
} else {
    "https://api.dicebear.com/9.x/initials/png?seed=" + android.net.Uri.encode(seed.ifBlank { "?" })
}

/**
 * Read a date field as an ISO-8601 local date-time string (the format models/UI
 * expect). Accepts a Firestore [com.google.firebase.Timestamp] (preferred) or a
 * plain String (backward compatible). Empty string if the field is absent.
 */
@RequiresApi(Build.VERSION_CODES.O)
fun DocumentSnapshot.isoDateTime(field: String = "createdAt"): String {
    getTimestamp(field)?.let {
        return it.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
    }
    return getString(field) ?: ""
}

/**
 * Read a date field without requiring API 26 (used by non-@RequiresApi models).
 * Formats a Timestamp as ISO-like text, or returns the raw String.
 */
fun DocumentSnapshot.displayDate(field: String = "createdAt"): String {
    getTimestamp(field)?.let {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(it.toDate())
    }
    return getString(field) ?: ""
}

/**
 * Epoch millis of a date field for in-memory sorting; 0 if absent/unparseable.
 */
@RequiresApi(Build.VERSION_CODES.O)
fun DocumentSnapshot.createdAtMillis(field: String = "createdAt"): Long {
    getTimestamp(field)?.let { return it.toDate().time }
    val s = getString(field) ?: return 0L
    return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        .getOrDefault(0L)
}

fun mapToUser(source: AuthUser?): User = if (source != null) {
    User(
        id = source.id,
        username = source.name.ifEmpty { "Unknown" },
        email = source.email.ifEmpty { "Unknown" },
        avatar = source.avatar.ifEmpty { "" },
    )
} else {
    User(id = "unknown", username = "Unknown", email = "Unknown", avatar = "")
}

fun trimUsername(username: String, takeFirst: Int = 6): String = if (username.length > 10) {
    username.take(takeFirst) + "..."
} else {
    username
}

fun takeFirstNameOfUser(username: String): String = if (username.isNotEmpty()) {
    username.split(" ").firstOrNull() ?: username
} else {
    "Unknown"
}

// if the caption length is more than 30 characters, truncate it and add "..."
fun trimCaption(caption: String, takeFirst: Int = 30): String = if (caption.length > takeFirst) {
    caption.take(takeFirst) + "..."
} else {
    caption
}
