package com.example.iotapp.model

/**
 * 界面层唯一认的模型（任务书 §5.2）。
 *
 * 规则：`home/` `history/` `control/` 等 UI 包里 **不允许出现 xxxDto**，
 * 所有字段已经是可直接显示的文本，UI 只做绑定，不做格式化。
 */

/** 首页实时数据。数值字段已格式化，缺数据显示 `--`。 */
data class TelemetryUi(
    val tempText: String,
    val humiText: String,
    val luxText: String,
    /** "3 秒前" 这类相对时间 */
    val updatedText: String,
    val online: Boolean,
    /** 数据陈旧：超过阈值没更新，卡片需要置灰提示 */
    val stale: Boolean,
)

data class HistoryPointUi(val ts: Long, val value: Float)

/** 历史曲线。降采样由后端做，App 只负责画图。 */
data class HistoryUi(
    val metricLabel: String,
    val unit: String,
    val points: List<HistoryPointUi>,
    val downsampled: Boolean,
)

/** 单条告警，文字已就绪。 */
data class AlarmUi(
    val id: Long,
    val metricLabel: String,
    val valueText: String,
    val limitText: String,
    /** info / warn / danger，UI 用它选颜色 */
    val level: String,
    val timeText: String,
    val ack: Boolean,
)

/** 告警列表。[unread] 目前后端不返回（契约 v1.1 无该字段），为空时前端自己数 [items]。 */
data class AlarmListUi(
    val items: List<AlarmUi>,
    val unread: Int?,
) {
    /** 角标显示用：优先用后端给的，没有就按本地列表算。 */
    val unreadCount: Int get() = unread ?: items.count { !it.ack }
}

/** 滑块类控件的范围。 */
data class RangeUi(
    val min: Float,
    val max: Float,
    val step: Float,
    val default: Float,
)

/** 下拉类控件的选项（如上报间隔 2/5/10/30 秒）。 */
data class SelectUi(val options: List<Int>, val default: Int)

/** 通用「值 + 中文名」选项，用于工作模式等。 */
data class OptionUi(val value: String, val label: String)

/** 一个指标（温度/湿度/光照）的显示规格。 */
data class MetricSpecUi(
    val key: String,
    val label: String,
    val unit: String,
    val precision: Int,
)

/** /config 的 commands 数组里的一项，决定控制页显示什么控件。 */
data class CommandSpecUi(
    val cmd: String,
    val label: String,
    val type: String,
)

/**
 * 设备能力描述（/config）。
 * 控制页的滑块范围、下拉选项、按钮列表全部从这里来，**禁止在 XML 里写死**。
 */
data class DeviceConfigUi(
    val deviceId: String,
    val version: Int,
    val metrics: List<MetricSpecUi>,
    val thresholds: Map<String, RangeUi>,
    val intervalOptions: List<Int>,
    val intervalDefault: Int,
    val volume: RangeUi,
    val modes: List<OptionUi>,
    val commands: List<CommandSpecUi>,
) {
    fun metricLabel(key: String): String = metrics.firstOrNull { it.key == key }?.label ?: key
    fun metricUnit(key: String): String = metrics.firstOrNull { it.key == key }?.unit ?: ""
}

/** 一次对话的结果。[actionText] 由后端生成，前端直接显示，不拼文案。 */
data class ChatUi(
    val reply: String,
    val actionText: String?,
)

/** 命令下发结果。 */
data class CommandResultUi(val cmdId: String?)
