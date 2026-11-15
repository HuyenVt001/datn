package com.example.snapget.core.fcm

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus su kien push CHUP CHUNG (FCM data.type) cho cac CoopViewModel DANG SONG
 * (2026-11-16): app dang mo ma nhan loi moi / loi moi bi huy -> the loi moi o
 * Camera + Feed cap nhat NGAY (khong doi lan poll 10s); man chup coop nhan
 * ACCEPTED/DONE -> refresh trang thai ngay (khong doi lan poll 2.5s).
 */
object CoopPushEvents {
    /** data.type server gui (CoopService). */
    const val TYPE_COOP_INVITE = "COOP_INVITE"
    const val TYPE_COOP_ACCEPTED = "COOP_ACCEPTED"
    const val TYPE_COOP_DONE = "COOP_DONE"

    /** Data-only: nguoi moi huy loi moi con PENDING — app khong hien thong bao. */
    const val TYPE_COOP_CANCELLED = "COOP_CANCELLED"

    fun isCoopType(type: String?): Boolean = type == TYPE_COOP_INVITE ||
        type == TYPE_COOP_ACCEPTED ||
        type == TYPE_COOP_DONE ||
        type == TYPE_COOP_CANCELLED

    /** 1 su kien = loai + inviteId (co the rong neu push thieu). */
    data class Event(val type: String, val inviteId: String?)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** Goi tu FirebaseMessagingService (thread bat ky) — tryEmit khong treo. */
    fun emit(type: String, inviteId: String?) {
        _events.tryEmit(Event(type, inviteId))
    }
}
