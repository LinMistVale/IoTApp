package com.example.iotapp.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * 网络层 DTO（任务书 §5.1）——**只允许出现在 data 包里**。
 *
 * 字段名全部用 @SerializedName 对齐契约里的 snake_case；
 * 所有可能为 null 的数值都声明成可空，交给 Mapper 转成 `--`。
 */

// ---------- 实时数据 / 设备状态 ----------

data class TelemetryDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("ts") val ts: Long,
    @SerializedName("temp") val temp: Double? = null,
    @SerializedName("humi") val humi: Double? = null,
    @SerializedName("lux") val lux: Int? = null,
    /** 预留字段，当前恒为 null，App 不展示 */
    @SerializedName("db") val db: Int? = null,
    @SerializedName("online") val online: Boolean = true,
)

data class DeviceStatusDto(
    @SerializedName("online") val online: Boolean,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("uptime") val uptime: Long? = null,
    @SerializedName("fw") val fw: String? = null,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("last_seen") val lastSeen: Long? = null,
)

// ---------- 能力发现 /config ----------

data class MetricDto(
    @SerializedName("key") val key: String,
    @SerializedName("label") val label: String,
    @SerializedName("unit") val unit: String,
    @SerializedName("precision") val precision: Int,
)

data class ThresholdDto(
    @SerializedName("min") val min: Double,
    @SerializedName("max") val max: Double,
    @SerializedName("step") val step: Double,
    // `default` 是 Kotlin 关键字，只能用别名承接
    @SerializedName("default") val defaultValue: Double,
)

data class IntervalDto(
    @SerializedName("options") val options: List<Int> = emptyList(),
    @SerializedName("default") val defaultValue: Int,
)

data class VolumeDto(
    @SerializedName("min") val min: Double,
    @SerializedName("max") val max: Double,
    @SerializedName("step") val step: Double,
    @SerializedName("default") val defaultValue: Double,
)

data class OptionDto(
    @SerializedName("value") val value: String,
    @SerializedName("label") val label: String,
)

data class CommandSpecDto(
    @SerializedName("cmd") val cmd: String,
    @SerializedName("label") val label: String,
    @SerializedName("type") val type: String,
)

data class DeviceConfigDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("version") val version: Int,
    @SerializedName("metrics") val metrics: List<MetricDto> = emptyList(),
    @SerializedName("thresholds") val thresholds: Map<String, ThresholdDto> = emptyMap(),
    @SerializedName("interval") val interval: IntervalDto,
    @SerializedName("volume") val volume: VolumeDto,
    @SerializedName("modes") val modes: List<OptionDto> = emptyList(),
    @SerializedName("commands") val commands: List<CommandSpecDto> = emptyList(),
    /**
     * 只有契约 v1.2 草稿才有该字段。当前 v1.1 后端不返回，保持可空：
     * 有就用，没有就回退到默认 10 秒（见 Mappers.STALE_SECONDS_DEFAULT）。
     */
    @SerializedName("stale_seconds") val staleSeconds: Int? = null,
)

// ---------- 历史 ----------

data class HistoryPointDto(
    @SerializedName("ts") val ts: Long,
    @SerializedName("value") val value: Double,
    @SerializedName("min") val min: Double? = null,
    @SerializedName("max") val max: Double? = null,
)

data class HistoryDto(
    @SerializedName("metric") val metric: String,
    @SerializedName("bucket") val bucket: String = "auto",
    @SerializedName("downsampled") val downsampled: Boolean = false,
    @SerializedName("points") val points: List<HistoryPointDto> = emptyList(),
)

// ---------- 告警 ----------

data class AlarmDto(
    @SerializedName("id") val id: Long,
    @SerializedName("ts") val ts: Long,
    @SerializedName("metric") val metric: String,
    @SerializedName("value") val value: Double,
    /** 契约 v1.1 叫 limit，v1.2 建议改名 threshold，这里两种都收 */
    @SerializedName("limit") val limit: Double? = null,
    @SerializedName("threshold") val threshold: Double? = null,
    @SerializedName("level") val level: String,
    @SerializedName("ack") val ack: Boolean = false,
) {
    val thresholdValue: Double? get() = threshold ?: limit
}

data class AlarmListDto(
    @SerializedName("items") val items: List<AlarmDto> = emptyList(),
    /** v1.2 才有；v1.1 返回 null，前端缺失时自行统计 */
    @SerializedName("unread") val unread: Int? = null,
    @SerializedName("total") val total: Int? = null,
)

data class AckDto(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("unread") val unread: Int? = null,
)

// ---------- 命令 ----------

data class CommandDto(
    @SerializedName("cmd") val cmd: String,
    /** 用 JsonElement 承接"可能是数字也可能是字符串"，避免 Gson 把 1 读成 1.0 的类型坑 */
    @SerializedName("value") val value: JsonElement? = null,
    @SerializedName("metric") val metric: String? = null,
    @SerializedName("text") val text: String? = null,
)

data class CommandResultDto(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("cmd_id") val cmdId: String? = null,
)

// ---------- 对话 ----------

data class ChatRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("text") val text: String,
)

data class ChatActionDto(
    @SerializedName("cmd") val cmd: String,
    @SerializedName("value") val value: JsonElement? = null,
    @SerializedName("cmd_id") val cmdId: String? = null,
    @SerializedName("display_text") val displayText: String = "",
)

data class ChatReplyDto(
    @SerializedName("reply") val reply: String,
    @SerializedName("action") val action: ChatActionDto? = null,
)

// ---------- 通用 ----------

data class HealthDto(
    @SerializedName("status") val status: String,
    @SerializedName("mqtt") val mqtt: Boolean = false,
    @SerializedName("db") val db: Boolean = false,
    @SerializedName("version") val version: String = "",
)

data class ApiErrorDto(
    @SerializedName("error") val error: ApiErrorBody,
)

data class ApiErrorBody(
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String,
    @SerializedName("request_id") val requestId: String? = null,
)
