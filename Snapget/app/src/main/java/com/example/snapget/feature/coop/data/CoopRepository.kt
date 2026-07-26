package com.example.snapget.feature.coop.data

import com.example.snapget.core.network.api.CoopApi
import com.example.snapget.core.network.api.UploadApi
import com.example.snapget.core.network.dto.AcceptCoopInviteRequest
import com.example.snapget.core.network.dto.CoopInviteDto
import com.example.snapget.core.network.dto.CreateCoopInviteRequest
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.network.ensureSuccess
import com.example.snapget.core.network.unwrap
import com.example.snapget.core.network.uploadFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature coop (chup chung) — goi API /moments/coop.
 * Luong: upload nua anh cua minh -> gui loi moi / chap nhan (server ghep 2 anh).
 */
@Singleton
class CoopRepository @Inject constructor(
    private val coopApi: CoopApi,
    private val uploadApi: UploadApi,
) {

    /** Upload nua anh cua minh len server (Cloudinary), tra ve URL. */
    suspend fun uploadHalf(file: File): String = uploadApi.uploadFile(file, "image/jpeg").url

    suspend fun sendInvite(friendUid: String, mediaUrl: String): CoopInviteDto = coopApi.createInvite(CreateCoopInviteRequest(friendUid, mediaUrl)).unwrap()

    suspend fun listPending(): List<CoopInviteDto> = coopApi.listPending().unwrap()

    suspend fun accept(inviteId: String, mediaUrl: String, caption: String? = null): MomentDto = coopApi.accept(inviteId, AcceptCoopInviteRequest(mediaUrl, caption)).unwrap()

    suspend fun decline(inviteId: String) {
        coopApi.decline(inviteId).ensureSuccess()
    }
}
