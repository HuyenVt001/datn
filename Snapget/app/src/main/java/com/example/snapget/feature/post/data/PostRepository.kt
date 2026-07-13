package com.example.snapget.feature.post.data

import com.example.snapget.core.network.PaginatedData
import com.example.snapget.core.network.api.FrameApi
import com.example.snapget.core.network.api.MomentApi
import com.example.snapget.core.network.api.UploadApi
import com.example.snapget.core.network.dto.CreateMomentRequest
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.network.dto.ReactRequest
import com.example.snapget.core.network.dto.UploadResultDto
import com.example.snapget.core.network.unwrap
import com.example.snapget.core.network.uploadFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature post — goi API server (KHONG cham Firestore).
 * Luong dang bai: uploadMedia() lay URL -> createMoment().
 */
@Singleton
class PostRepository @Inject constructor(
    private val uploadApi: UploadApi,
    private val momentApi: MomentApi,
    private val frameApi: FrameApi,
) {

    /** Catalog khung + trang thai mo khoa — dung cho picker khung va overlay khung tren feed. */
    suspend fun getFrames(): List<FrameDto> = frameApi.list().unwrap()

    /**
     * Upload anh/video len server (server day len Cloudinary).
     * Video >5s bi server tu choi — loi tra ve bang message tieng Viet.
     */
    suspend fun uploadMedia(file: File, isVideo: Boolean = false): UploadResultDto = uploadApi.uploadFile(file, if (isVideo) "video/mp4" else "image/jpeg")

    /** Tao moment. Server tu tang personal streak + gui FCM cho ban be. */
    suspend fun createMoment(
        mediaUrl: String,
        isVideo: Boolean = false,
        caption: String? = null,
        frameId: String? = null,
    ): MomentDto = momentApi.create(
        CreateMomentRequest(
            contentType = if (isVideo) "VIDEO" else "PHOTO",
            mediaUrl = mediaUrl,
            frameId = frameId,
            caption = caption,
        ),
    ).unwrap()

    /** Feed cua minh + ban be (moi nhat truoc). */
    suspend fun getFeed(page: Int = 1, limit: Int = 20): PaginatedData<MomentDto> = momentApi.getFeed(page, limit).unwrap()

    /** Moment cua chinh minh — tab "You" tren feed. */
    suspend fun getMyMoments(): List<MomentDto> = momentApi.getMine().unwrap().items

    /** Moment cua 1 nguoi ban — tab ban be tren feed (server 403 neu khong phai ban). */
    suspend fun getUserMoments(uid: String): List<MomentDto> = momentApi.getOfUser(uid).unwrap().items

    /** He thong tu goi khi user luot qua moment tren feed. */
    suspend fun markSeen(momentId: String) {
        momentApi.markSeen(momentId)
    }

    /** Tha emoji — server cap nhat friend streak voi chu bai. */
    suspend fun react(momentId: String, emojiType: String) = momentApi.react(momentId, ReactRequest(emojiType)).unwrap()
}
