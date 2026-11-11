package com.example.iotapp.settings

import android.content.Context
import com.example.iotapp.core.AppConfig
import com.example.iotapp.data.remote.ApiClient

/**
 * 本地配置：服务器地址 + 设备 ID + MQTT 地址。
 *
 * 为什么必须有它：手机热点/校园网每次切换，电脑和后端的 IP 都会变
 * （2026-11-11 当天就从 192.168.116.38 变成 192.168.22.38，之后又变成后端 10.226.236.84）。
 * 把地址写死在代码里 = 每次换网都要重新编译，所以改成运行时可在设置页修改。
 *
 * 默认值统一来自 [AppConfig]，换环境只改那一个文件。
 */
class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("iot_settings", Context.MODE_PRIVATE)

    /** 形如 `http://192.168.22.38:8000/`，写入时自动补协议头和结尾斜杠 */
    var baseUrl: String
        get() = sp.getString(KEY_BASE_URL, ApiClient.DEFAULT_BASE_URL) ?: ApiClient.DEFAULT_BASE_URL
        set(value) {
            sp.edit().putString(KEY_BASE_URL, normalizeUrl(value)).apply()
        }

    var deviceId: String
        get() = sp.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID) ?: DEFAULT_DEVICE_ID
        set(value) {
            sp.edit().putString(KEY_DEVICE_ID, value.trim().ifBlank { DEFAULT_DEVICE_ID }).apply()
        }

    /**
     * MQTT Broker 地址，形如 `tcp://10.226.236.84:1883`。
     * 目前只做存储与展示，实时推送在 D5 接入后才会真正使用。
     */
    var mqttUrl: String
        get() = sp.getString(KEY_MQTT_URL, AppConfig.MQTT_URL) ?: AppConfig.MQTT_URL
        set(value) {
            sp.edit().putString(KEY_MQTT_URL, value.trim().ifBlank { AppConfig.MQTT_URL }).apply()
        }

    companion object {
        const val DEFAULT_DEVICE_ID = AppConfig.DEVICE_ID

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_MQTT_URL = "mqtt_url"

        /**
         * 规范化地址。Retrofit 的 baseUrl **必须以 `/` 结尾**，
         * 否则一创建就抛 IllegalArgumentException，而且报错信息很难懂。
         */
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return ApiClient.DEFAULT_BASE_URL
            val withScheme =
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
                else "http://$trimmed"
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }
    }
}
