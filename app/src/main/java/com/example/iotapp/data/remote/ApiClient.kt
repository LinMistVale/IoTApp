package com.example.iotapp.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络客户端工厂。
 *
 * 两套超时（契约 §1.2）：
 * - 普通接口 5 秒：latest / history / alarms / command / config / status
 * - 对话接口 15 秒：chat 要等 ASR + LLM + TTS，用 5 秒必超时
 *
 * TODO(D3)：BASE_URL 后续迁到 SettingsStore，由设置页修改（热点重连后 IP 会变）
 */
object ApiClient {

    /**
     * 默认地址 —— **只作为兜底**。
     * 手机热点每次重连，电脑的 192.168.x.y 都会变（2026-11-11 就从 192.168.116.38 变成 192.168.22.38），
     * 所以真正的地址必须由设置页写入 SharedPreferences，见 D3 的 SettingsStore。
     */
    const val DEFAULT_BASE_URL = "http://192.168.22.38:8000/"

    private const val NORMAL_TIMEOUT_SEC = 5L
    private const val CHAT_TIMEOUT_SEC = 15L

    /** 打印请求/响应体，联调期很有用；上线前改成 NONE */
    private val LOG_LEVEL = HttpLoggingInterceptor.Level.BODY

    val gson: Gson = GsonBuilder().setLenient().create()

    private fun client(timeoutSec: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSec, TimeUnit.SECONDS)
        .readTimeout(timeoutSec, TimeUnit.SECONDS)
        .writeTimeout(timeoutSec, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = LOG_LEVEL })
        .build()

    private fun retrofit(baseUrl: String, timeoutSec: Long): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client(timeoutSec))
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    /** 普通接口（5 s） */
    fun api(baseUrl: String = DEFAULT_BASE_URL): ApiService =
        retrofit(baseUrl, NORMAL_TIMEOUT_SEC).create(ApiService::class.java)

    /** 对话接口（15 s） */
    fun chatApi(baseUrl: String = DEFAULT_BASE_URL): ApiService =
        retrofit(baseUrl, CHAT_TIMEOUT_SEC).create(ApiService::class.java)

    /** 单元测试用：指向 MockWebServer 的地址 */
    fun buildForTest(baseUrl: String): ApiService = api(baseUrl)

    fun buildChatForTest(baseUrl: String): ApiService = chatApi(baseUrl)
}
