package com.example.iotapp.data

import com.example.iotapp.core.ApiException
import com.example.iotapp.core.UiState
import com.example.iotapp.core.toUiState
import com.example.iotapp.data.remote.ApiClient
import com.example.iotapp.data.remote.ApiService
import com.example.iotapp.data.remote.toApiException
import com.example.iotapp.data.remote.dto.ChatRequest
import com.example.iotapp.data.remote.dto.CommandDto
import com.example.iotapp.model.AlarmListUi
import com.example.iotapp.model.ChatUi
import com.example.iotapp.model.CommandResultUi
import com.example.iotapp.model.DeviceConfigUi
import com.example.iotapp.model.HistoryUi
import com.example.iotapp.model.TelemetryUi
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * 数据层唯一入口（任务书 §6.2）。
 *
 * 铁律：
 * 1. UI **只**通过这里拿数据，不允许直接碰 [ApiService]；
 * 2. 返回值一律是 [UiState] + UiModel，**不返回 DTO**；
 * 3. 命令走类型化方法（setLed / setThreshold…），ViewModel 不需要自己拼 CommandDto。
 *
 * 这样后续加鉴权 Interceptor、加本地缓存、接入 MQTT，UI 层一行都不用改。
 */
class DeviceRepository(
    private val api: ApiService = ApiClient.api(),
    private val chatApi: ApiService = ApiClient.chatApi(),
) {

    /** 最后一次成功的实时数据，设备离线时用它做兜底展示 */
    private var lastTelemetry: TelemetryUi? = null

    /** /config 缓存，避免每次进控制页都请求 */
    private var configCache: DeviceConfigUi? = null

    // ---------- 查询 ----------

    /** 首页实时数据。先发 Loading，再发结果，UI 直接 collect 即可。 */
    fun telemetry(deviceId: String): Flow<UiState<TelemetryUi>> = flow {
        emit(UiState.Loading)
        val state = fetch(lastTelemetry) { api.latest(deviceId).toUi() }
        if (state is UiState.Success) lastTelemetry = state.data
        emit(state)
    }.flowOn(Dispatchers.IO)

    /** 历史曲线。降采样由后端完成，App 只画图。 */
    fun history(
        deviceId: String,
        metric: String,
        from: Long? = null,
        to: Long? = null,
        bucket: String = "auto",
        limit: Int = 500,
    ): Flow<UiState<HistoryUi>> = flow {
        emit(UiState.Loading)
        emit(fetch(null) { api.history(deviceId, metric, from, to, bucket, limit).toUi(configCache) })
    }.flowOn(Dispatchers.IO)

    /** 告警列表。 */
    fun alarms(deviceId: String, limit: Int = 50): Flow<UiState<AlarmListUi>> = flow {
        emit(UiState.Loading)
        emit(fetch(null) { api.alarms(deviceId, limit).toUi(configCache) })
    }.flowOn(Dispatchers.IO)

    /**
     * 能力发现。默认走缓存，[forceRefresh] = true 才重新请求。
     * 注意：该接口离线也应可用（后端返回缓存配置），所以失败时不清空旧缓存。
     */
    suspend fun config(deviceId: String, forceRefresh: Boolean = false): UiState<DeviceConfigUi> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                configCache?.let { return@withContext UiState.Success(it) }
            }
            val state = fetch(null) { api.config(deviceId).toUi() }
            if (state is UiState.Success) configCache = state.data
            state
        }

    // ---------- 对话 ----------

    /** 文字对话。走 15 秒超时的 client，别用默认 5 秒那个。 */
    suspend fun chat(deviceId: String, text: String): UiState<ChatUi> =
        withContext(Dispatchers.IO) {
            fetch(null) { chatApi.chatText(ChatRequest(deviceId, text)).toUi() }
        }

    // ---------- 告警操作 ----------

    suspend fun ack(alarmId: Long): UiState<Unit> = withContext(Dispatchers.IO) {
        fetch(Unit) {
            if (api.ack(alarmId).ok) Unit
            else throw ApiException("ACK_FAILED", "标记已读失败")
        }
    }

    // ---------- 健康检查 ----------

    /** 设置页「测试连接」用：只关心能不能连通，不关心业务数据 */
    suspend fun ping(): UiState<Unit> = withContext(Dispatchers.IO) {
        fetch(Unit) {
            val health = api.healthz()
            if (health.status.equals("ok", ignoreCase = true)) Unit
            else throw ApiException("BAD_HEALTH", "服务状态异常：${health.status}")
        }
    }

    // ---------- 控制命令（类型化，禁止 UI 自己拼 DTO）----------

    suspend fun setLed(deviceId: String, on: Boolean): UiState<CommandResultUi> =
        send(deviceId, CommandDto(cmd = "led", value = json(if (on) 1 else 0)))

    suspend fun setThreshold(deviceId: String, metric: String, value: Double): UiState<CommandResultUi> =
        send(deviceId, CommandDto(cmd = "threshold", value = json(value), metric = metric))

    suspend fun setInterval(deviceId: String, seconds: Int): UiState<CommandResultUi> =
        send(deviceId, CommandDto(cmd = "interval", value = json(seconds)))

    suspend fun setVolume(deviceId: String, volume: Int): UiState<CommandResultUi> =
        send(deviceId, CommandDto(cmd = "volume", value = json(volume)))

    suspend fun setMode(deviceId: String, mode: String): UiState<CommandResultUi> =
        send(deviceId, CommandDto(cmd = "mode", value = json(mode)))

    suspend fun speak(deviceId: String, text: String): UiState<CommandResultUi> =
        send(deviceId, CommandDto(cmd = "speak", text = text))

    /**
     * TODO(D6)：设备状态 /status 目前没有对应的 UiModel，等首页要显示信号强度时再补
     * `suspend fun status(deviceId): UiState<DeviceStatusUi>`。
     */

    // ---------- 内部 ----------

    private suspend fun send(deviceId: String, cmd: CommandDto): UiState<CommandResultUi> =
        withContext(Dispatchers.IO) {
            fetch(null) {
                val result = api.command(deviceId, cmd)
                if (!result.ok) throw ApiException("CMD_FAILED", "命令下发失败")
                result.toUi()
            }
        }

    /** 统一把「网络调用 + 异常」收敛成 UiState，[last] 用于离线兜底 */
    private suspend fun <U> fetch(last: U?, block: suspend () -> U): UiState<U> = try {
        UiState.Success(block())
    } catch (e: HttpException) {
        e.toApiException(ApiClient.gson).toUiState(last)
    } catch (e: Exception) {
        e.toUiState(last)
    }

    /** 命令的 value 可能是数字也可能是字符串，统一转成 JsonElement 交给 Gson */
    private fun json(value: Any): JsonElement = ApiClient.gson.toJsonTree(value)
}
