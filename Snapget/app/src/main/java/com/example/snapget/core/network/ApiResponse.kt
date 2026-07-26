package com.example.snapget.core.network

import com.example.snapget.core.common.AppException

/**
 * Envelope chuan cua server NestJS: { success, statusCode, message, data }.
 * Moi response (ke ca loi) deu co dang nay — xem server/GUIDE.md.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val statusCode: Int,
    val message: String?,
    val data: T?,
)

/** Ket qua phan trang nam trong `data` cua envelope. */
data class PaginatedData<T>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val total: Int,
)

/**
 * Boc `data` ra khoi envelope. That bai (success=false hoac data null) -> nem
 * AppException voi message tieng Viet tu server de UI hien thi truc tiep.
 */
fun <T> ApiResponse<T>.unwrap(): T {
    if (!success) throw AppException.UnexpectedException(message ?: "Something went wrong.")
    return data ?: throw AppException.UnexpectedException(message ?: "Server returned no data.")
}

/** Nhu [unwrap] nhung cho phep data null (endpoint kieu xac nhan, khong can body). */
fun <T> ApiResponse<T>.ensureSuccess() {
    if (!success) throw AppException.UnexpectedException(message ?: "Something went wrong.")
}
