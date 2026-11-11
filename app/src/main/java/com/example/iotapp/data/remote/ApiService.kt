package com.example.iotapp.data.remote

import com.example.iotapp.data.remote.dto.AckDto
import com.example.iotapp.data.remote.dto.AlarmListDto
import com.example.iotapp.data.remote.dto.ChatReplyDto
import com.example.iotapp.data.remote.dto.ChatRequest
import com.example.iotapp.data.remote.dto.CommandDto
import com.example.iotapp.data.remote.dto.CommandResultDto
import com.example.iotapp.data.remote.dto.DeviceConfigDto
import com.example.iotapp.data.remote.dto.DeviceStatusDto
import com.example.iotapp.data.remote.dto.HealthDto
import com.example.iotapp.data.remote.dto.HistoryDto
import com.example.iotapp.data.remote.dto.TelemetryDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST 接口定义，路径与参数严格对照 `接口契约.md` §2。
 *
 * 这里只声明「怎么调」，不处理错误：
 * 非 2xx 会抛 retrofit2.HttpException，由 Repository 统一转成 UiState。
 */
interface ApiService {

    @GET("api/v1/healthz")
    suspend fun healthz(): HealthDto

    /** 能力发现：控制页的滑块范围、下拉选项、命令列表都由它渲染 */
    @GET("api/v1/devices/{id}/config")
    suspend fun config(@Path("id") deviceId: String): DeviceConfigDto

    /** 实时数据 */
    @GET("api/v1/devices/{id}/latest")
    suspend fun latest(@Path("id") deviceId: String): TelemetryDto

    /**
     * 历史数据。
     * @param bucket auto / raw / 1m / 5m / 1h，默认 auto 由后端选最小档位
     * @param limit 默认 500，上限 2000；App 不做降采样
     */
    @GET("api/v1/devices/{id}/history")
    suspend fun history(
        @Path("id") deviceId: String,
        @Query("metric") metric: String,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null,
        @Query("bucket") bucket: String = "auto",
        @Query("limit") limit: Int = 500,
    ): HistoryDto

    @GET("api/v1/devices/{id}/alarms")
    suspend fun alarms(
        @Path("id") deviceId: String,
        @Query("limit") limit: Int = 50,
        @Query("unacked_only") unackedOnly: Boolean = false,
    ): AlarmListDto

    @POST("api/v1/alarms/{alarmId}/ack")
    suspend fun ack(@Path("alarmId") alarmId: Long): AckDto

    @POST("api/v1/devices/{id}/command")
    suspend fun command(
        @Path("id") deviceId: String,
        @Body body: CommandDto,
    ): CommandResultDto

    @GET("api/v1/devices/{id}/status")
    suspend fun status(@Path("id") deviceId: String): DeviceStatusDto

    /** 文字对话。AI 链路慢，必须用 15 秒超时的 client，不能用默认 5 秒那个 */
    @POST("api/v1/chat/text")
    suspend fun chatText(@Body body: ChatRequest): ChatReplyDto
}
