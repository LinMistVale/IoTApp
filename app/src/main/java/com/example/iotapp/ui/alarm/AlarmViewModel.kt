package com.example.iotapp.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotapp.core.UiState
import com.example.iotapp.data.DeviceRepository
import com.example.iotapp.model.AlarmListUi
import com.example.iotapp.model.AlarmUi
import com.example.iotapp.settings.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 告警页的一次性事件，只用于弹 Toast，不参与界面状态。 */
sealed interface AlarmEvent {
    data object AckOk : AlarmEvent

    /** [detail] 为 null 表示后端没给原因（例如离线），由界面兜底文案 */
    data class AckFail(val detail: String?) : AlarmEvent
}

/**
 * 告警页。
 *
 * 数据全部来自 [DeviceRepository.alarms] / [DeviceRepository.ack]，
 * 页面本身不做任何字段解析——告警文字在 Mapper 里已经拼好了（AlarmUi）。
 */
class AlarmViewModel(
    private val repo: DeviceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<AlarmListUi>>(UiState.Loading)
    val state: StateFlow<UiState<AlarmListUi>> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AlarmEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AlarmEvent> = _events.asSharedFlow()

    val deviceId: String get() = settings.deviceId

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repo.alarms(settings.deviceId).collect { _state.value = it }
        }
    }

    /**
     * 标记单条已读（契约 §2.6）。
     *
     * 成功后就地改内存里的那一条，不整页重刷 —— 否则列表会闪一下、
     * 而且刚点掉的那条可能因为后端排序变化跳位置。
     */
    fun ack(item: AlarmUi) {
        if (item.ack) return
        viewModelScope.launch {
            when (val result = repo.ack(item.id)) {
                is UiState.Success -> {
                    markReadLocally(item.id)
                    _events.tryEmit(AlarmEvent.AckOk)
                }

                is UiState.Error -> _events.tryEmit(AlarmEvent.AckFail(result.message))
                is UiState.Offline -> _events.tryEmit(AlarmEvent.AckFail(null))
                UiState.Loading -> Unit
            }
        }
    }

    private fun markReadLocally(id: Long) {
        val current = _state.value
        if (current !is UiState.Success) return

        val newItems = current.data.items.map { if (it.id == id) it.copy(ack = true) else it }
        val newUnread = current.data.unread?.let { maxOf(0, it - 1) }
        _state.value = UiState.Success(AlarmListUi(newItems, newUnread))
    }
}
