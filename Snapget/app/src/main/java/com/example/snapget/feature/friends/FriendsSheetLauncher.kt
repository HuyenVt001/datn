package com.example.snapget.feature.friends

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Yeu cau "mo sheet ban be" tu ngoai Compose (bam thong bao FRIEND_REQUEST /
 * FRIEND_ACCEPTED). Navigation dua user ve Camera, CameraScreen collect co nay
 * roi mo sheet. In-memory la du (intent thong bao den lai moi lan launch).
 */
object FriendsSheetLauncher {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    /** true neu co yeu cau dang cho (va xoa co — moi yeu cau mo sheet 1 lan). */
    fun consume(): Boolean = _pending.getAndUpdate { false }
}
