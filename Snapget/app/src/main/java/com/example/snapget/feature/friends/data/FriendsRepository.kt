package com.example.snapget.feature.friends.data

import com.example.snapget.core.network.api.FriendshipApi
import com.example.snapget.core.network.dto.ConnectFriendRequest
import com.example.snapget.core.network.dto.FriendRequestDto
import com.example.snapget.core.network.dto.FriendSummaryDto
import com.example.snapget.core.network.dto.FriendshipDto
import com.example.snapget.core.network.dto.InviteInfoDto
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

    /** Ma moi + link chia se cua chinh minh (hieu luc 30 ngay — het han server tu sinh ma moi). */
    suspend fun getInviteLink(): InviteLinkDto = friendshipApi.getInviteLink().unwrap()

    /**
     * Thong tin nguoi moi (ten + avatar + han link) tu ma moi — hien dialog
     * "Ket ban voi X?" de xac nhan. Ma sai/het han -> AppException message server.
     */
    suspend fun getInviteInfo(inviteCode: String): InviteInfoDto = friendshipApi.getInviteInfo(inviteCode).unwrap()

    /**
     * GUI LOI MOI ket ban bang ma moi (da boc tach qua [parseInviteCode] neu quet QR).
     * Tra ve status PENDING (cho chu link xac nhan) hoac ACCEPTED (2 ben cung moi nhau).
     * Loi nghiep vu (ma sai/het han, du 20 ban, da la ban, da moi truoc do...) nem
     * AppException voi message tieng Viet cua server — UI hien truc tiep.
     */
    suspend fun connect(inviteCode: String): FriendshipDto = friendshipApi.connect(ConnectFriendRequest(inviteCode)).unwrap()

    /** Loi moi ket ban dang cho MINH (chu link) xac nhan. */
    suspend fun listRequests(): List<FriendRequestDto> = friendshipApi.listRequests().unwrap()

    /** Chap nhan loi moi cua [requesterUid] -> thanh ban (server check lai gioi han 20). */
    suspend fun acceptRequest(requesterUid: String): FriendshipDto = friendshipApi.acceptRequest(requesterUid).unwrap()

    /** Tu choi loi moi cua [requesterUid] (xoa im lang). */
    suspend fun declineRequest(requesterUid: String) {
        friendshipApi.declineRequest(requesterUid).ensureSuccess()
    }

    /** Xoa ban — friend streak chung mat vinh vien. */
    suspend fun removeFriend(friendUid: String) {
        friendshipApi.removeFriend(friendUid).ensureSuccess()
    }

    companion object {
        /**
         * Rut inviteCode tu noi dung QR / deep link: chap nhan link App Links
         * (https://snapget-d8693.web.app/invite/{code}), scheme du phong
         * (snapget://invite/{code}) lan ma tho.
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
