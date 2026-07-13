package com.example.snapget.core.network.dto

/** Khung anh trong catalog (GET /frames — kem trang thai da mo khoa cua minh). */
data class FrameDto(
    val frameId: String,
    val frameName: String,
    /** URL anh overlay PNG (nen trong suot) tren Cloudinary. */
    val imageUrl: String? = null,
    /** Moc streak (3/7/14/30) neu la khung thuong moc; null = khung thuong quest. */
    val milestone: Int? = null,
    val isUnlocked: Boolean = false,
)
