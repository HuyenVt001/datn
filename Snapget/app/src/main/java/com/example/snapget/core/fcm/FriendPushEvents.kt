package com.example.snapget.core.fcm

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus su kien push ket ban (FCM data.type) cho cac FriendsViewModel DANG SONG:
 * app dang mo ma nhan loi moi / duoc chap nhan -> tu tai lai list loi moi + ban be
 * (banner Feed, badge pill Camera cap nhat ngay, khong can mo lai man).
 */
object FriendPushEvents {
    /** data.type server gui (FriendshipsService.notifyUser). */
    const val TYPE_FRIEND_REQUEST = "FRIEND_REQUEST"
    const val TYPE_FRIEND_ACCEPTED = "FRIEND_ACCEPTED"

    fun isFriendType(type: String?): Boolean = type == TYPE_FRIEND_REQUEST || type == TYPE_FRIEND_ACCEPTED

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Goi tu FirebaseMessagingService (thread bat ky) — tryEmit khong treo. */
    fun emit(type: String) {
        _events.tryEmit(type)
    }
}
