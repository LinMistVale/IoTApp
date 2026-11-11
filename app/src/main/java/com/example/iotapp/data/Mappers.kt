package com.example.iotapp.data

import com.example.iotapp.data.remote.dto.AlarmDto
import com.example.iotapp.data.remote.dto.AlarmListDto
import com.example.iotapp.data.remote.dto.ChatReplyDto
import com.example.iotapp.data.remote.dto.CommandResultDto
import com.example.iotapp.data.remote.dto.DeviceConfigDto
import com.example.iotapp.data.remote.dto.HistoryDto
import com.example.iotapp.data.remote.dto.TelemetryDto
import com.example.iotapp.model.AlarmListUi
import com.example.iotapp.model.AlarmUi
import com.example.iotapp.model.ChatUi
import com.example.iotapp.model.CommandResultUi
import com.example.iotapp.model.CommandSpecUi
import com.example.iotapp.model.DeviceConfigUi
import com.example.iotapp.model.HistoryPointUi
import com.example.iotapp.model.HistoryUi
import com.example.iotapp.model.MetricSpecUi
import com.example.iotapp.model.OptionUi
import com.example.iotapp.model.RangeUi
import com.example.iotapp.model.TelemetryUi
import com.example.iotapp.util.TimeUtil
import java.util.Locale

/**
 * DTO → UiModel 的唯一转换处（任务书 §5.3）。
 *
 * **接口字段变了，只改这个文件**，UI 层一行不用动。
 * 所有格式化（小数位、`--` 占位、相对时间）都在这里完成。
 */

/** 数值缺失时统一显示的占位符 */
const val PLACEHOLDER = "--"

/** 契约 v1.1 没有 stale_seconds，先用 10 秒；拿到 /config 后应改用后端给的值 */
const val STALE_SECONDS_DEFAULT = 10

// ---------- 实时数据 ----------

fun TelemetryDto.toUi(
    nowSec: Long = TimeUtil.nowSec(),
    staleSeconds: Int = STALE_SECONDS_DEFAULT,
): TelemetryUi = TelemetryUi(
    tempText = formatNumber(temp, 1),
    humiText = formatNumber(humi, 1),
    luxText = lux?.toString() ?: PLACEHOLDER,
    updatedText = TimeUtil.friendly(ts, nowSec),
    online = online,
    // TODO(D5)：改成读 /config 的 stale_seconds，避免 interval=30 时首页永远显示"过期"
    stale = online && nowSec - ts > staleSeconds,
)

// ---------- 能力发现 ----------

fun DeviceConfigDto.toUi(): DeviceConfigUi = DeviceConfigUi(
    deviceId = deviceId,
    version = version,
    metrics = metrics.map { MetricSpecUi(it.key, it.label, it.unit, it.precision) },
    thresholds = thresholds.mapValues {
        RangeUi(it.value.min.toFloat(), it.value.max.toFloat(), it.value.step.toFloat(), it.value.defaultValue.toFloat())
    },
    intervalOptions = interval.options,
    intervalDefault = interval.defaultValue,
    volume = RangeUi(volume.min.toFloat(), volume.max.toFloat(), volume.step.toFloat(), volume.defaultValue.toFloat()),
    modes = modes.map { OptionUi(it.value, it.label) },
    commands = commands.map { CommandSpecUi(it.cmd, it.label, it.type) },
)

// ---------- 历史 ----------

fun HistoryDto.toUi(config: DeviceConfigUi? = null): HistoryUi {
    val label = config?.metricLabel(metric) ?: DEFAULT_METRIC_LABELS[metric] ?: metric
    val unit = config?.metricUnit(metric) ?: DEFAULT_METRIC_UNITS[metric] ?: ""
    return HistoryUi(
        metricLabel = label,
        unit = unit,
        points = points.map { HistoryPointUi(it.ts, it.value.toFloat()) },
        downsampled = downsampled,
    )
}

// ---------- 告警 ----------

fun AlarmDto.toUi(
    config: DeviceConfigUi? = null,
    nowSec: Long = TimeUtil.nowSec(),
): AlarmUi {
    val label = config?.metricLabel(metric) ?: DEFAULT_METRIC_LABELS[metric] ?: metric
    val unit = config?.metricUnit(metric) ?: DEFAULT_METRIC_UNITS[metric] ?: ""
    val precision = config?.metrics?.firstOrNull { it.key == metric }?.precision ?: 1
    return AlarmUi(
        id = id,
        metricLabel = label,
        valueText = formatNumber(value, precision) + unit,
        limitText = thresholdValue?.let { formatNumber(it, precision) + unit } ?: PLACEHOLDER,
        level = level,
        timeText = TimeUtil.friendly(ts, nowSec),
        ack = ack,
    )
}

fun AlarmListDto.toUi(
    config: DeviceConfigUi? = null,
    nowSec: Long = TimeUtil.nowSec(),
): AlarmListUi = AlarmListUi(
    items = items.map { it.toUi(config, nowSec) },
    unread = unread,
)

// ---------- 对话 / 命令 ----------

fun ChatReplyDto.toUi(): ChatUi = ChatUi(
    reply = reply,
    // display_text 由后端生成，前端直接显示，不拼文案
    actionText = action?.displayText?.takeIf { it.isNotBlank() },
)

fun CommandResultDto.toUi(): CommandResultUi = CommandResultUi(cmdId = cmdId)

// ---------- 内部工具 ----------

private fun formatNumber(value: Double?, precision: Int): String =
    if (value == null) PLACEHOLDER
    else String.format(Locale.US, "%.${precision}f", value)

/** /config 还没拿到时的兜底叫法（控制页会把 config 传进来，优先用后端给的 label） */
private val DEFAULT_METRIC_LABELS = mapOf("temp" to "温度", "humi" to "湿度", "lux" to "光照")
private val DEFAULT_METRIC_UNITS = mapOf("temp" to "℃", "humi" to "%", "lux" to "lx")
