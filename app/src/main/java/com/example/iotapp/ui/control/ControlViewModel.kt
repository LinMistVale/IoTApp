package com.example.iotapp.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotapp.core.UiState
import com.example.iotapp.data.DeviceRepository
import com.example.iotapp.model.CommandResultUi
import com.example.iotapp.model.DeviceConfigUi
import com.example.iotapp.settings.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 控制页：控件全部由 /config 渲染，ViewModel 只负责取配置 + 下发命令。
 * 每个命令方法都是类型化的，UI 不需要知道 CommandDto 长什么样。
 */
class ControlViewModel(
    private val repo: DeviceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _config = MutableStateFlow<UiState<DeviceConfigUi>>(UiState.Loading)
    val config: StateFlow<UiState<DeviceConfigUi>> = _config.asStateFlow()

    /** 一次性的提示文字（Toast 用） */
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        loadConfig()
    }

    fun loadConfig(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _config.value = UiState.Loading
            _config.value = repo.config(settings.deviceId, forceRefresh)
        }
    }

    fun setLed(on: Boolean) = send("开关灯") { repo.setLed(settings.deviceId, on) }

    fun setThreshold(metric: String, value: Float) =
        send("告警阈值") { repo.setThreshold(settings.deviceId, metric, value.toDouble()) }

    fun setInterval(seconds: Int) = send("上报间隔") { repo.setInterval(settings.deviceId, seconds) }

    fun setVolume(volume: Int) = send("音量") { repo.setVolume(settings.deviceId, volume) }

    fun setMode(mode: String) = send("工作模式") { repo.setMode(settings.deviceId, mode) }

    fun speak(text: String) = send("让它说话") { repo.speak(settings.deviceId, text) }

    private fun send(label: String, block: suspend () -> UiState<CommandResultUi>) {
        viewModelScope.launch {
            val text = when (val result = block()) {
                is UiState.Success -> "已下发：$label"
                is UiState.Offline -> "设备离线，命令没发出去"
                is UiState.Error -> "失败（${result.code}）：${result.message}"
                UiState.Loading -> return@launch
            }
            _message.emit(text)
        }
    }
}
