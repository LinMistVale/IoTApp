package com.example.iotapp.core

/**
 * 全局 UI 状态（任务书 §6.1）。
 *
 * 约定：
 * - [Loading] / [Error] / [Offline] 都不携带业务数据；
 * - [Offline] 的 [Offline.last] 带**最后一次成功的数据**，断网时界面仍能显示旧值并置灰；
 * - [Error.code] 直接用后端错误体里的 code，如 `DEVICE_OFFLINE` / `AI_TIMEOUT`。
 */
sealed interface UiState<out T> {

    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    data class Error(val code: String, val message: String) : UiState<Nothing>

    data class Offline<T>(val last: T? = null) : UiState<T>
}
