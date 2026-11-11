package com.example.iotapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotapp.core.UiState
import com.example.iotapp.data.DeviceRepository
import com.example.iotapp.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)

/** 文字对话。回复和「已开灯」这类动作文案都直接显示后端给的文本。 */
class ChatViewModel(
    private val repo: DeviceRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _sending.value) return

        append(ChatMessage(text, fromUser = true))
        viewModelScope.launch {
            _sending.value = true
            when (val result = repo.chat(settings.deviceId, text)) {
                is UiState.Success -> {
                    val ui = result.data
                    val reply = buildString {
                        append(ui.reply)
                        ui.actionText?.let { append("\n").append(it) }
                    }
                    append(ChatMessage(reply, fromUser = false))
                }
                is UiState.Offline -> append(ChatMessage("设备离线，暂时联系不上", fromUser = false))
                is UiState.Error -> append(ChatMessage("发送失败（${result.code}）：${result.message}", fromUser = false))
                UiState.Loading -> Unit
            }
            _sending.value = false
        }
    }

    private fun append(message: ChatMessage) {
        _messages.update { it + message }
    }
}
