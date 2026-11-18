package com.example.iotapp.ui.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotapp.core.UiState
import com.example.iotapp.data.DeviceRepository
import com.example.iotapp.data.mqtt.MqttState
import com.example.iotapp.model.TelemetryUi
import com.example.iotapp.settings.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 首页数据。
 *
 * 三条数据来路：
 * 1. **首屏 / 下拉刷新**：HTTP 请求 `/latest`；
 * 2. **实时**：MQTT `iot/{id}/latest`，实测 2 秒一条，这里同样按 2 秒节流，避免每次重建视图；
 * 3. **兜底**：MQTT 断开时每 5 秒 HTTP 轮询（契约 §3.2 第 4 条），否则断线后首页就彻底不动了。
 */
class HomeViewModel(
    private val repo: DeviceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<TelemetryUi>>(UiState.Loading)
    val state: StateFlow<UiState<TelemetryUi>> = _state.asStateFlow()

    /** 右上角未读角标：HTTP 校准一次，之后由 MQTT 推来的新告警累加 */
    private val _badge = MutableStateFlow(0)
    val badge: StateFlow<Int> = _badge.asStateFlow()

    private val _mqttState = MutableStateFlow<MqttState>(MqttState.Disconnected)
    val mqttState: StateFlow<MqttState> = _mqttState.asStateFlow()

    val deviceId: String get() = settings.deviceId

    private var lastPushAt = 0L
    private var pollJob: Job? = null

    init {
        refresh()
        observeRealtime()
        observeConnection()
        observeNewAlarms()
    }

    /** 下拉刷新 / 断线重试 / 首次加载都走它 */
    fun refresh() {
        viewModelScope.launch {
            repo.telemetry(settings.deviceId).collect { _state.value = it }
            syncBadgeFromHttp()
        }
    }

    /** 供 Fragment 在 onResume 调用：从告警页标记已读回来时角标需要重新校准 */
    fun syncBadge() {
        syncBadgeFromHttp()
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            repo.mqttTelemetry().collect { ui ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastPushAt < PUSH_THROTTLE_MS) return@collect
                lastPushAt = now
                _state.value = UiState.Success(ui)
            }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            repo.mqttState.collect { state ->
                _mqttState.value = state
                if (state is MqttState.Connected) stopPolling() else startPolling()
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                repo.telemetry(settings.deviceId).collect {
                    // 轮询时丢掉 Loading，否则首页每 5 秒闪一次「正在加载」
                    if (it !is UiState.Loading) _state.value = it
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun observeNewAlarms() {
        viewModelScope.launch {
            repo.mqttAlarm().collect { _badge.value += 1 }
        }
    }

    private fun syncBadgeFromHttp() {
        viewModelScope.launch {
            val state = repo.alarms(settings.deviceId).last()
            if (state is UiState.Success) _badge.value = state.data.unreadCount
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private companion object {
        const val PUSH_THROTTLE_MS = 2_000L
        const val POLL_INTERVAL_MS = 5_000L
    }
}
