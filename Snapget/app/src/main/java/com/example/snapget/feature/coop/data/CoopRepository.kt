package com.example.snapget.feature.coop.data

import com.example.snapget.core.network.api.CoopApi
import com.example.snapget.core.network.api.UploadApi
import com.example.snapget.core.network.dto.CoopInviteDto
import com.example.snapget.core.network.dto.CreateCoopInviteRequest
import com.example.snapget.core.network.dto.SubmitCoopMediaRequest
import com.example.snapget.core.network.ensureSuccess
import com.example.snapget.core.network.unwrap
import com.example.snapget.core.network.uploadFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature coop (chup chung) — goi API /moments/coop.
 * Redesign 2026-08-02: moi khong kem anh (TTL 5 phut) -> accept -> ca 2 vao man
 * chup, moi nguoi upload + nop nua anh cua minh -> server ghep -> mergedMediaUrl.
 */
@Singleton
class CoopRepository @Inject constructor(
    private val coopApi: CoopApi,
    private val uploadApi: UploadApi,
) {

    /** Upload nua anh cua minh len server (Cloudinary), tra ve URL. */
    suspend fun uploadHalf(file: File): String = uploadApi.uploadFile(file, "image/jpeg").url

    /** Gui loi moi chup chung (khong kem anh). */
    suspend fun sendInvite(friendUid: String): CoopInviteDto = coopApi.createInvite(CreateCoopInviteRequest(friendUid)).unwrap()

    suspend fun listPending(): List<CoopInviteDto> = coopApi.listPending().unwrap()

    /** Chi tiet loi moi — man chup coop poll trang thai. */
    suspend fun getInvite(inviteId: String): CoopInviteDto = coopApi.getInvite(inviteId).unwrap()

    /** Chap nhan loi moi -> ACCEPTED. */
    suspend fun accept(inviteId: String): CoopInviteDto = coopApi.accept(inviteId).unwrap()

    suspend fun decline(inviteId: String) {
        coopApi.decline(inviteId).ensureSuccess()
    }

    /** Nop nua anh cua minh — du 2 nua thi response da co mergedMediaUrl. */
    suspend fun submitMedia(inviteId: String, mediaUrl: String): CoopInviteDto = coopApi.submitMedia(inviteId, SubmitCoopMediaRequest(mediaUrl)).unwrap()
}
