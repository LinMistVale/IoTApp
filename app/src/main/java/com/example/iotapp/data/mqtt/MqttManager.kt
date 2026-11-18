package com.example.iotapp.data.mqtt

import android.util.Log
import com.example.iotapp.core.AppConfig
import com.example.iotapp.data.remote.dto.AlarmDto
import com.example.iotapp.data.remote.dto.DeviceStatusDto
import com.example.iotapp.data.remote.dto.TelemetryDto
import com.google.gson.Gson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * MQTT 连接与分发（契约 §3）。
 *
 * 设计要点：
 * 1. **单例、跟随进程**。不做前台服务 —— App 被杀就断，这是 Android 的既定行为，
 *    换来的是不用在通知栏常驻一条"正在监控"。
 * 2. **订阅通配符 `iot/{id}/#`**，一条订阅覆盖 telemetry / latest / alarm / status 四个主题。
 * 3. **只解析、不加工**：消息转成 DTO 就发出去，UiModel 转换在仓库层做（那边有 config 缓存）。
 * 4. clientId 必须每台设备唯一，否则 broker 会踢掉旧连接。由调用方传入已持久化的 UUID。
 *
 * 实测（2026-11-11，后端 10.226.236.84:1883）：
 * telemetry / latest 每 2 秒各一条（QoS1），status 会重复两次（QoS0+QoS1），
 * alarm 是 retained，订阅瞬间就会补发最后一条 —— 所以上层必须按 id 去重。
 */
object MqttManager {

    private const val TAG = "MqttManager"

    private val gson = Gson()

    @Volatile
    private var client: MqttAsyncClient? = null

    @Volatile
    private var currentClientId: String? = null

    @Volatile
    private var deviceId: String = AppConfig.DEVICE_ID

    /** 事件流。缓冲区满了丢最旧的 —— 遥测每 2 秒一条，旧数据没有保留价值。 */
    private val _events = MutableSharedFlow<MqttEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MqttEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow<MqttState>(MqttState.Disconnected)
    val state: StateFlow<MqttState> = _state.asStateFlow()

    /**
     * 连接并订阅。重复调用同样的参数会直接返回，避免设置页反复点保存时把连接踢掉重建。
     */
    @Synchronized
    fun connect(rawUrl: String, deviceId: String, clientId: String) {
        if (client?.isConnected == true && this.deviceId == deviceId && currentClientId == clientId) {
            return
        }

        teardown()

        this.deviceId = deviceId
        currentClientId = clientId
        _state.value = MqttState.Connecting

        try {
            val c = MqttAsyncClient(normalize(rawUrl), clientId, MemoryPersistence())
            c.setCallback(callback)

            val options = MqttConnectOptions().apply {
                isCleanSession = AppConfig.MQTT_CLEAN_SESSION
                isAutomaticReconnect = true
                keepAliveInterval = 30
                connectionTimeout = 5
                maxInflight = 20
            }

            client = c
            c.connect(options)
            Log.i(TAG, "connecting to ${normalize(rawUrl)} as $clientId")
        } catch (e: Exception) {
            // 地址写错、端口不通都会在这里抛，不能让它把 App 带崩
            Log.w(TAG, "connect failed", e)
            client = null
            _state.value = MqttState.Lost(e.message ?: "connect failed")
        }
    }

    @Synchronized
    fun disconnect() {
        teardown()
        _state.value = MqttState.Disconnected
    }

    private fun teardown() {
        val c = client ?: return
        client = null
        currentClientId = null
        try {
            if (c.isConnected) c.disconnect(1000)
            c.close()
        } catch (e: Exception) {
            Log.w(TAG, "teardown", e)
        }
    }

    private val callback = object : MqttCallbackExtended {

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            Log.i(TAG, "connected (reconnect=$reconnect)")
            _state.value = MqttState.Connected
            subscribeAll()
        }

        override fun connectionLost(cause: Throwable?) {
            // 自动重连会接着跑，这里只更新状态，界面据此切到 HTTP 兜底
            Log.w(TAG, "connection lost: ${cause?.message}")
            _state.value = MqttState.Lost(cause?.message ?: "connection lost")
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            val t = topic ?: return
            val payload = message?.payload ?: return
            dispatch(t, String(payload, Charsets.UTF_8))
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    private fun subscribeAll() {
        val c = client ?: return
        val filter = "iot/$deviceId/#"
        try {
            c.subscribe(filter, AppConfig.MQTT_QOS)
            Log.i(TAG, "subscribed $filter")
        } catch (e: Exception) {
            Log.w(TAG, "subscribe failed: $filter", e)
        }
    }

    private fun dispatch(topic: String, payload: String) {
        try {
            when (topic) {
                AppConfig.topicTelemetry(deviceId),
                AppConfig.topicLatest(deviceId),
                -> {
                    val dto = gson.fromJson(payload, TelemetryDto::class.java) ?: return
                    // telemetry 的 payload 没有 online 字段，而 Gson 是反射建对象、不走 Kotlin 默认值，
                    // 会把 Boolean 填成 false —— 直接用会让首页在「在线/离线」之间来回跳。
                    // 没有这个字段就当作在线（能收到消息本身就说明设备在发）。
                    val fixed = if (payload.contains("\"online\"")) dto else dto.copy(online = true)
                    _events.tryEmit(MqttEvent.Telemetry(fixed))
                }

                AppConfig.topicAlarm(deviceId) -> {
                    val dto = gson.fromJson(payload, AlarmDto::class.java) ?: return
                    _events.tryEmit(MqttEvent.Alarm(dto))
                }

                AppConfig.topicStatus(deviceId) -> {
                    val dto = gson.fromJson(payload, DeviceStatusDto::class.java) ?: return
                    _events.tryEmit(MqttEvent.Status(dto))
                }

                else -> Unit
            }
        } catch (e: Exception) {
            // 后端某天多推了个字段或换了格式，不能让整个连接挂掉
            Log.w(TAG, "parse failed on $topic: ${payload.take(120)}", e)
        }
    }

    /** 允许设置页只填 `10.226.236.84:1883`，自动补协议头 */
    private fun normalize(raw: String): String {
        val t = raw.trim()
        return when {
            t.isBlank() -> AppConfig.MQTT_URL
            t.startsWith("tcp://") || t.startsWith("ssl://") || t.startsWith("ws://") -> t
            else -> "tcp://$t"
        }
    }
}
