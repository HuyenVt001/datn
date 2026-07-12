package com.example.snapget.feature.friends.data

import com.example.snapget.core.network.api.FriendshipApi
import com.example.snapget.core.network.dto.ConnectFriendRequest
import com.example.snapget.core.network.dto.FriendSummaryDto
import com.example.snapget.core.network.dto.FriendshipDto
import com.example.snapget.core.network.dto.InviteLinkDto
import com.example.snapget.core.network.ensureSuccess
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature friends — goi API /friendships (KHONG cham Firestore).
 * Ket ban CHI qua ma moi: minh dua QR (chua inviteCode) cho ban quet, hoac nguoc lai.
 */
@Singleton
class FriendsRepository @Inject constructor(
    private val friendshipApi: FriendshipApi,
) {

    /** Danh sach ban be kem friend streak (server gioi han 20). */
    suspend fun listFriends(): List<FriendSummaryDto> = friendshipApi.listFriends().unwrap()

    /** Ma moi + link chia se cua chinh minh. */
    suspend fun getInviteLink(): InviteLinkDto = friendshipApi.getInviteLink().unwrap()

    /**
     * Ket ban bang ma moi (da boc tach qua [parseInviteCode] neu quet tu QR).
     * Loi nghiep vu (ma sai, du 20 ban, da la ban...) nem AppException voi
     * message tieng Viet cua server — UI hien truc tiep.
     */
    suspend fun connect(inviteCode: String): FriendshipDto = friendshipApi.connect(ConnectFriendRequest(inviteCode)).unwrap()

    /** Xoa ban — friend streak chung mat vinh vien. */
    suspend fun removeFriend(friendUid: String) {
        friendshipApi.removeFriend(friendUid).ensureSuccess()
    }

    companion object {
        /**
         * Rut inviteCode tu noi dung QR: chap nhan ca link day du
         * (https://snapget.app/invite/{code}) lan ma tho.
         */
        fun parseInviteCode(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.contains("/invite/")) {
                trimmed.substringAfterLast("/invite/").trimEnd('/')
            } else {
                trimmed
            }
        }
    }
}
