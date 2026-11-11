package com.example.iotapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotapp.core.UiState
import com.example.iotapp.data.DeviceRepository
import com.example.iotapp.model.TelemetryUi
import com.example.iotapp.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 首页：只做「拿数据 + 暴露状态」，不碰任何 View。 */
class HomeViewModel(
    private val repo: DeviceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<TelemetryUi>>(UiState.Loading)
    val state: StateFlow<UiState<TelemetryUi>> = _state.asStateFlow()

    val deviceId: String get() = settings.deviceId

    init {
        refresh()
    }

    /** 下拉刷新 / 断线重试都走它 */
    fun refresh() {
        viewModelScope.launch {
            repo.telemetry(settings.deviceId).collect { _state.value = it }
        }
    }
}
