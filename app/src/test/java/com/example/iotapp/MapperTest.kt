package com.example.iotapp

import com.example.iotapp.data.PLACEHOLDER
import com.example.iotapp.data.toUi
import com.example.iotapp.data.remote.dto.AlarmDto
import com.example.iotapp.data.remote.dto.ChatActionDto
import com.example.iotapp.data.remote.dto.ChatReplyDto
import com.example.iotapp.data.remote.dto.HistoryDto
import com.example.iotapp.data.remote.dto.HistoryPointDto
import com.example.iotapp.data.remote.dto.TelemetryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapper 纯函数单测：不走网络，专门盯格式化与边界。
 * 这类测试最快，改 Mapper 时先跑它。
 */
class MapperTest {

    private val epoch = 1_730_000_000L

    @Test
    fun telemetry_formats_one_decimal() {
        val ui = TelemetryDto(
            deviceId = "s3-01", ts = epoch,
            temp = 24.567, humi = 55.0, lux = 320, online = true,
        ).toUi(nowSec = epoch + 3)

        assertEquals("24.6", ui.tempText)   // 四舍五入到 1 位
        assertEquals("55.0", ui.humiText)   // 不能显示成 55
        assertEquals("320", ui.luxText)     // lux 是整数
        assertEquals("刚刚", ui.updatedText)
        assertFalse(ui.stale)
    }

    @Test
    fun null_values_show_placeholder() {
        val ui = TelemetryDto(
            deviceId = "s3-01", ts = epoch,
            temp = null, humi = null, lux = null, online = false,
        ).toUi(nowSec = epoch + 1)

        assertEquals(PLACEHOLDER, ui.tempText)
        assertEquals(PLACEHOLDER, ui.humiText)
        assertEquals(PLACEHOLDER, ui.luxText)
    }

    @Test
    fun stale_is_true_after_threshold() {
        val dto = TelemetryDto(deviceId = "s3-01", ts = epoch, temp = 20.0, online = true)

        assertFalse(dto.toUi(nowSec = epoch + 10, staleSeconds = 10).stale)  // 边界：恰好 10 秒不算过期
        assertTrue(dto.toUi(nowSec = epoch + 11, staleSeconds = 10).stale)
    }

    @Test
    fun relative_time_text() {
        val dto = TelemetryDto(deviceId = "s3-01", ts = epoch, temp = 20.0)
        assertEquals("5 秒前", dto.toUi(nowSec = epoch + 5).updatedText)
        assertEquals("2 分钟前", dto.toUi(nowSec = epoch + 125).updatedText)
        assertEquals("1 小时前", dto.toUi(nowSec = epoch + 3700).updatedText)
    }

    @Test
    fun history_maps_points_and_default_labels() {
        val ui = HistoryDto(
            metric = "humi",
            points = listOf(HistoryPointDto(epoch, 55.2), HistoryPointDto(epoch + 300, 56.0)),
            downsampled = true,
        ).toUi()

        assertEquals("湿度", ui.metricLabel)
        assertEquals("%", ui.unit)
        assertEquals(2, ui.points.size)
        assertEquals(55.2f, ui.points[0].value, 0.001f)
        assertTrue(ui.downsampled)
    }

    @Test
    fun unknown_metric_falls_back_to_key() {
        val ui = HistoryDto(metric = "co2", points = emptyList()).toUi()
        assertEquals("co2", ui.metricLabel)   // 后端新增指标也不崩
        assertEquals("", ui.unit)
    }

    @Test
    fun alarm_uses_threshold_alias() {
        // v1.1 字段名是 limit；v1.2 会改成 threshold，两种都要能读
        val old = AlarmDto(id = 1, ts = epoch, metric = "temp", value = 31.2, limit = 30.0, level = "warn", ack = false)
        val new = old.copy(limit = null, threshold = 30.0)

        assertEquals("30.0℃", old.toUi(nowSec = epoch).limitText)
        assertEquals("30.0℃", new.toUi(nowSec = epoch).limitText)
        assertEquals("31.2℃", old.toUi(nowSec = epoch).valueText)
    }

    @Test
    fun alarm_without_threshold_shows_placeholder() {
        val a = AlarmDto(id = 2, ts = epoch, metric = "humi", value = 91.5, level = "danger", ack = true)
        assertEquals(PLACEHOLDER, a.toUi(nowSec = epoch).limitText)
    }

    @Test
    fun chat_without_action_is_null() {
        val ui = ChatReplyDto(reply = "室内 24.6 ℃", action = null).toUi()
        assertEquals("室内 24.6 ℃", ui.reply)
        assertNull(ui.actionText)
    }

    @Test
    fun chat_blank_display_text_treated_as_null() {
        val ui = ChatReplyDto(
            reply = "好的",
            action = ChatActionDto(cmd = "led", displayText = "  "),
        ).toUi()
        assertNull(ui.actionText)   // 后端漏填时不要显示一片空白
    }
}
