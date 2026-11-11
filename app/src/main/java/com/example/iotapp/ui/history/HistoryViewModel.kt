package com.example.iotapp.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotapp.core.UiState
import com.example.iotapp.data.DeviceRepository
import com.example.iotapp.model.HistoryUi
import com.example.iotapp.settings.SettingsStore
import com.example.iotapp.util.TimeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 历史：指标 + 时间范围由 UI 选择，ViewModel 只负责取数。 */
class HistoryViewModel(
    private val repo: DeviceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HistoryUi>>(UiState.Loading)
    val state: StateFlow<UiState<HistoryUi>> = _state.asStateFlow()

    var metric: String = METRIC_TEMP
        private set
    var hours: Int = RANGE_24H
        private set

    init {
        load()
    }

    fun select(metric: String = this.metric, hours: Int = this.hours) {
        this.metric = metric
        this.hours = hours
        load()
    }

    fun load() {
        viewModelScope.launch {
            val to = TimeUtil.nowSec()
            val from = to - hours * 3600L
            repo.history(settings.deviceId, metric, from, to, bucket = "auto")
                .collect { _state.value = it }
        }
    }

    companion object {
        const val METRIC_TEMP = "temp"
        const val METRIC_HUMI = "humi"
        const val METRIC_LUX = "lux"
        const val RANGE_1H = 1
        const val RANGE_24H = 24
    }
}
