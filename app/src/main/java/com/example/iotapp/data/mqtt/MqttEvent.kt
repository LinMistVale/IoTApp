package com.example.iotapp.data.mqtt

import com.example.iotapp.data.remote.dto.AlarmDto
import com.example.iotapp.data.remote.dto.DeviceStatusDto
import com.example.iotapp.data.remote.dto.TelemetryDto

/**
 * MQTT 推上来的事件，已经解析成 DTO。
 *
 * 为什么不直接给 UiModel：UiModel 需要 `/config` 里的单位与精度，
 * 而 config 是仓库层缓存的东西，转换放在 [com.example.iotapp.data.DeviceRepository] 里做，
 * MqttManager 只管「收到什么、是什么类型」。
 */
sealed interface MqttEvent {

    /** `iot/{id}/telemetry` 与 `iot/{id}/latest` 都会归到这里 */
    data class Telemetry(val dto: TelemetryDto) : MqttEvent

    /** `iot/{id}/alarm`，单条告警对象 */
    data class Alarm(val dto: AlarmDto) : MqttEvent

    /** `iot/{id}/status`，含设备 LWT 的 offline 消息 */
    data class Status(val dto: DeviceStatusDto) : MqttEvent
}

/** 连接状态。界面上用它区分「实时刷新中」和「走 HTTP 兜底」。 */
sealed interface MqttState {

    data object Disconnected : MqttState

    data object Connecting : MqttState

    data object Connected : MqttState

    /** 连接失败或中途断开，[reason] 只用于日志与设置页展示 */
    data class Lost(val reason: String) : MqttState
}
