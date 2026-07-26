package com.example.snapget.core.network

import com.example.snapget.core.network.api.UploadApi
import com.example.snapget.core.network.dto.UploadResultDto
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Upload 1 file len server (POST /upload -> Cloudinary) — helper DUY NHAT cho
 * multipart, dung chung cho post/coop/chat/avatar (truoc day 4 repository tu
 * lap lai block createFormData giong het nhau).
 */
suspend fun UploadApi.uploadFile(file: File, mimeType: String): UploadResultDto {
    val part = MultipartBody.Part.createFormData(
        name = "file",
        filename = file.name,
        body = file.asRequestBody(mimeType.toMediaType()),
    )
    return upload(part).unwrap()
}
