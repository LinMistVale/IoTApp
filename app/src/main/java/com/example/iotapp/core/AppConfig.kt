package com.example.iotapp.core

/**
 * 全局默认配置（唯一改动入口）。
 *
 * 为什么集中在一个文件：
 * 这些值会随部署环境变化（换教室、换热点、后端换机器），以前散落在 ApiClient、
 * SettingsStore 里，改一次要翻三个文件。现在只改这里，其余代码全部引用它。
 *
 * 运行时仍以设置页（[com.example.iotapp.settings.SettingsStore]）里的值为准，
 * 这里的只是「首次安装、用户没改过」时的默认值。
 */
object AppConfig {

    /** 后端 HTTP 地址，必须带协议头和结尾斜杠 */
    const val BASE_URL = "http://10.226.236.84:8000/"

    /** MQTT Broker（D5 实时推送用，当前仅作配置占位） */
    const val MQTT_URL = "tcp://10.226.236.84:1883"

    /** 默认设备 ID */
    const val DEVICE_ID = "s3-01"

    // ---------- 接口路径（相对 BASE_URL）----------

    /** 健康检查：GET {BASE_URL}api/v1/healthz */
    const val PATH_HEALTHZ = "api/v1/healthz"

    // ---------- 超时（秒）----------

    /** 普通接口：latest / history / alarms / config / command */
    const val TIMEOUT_NORMAL_S = 5L

    /** 对话接口：AI 链路慢，5 秒必超时，单独放宽 */
    const val TIMEOUT_CHAT_S = 15L

    // ---------- MQTT 约定 ----------

    /** 订阅 + 发布统一用 QoS1，保证至少一次送达 */
    const val MQTT_QOS = 1

    /** 是否保留会话（App 进程被杀后重连能收到离线消息） */
    const val MQTT_CLEAN_SESSION = true

    /** 设备上报主题：iot/{deviceId}/telemetry */
    fun topicTelemetry(deviceId: String) = "iot/$deviceId/telemetry"

    /** 最新值主题：iot/{deviceId}/latest（后端 retained） */
    fun topicLatest(deviceId: String) = "iot/$deviceId/latest"

    /** 命令下行主题：iot/{deviceId}/cmd */
    fun topicCmd(deviceId: String) = "iot/$deviceId/cmd"

    /** 命令回执主题：iot/{deviceId}/cmd/ack */
    fun topicCmdAck(deviceId: String) = "iot/$deviceId/cmd/ack"

    /** 告警主题：iot/{deviceId}/alarm */
    fun topicAlarm(deviceId: String) = "iot/$deviceId/alarm"
}
