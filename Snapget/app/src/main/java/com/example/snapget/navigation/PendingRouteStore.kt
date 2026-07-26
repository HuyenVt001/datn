package com.example.snapget.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Giu route cho xu ly khi app duoc mo tu widget (extra EXTRA_WIDGET_ROUTE).
 * In-memory la du: intent den lai moi lan launch (mirror PendingInviteStore
 * nhung khong can persist).
 *
 * La StateFlow (fix 2026-07-26): tap widget khi app DANG chay di vao onNewIntent
 * nhung authState khong doi -> LaunchedEffect(authState) cu khong chay lai,
 * route ket trong store (khong dieu huong + sau nay sign out/login bi nhay man
 * bat ngo). Navigation observe flow nay nen route den luc nao cung xu ly duoc.
 */
object PendingRouteStore {
    private val _pending = MutableStateFlow<String?>(null)

    /** Route dang cho dieu huong (null = khong co). Navigation collect flow nay. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun set(route: String) {
        _pending.value = route
    }

    /** Lay route va xoa (moi route chi dieu huong 1 lan). */
    fun consume(): String? = _pending.getAndUpdate { null }
}
