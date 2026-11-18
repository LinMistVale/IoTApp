package com.example.iotapp

import android.app.Application
import com.example.iotapp.data.AppContainer
import com.example.iotapp.data.mqtt.MqttManager
import com.example.iotapp.notify.AlarmWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口。
 *
 * 除了初始化依赖容器，还要在这里把 MQTT 连上 —— 实时数据和告警通知都要求
 * 「进程活着就保持连接」，不能等某个 Fragment 出现才开始连。
 */
class IotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)

        val settings = AppContainer.settings

        // 预热 /config：MQTT 事件转成文字要用它的单位与精度（湿度 %、光照 lx、几位小数）。
        // 失败也无所谓，Mapper 里有默认标签兜底。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AppContainer.repository().config(settings.deviceId)
        }

        MqttManager.connect(
            rawUrl = settings.mqttUrl,
            deviceId = settings.deviceId,
            clientId = settings.mqttClientId,
        )

        AlarmWatcher.start(this)
    }
}
