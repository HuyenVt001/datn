package com.example.snapget.feature.friends.data

import android.content.Context
import androidx.core.content.edit

/**
 * Luu ma moi ket ban khi user bam deep link luc CHUA dang nhap: giu lai ma
 * (SharedPreferences) de sau khi dang nhap xong tu hien dialog xac nhan —
 * khong bat user quay lai bam link lan nua.
 */
object PendingInviteStore {

    private const val PREFS_NAME = "snapget_pending_invite"
    private const val KEY_CODE = "invite_code"

    /** Luu ma moi cho xu ly sau khi dang nhap. */
    fun save(context: Context, inviteCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CODE, inviteCode) }
    }

    /** Lay ma dang cho va XOA luon (moi ma chi xu ly 1 lan). */
    fun consume(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null)
        if (code != null) {
            prefs.edit { remove(KEY_CODE) }
        }
        return code
    }
}
