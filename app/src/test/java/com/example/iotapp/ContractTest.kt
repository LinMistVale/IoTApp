package com.example.iotapp

import com.example.iotapp.core.UiState
import com.example.iotapp.data.DeviceRepository
import com.example.iotapp.data.remote.ApiClient
import com.example.iotapp.data.remote.ApiService
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 契约测试（任务书 §8）。
 *
 * 思路：夹具 JSON 就是契约，用 MockWebServer 原样返回，断言 App 能正确解析。
 * 后端哪天把字段改了，这里立刻红，比群里通知可靠。
 *
 * 夹具来源：app/src/test/resources/contract/（随仓库提交，直接读 classpath，无需同步任务）。
 */
class ContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiService
    private lateinit var repo: DeviceRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString()
        api = ApiClient.buildForTest(baseUrl)
        repo = DeviceRepository(api = api, chatApi = ApiClient.buildChatForTest(baseUrl))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------- 正常路径 ----------

    @Test
    fun latest_parses_contract_fixture() = runBlocking {
        enqueue("latest.json")
        val state = repo.telemetry("s3-01").last()

        assertTrue("应为 Success，实际 $state", state is UiState.Success)
        val ui = (state as UiState.Success).data
        assertEquals("24.6", ui.tempText)
        assertEquals("55.2", ui.humiText)
        assertEquals("320", ui.luxText)
        assertEquals(true, ui.online)
        // 注意：夹具里的 ts 是固定的历史时间，按真实"现在"算必然 stale=true，
        // stale 的计算逻辑由 MapperTest 用固定时间单独覆盖，这里不做断言。
    }

    @Test
    fun config_renders_all_control_options() = runBlocking {
        enqueue("config.json")
        val state = repo.config("s3-01")

        assertTrue(state is UiState.Success)
        val cfg = (state as UiState.Success).data
        assertEquals(3, cfg.metrics.size)
        assertEquals(listOf(2, 5, 10, 30), cfg.intervalOptions)
        assertEquals(6, cfg.commands.size)
        assertEquals(15f, cfg.thresholds.getValue("temp").min)
        assertEquals(40f, cfg.thresholds.getValue("temp").max)
        assertEquals(2, cfg.modes.size)
        assertEquals("温度", cfg.metricLabel("temp"))
    }

    @Test
    fun history_parses_points() = runBlocking {
        enqueue("history.json")
        val state = repo.history("s3-01", "temp").last()

        assertTrue(state is UiState.Success)
        val ui = (state as UiState.Success).data
        assertEquals(5, ui.points.size)
        assertEquals("温度", ui.metricLabel)
        assertEquals("℃", ui.unit)
        assertEquals(true, ui.downsampled)
    }

    @Test
    fun alarms_map_level_and_ack() = runBlocking {
        enqueue("alarms.json")
        val state = repo.alarms("s3-01").last()

        assertTrue(state is UiState.Success)
        val ui = (state as UiState.Success).data
        assertEquals(2, ui.items.size)
        assertEquals("warn", ui.items[0].level)
        assertEquals(false, ui.items[0].ack)
        // 契约 v1.1 不返回 unread，前端自己数：2 条里 1 条未读
        assertEquals(1, ui.unreadCount)
    }

    @Test
    fun chat_action_has_display_text() = runBlocking {
        enqueue("chat.json")
        val state = repo.chat("s3-01", "开灯")

        assertTrue(state is UiState.Success)
        val ui = (state as UiState.Success).data
        assertEquals("已开灯", ui.actionText)
        assertNotNull(ui.reply)
    }

    // ---------- 边界数据（最容易崩的分支）----------

    @Test
    fun latest_offline_shows_placeholder() = runBlocking {
        enqueue("latest_offline.json")
        val state = repo.telemetry("s3-01").last()

        assertTrue(state is UiState.Success)
        val ui = (state as UiState.Success).data
        assertEquals("--", ui.tempText)
        assertEquals("--", ui.humiText)
        assertEquals("--", ui.luxText)
        assertEquals(false, ui.online)
    }

    @Test
    fun empty_collections_do_not_crash() = runBlocking {
        enqueue("alarms_empty.json")
        val alarms = repo.alarms("s3-01").last()
        assertTrue(alarms is UiState.Success)
        assertEquals(0, (alarms as UiState.Success).data.items.size)
        assertEquals(0, alarms.data.unreadCount)

        enqueue("history_empty.json")
        val history = repo.history("s3-01", "lux").last()
        assertTrue(history is UiState.Success)
        assertEquals(0, (history as UiState.Success).data.points.size)
    }

    @Test
    fun chat_without_action_returns_null_text() = runBlocking {
        enqueue("chat_no_action.json")
        val state = repo.chat("s3-01", "现在多少度")

        assertTrue(state is UiState.Success)
        assertNull((state as UiState.Success).data.actionText)
    }

    // ---------- 错误路径 ----------

    @Test
    fun device_offline_maps_to_offline_state() = runBlocking {
        enqueue("error_offline.json", code = 503)
        val state = repo.telemetry("s3-01").last()

        assertTrue("应为 Offline，实际 $state", state is UiState.Offline)
    }

    @Test
    fun offline_keeps_last_successful_data() = runBlocking {
        enqueue("latest.json")
        repo.telemetry("s3-01").last()

        enqueue("error_offline.json", code = 503)
        val state = repo.telemetry("s3-01").last()

        assertTrue(state is UiState.Offline)
        val last = (state as UiState.Offline).last
        assertNotNull("离线时应保留上一次的数据", last)
        assertEquals("24.6", last!!.tempText)
    }

    @Test
    fun invalid_param_maps_to_error_code() = runBlocking {
        enqueue("error_400.json", code = 400)
        val state = repo.setInterval("s3-01", 90)

        assertTrue(state is UiState.Error)
        assertEquals("INVALID_PARAM", (state as UiState.Error).code)
    }

    @Test
    fun ai_timeout_maps_to_error_code() = runBlocking {
        enqueue("error_504.json", code = 504)
        val state = repo.chat("s3-01", "你好")

        assertTrue(state is UiState.Error)
        assertEquals("AI_TIMEOUT", (state as UiState.Error).code)
    }

    // ---------- 请求体也要对 ----------

    @Test
    fun set_led_sends_contract_command_body() = runBlocking {
        enqueue("command_ok.json")
        val state = repo.setLed("s3-01", true)

        assertTrue(state is UiState.Success)
        val body = server.takeRequest().body.readUtf8()
        assertTrue("请求体应包含 cmd=led：$body", body.contains("\"cmd\":\"led\""))
        assertTrue("开灯应下发 1：$body", body.contains("\"value\":1"))
    }

    @Test
    fun set_threshold_carries_metric() = runBlocking {
        enqueue("command_ok.json")
        repo.setThreshold("s3-01", "temp", 28.5)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"cmd\":\"threshold\""))
        assertTrue(body.contains("\"metric\":\"temp\""))
        assertTrue(body.contains("28.5"))
    }

    // ---------- 工具 ----------

    /** 让下一次请求返回指定夹具 */
    private fun enqueue(name: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture(name))
        )
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("contract/$name")!!.readText()
}
