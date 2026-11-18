package com.example.iotapp.notify

import android.content.Context
import android.util.Log
import com.example.iotapp.data.AppContainer
import com.example.iotapp.data.mqtt.MqttEvent
import com.example.iotapp.data.mqtt.MqttManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * 新告警 → 弹通知。
 *
 * 为什么单独一个类而不是塞进 ViewModel：
 * 通知要在**任何页面**都能弹（用户可能停在首页或对话页），而 ViewModel 只在页面存活时存在。
 * 所以挂在 Application 生命周期上，跟随进程。
 *
 * 去重策略：只在 `id > lastNotifiedAlarmId` 时弹。
 * - `iot/{id}/alarm` 是 retained 的，每次重连 broker 都重发最后一条；
 * - 后端若重放历史，id 也不会更大；
 * 两者都能被这一条挡住。
 */
object AlarmWatcher {

    private const val TAG = "AlarmWatcher"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true

        val app = context.applicationContext
        AlarmNotifier.ensureChannel(app)

        scope.launch {
            MqttManager.events
                .filterIsInstance<MqttEvent.Alarm>()
                .collect { event ->
                    val id = event.dto.id
                    val settings = AppContainer.settings

                    if (id <= settings.lastNotifiedAlarmId) return@collect

                    // 先记下再弹，避免 notify 抛异常时反复重试同一条
                    settings.lastNotifiedAlarmId = id

                    val ui = AppContainer.repository().alarmUi(event.dto)
                    AlarmNotifier.notifyAlarm(app, ui, id)
                    Log.i(TAG, "notified alarm #$id ${ui.metricLabel} ${ui.valueText}")
                }
        }
    }
}
