package com.example.iotapp.data

import android.content.Context
import com.example.iotapp.data.remote.ApiClient
import com.example.iotapp.settings.SettingsStore

/**
 * 极简依赖容器（不引入 Hilt，16 天项目没必要）。
 *
 * 职责：
 * - 持有 [SettingsStore]；
 * - 按当前设置的 IP 构造 [DeviceRepository]，并在设置变更后 [resetRepository]。
 */
object AppContainer {

    private lateinit var settingsStore: SettingsStore

    @Volatile
    private var repository: DeviceRepository? = null

    /** 在 Application.onCreate 里调用一次 */
    fun init(context: Context) {
        settingsStore = SettingsStore(context.applicationContext)
    }

    val settings: SettingsStore get() = settingsStore

    fun repository(): DeviceRepository = repository ?: synchronized(this) {
        repository ?: DeviceRepository(
            api = ApiClient.api(settingsStore.baseUrl),
            chatApi = ApiClient.chatApi(settingsStore.baseUrl),
        ).also { repository = it }
    }

    /** 改完 IP 必须调用，否则还在用旧地址的 Retrofit 实例 */
    fun resetRepository() {
        synchronized(this) { repository = null }
    }
}
