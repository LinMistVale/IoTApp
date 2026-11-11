package com.example.iotapp.data.remote

import com.example.iotapp.core.ApiException
import com.example.iotapp.data.remote.dto.ApiErrorDto
import com.google.gson.Gson
import retrofit2.HttpException

/**
 * HTTP 层错误 → 业务异常。
 *
 * 后端所有非 2xx 都返回 `{"error":{"code","message","request_id"}}`，
 * 这里解析出来，解析失败就降级成 `HTTP_404` 这种，保证一定拿到一个 code。
 *
 * 放在 data 层，是因为它既依赖 Retrofit 又依赖 DTO；core 层保持干净。
 */
fun HttpException.toApiException(gson: Gson): ApiException {
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val body = raw?.takeIf { it.isNotBlank() }
        ?.let { runCatching { gson.fromJson(it, ApiErrorDto::class.java) }.getOrNull() }
    return ApiException(
        code = body?.error?.code ?: "HTTP_${code()}",
        message = body?.error?.message ?: "服务返回 HTTP ${code()}",
        requestId = body?.error?.requestId,
        httpStatus = code(),
    )
}
