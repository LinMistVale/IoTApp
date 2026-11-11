package com.example.iotapp.core

import java.io.IOException

/**
 * 错误码常量：直接对应契约 §1.1 的 error.code。
 * 前端只按 code 分支，不要按 HTTP 状态码写业务逻辑。
 */
const val CODE_OFFLINE = "DEVICE_OFFLINE"
const val CODE_INVALID_PARAM = "INVALID_PARAM"
const val CODE_COMMAND_BUSY = "COMMAND_BUSY"
const val CODE_AI_TIMEOUT = "AI_TIMEOUT"
const val CODE_NETWORK = "NETWORK"
const val CODE_UNKNOWN = "UNKNOWN"

/**
 * 业务异常：把后端的统一错误体收敛成一个可以直接判断的类型。
 *
 * 放在 core 包里，因此**不 import 任何 data 层的东西**；
 * 把 `HttpException` 转成它的活儿在 `data/remote/HttpErrorMapper.kt` 里做。
 */
class ApiException(
    val code: String,
    override val message: String,
    val requestId: String? = null,
    val httpStatus: Int = 0,
) : IOException(message)

/**
 * 统一异常 → UiState。
 *
 * [last] 是上一次成功的数据：设备离线时带进 `UiState.Offline`，
 * 这样首页断网不会变成一片空白，而是显示旧值 + 置灰。
 *
 * 注意：调用前应先把 `HttpException` 转成 [ApiException]（见 HttpErrorMapper），
 * 否则会落到最后一个分支，拿不到后端的 error.code。
 */
fun <T> Throwable.toUiState(last: T? = null): UiState<T> = when {
    this is ApiException && code == CODE_OFFLINE -> UiState.Offline(last)
    this is ApiException -> UiState.Error(code, message)
    this is IOException -> UiState.Error(CODE_NETWORK, "网络连接失败，请确认手机和电脑在同一 WiFi")
    else -> UiState.Error(CODE_UNKNOWN, message ?: "未知错误")
}
