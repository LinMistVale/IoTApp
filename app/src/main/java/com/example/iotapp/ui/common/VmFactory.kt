package com.example.iotapp.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.iotapp.data.AppContainer
import com.example.iotapp.ui.chat.ChatViewModel
import com.example.iotapp.ui.control.ControlViewModel
import com.example.iotapp.ui.history.HistoryViewModel
import com.example.iotapp.ui.home.HomeViewModel

/**
 * 手写 ViewModel 工厂（不引 Hilt，省一天配置）。
 * 新加 ViewModel 时在这里补一行。
 */
class VmFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = AppContainer.repository()
        val settings = AppContainer.settings
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repo, settings)
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(repo, settings)
            modelClass.isAssignableFrom(ControlViewModel::class.java) -> ControlViewModel(repo, settings)
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(repo, settings)
            else -> throw IllegalArgumentException("未知的 ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}
