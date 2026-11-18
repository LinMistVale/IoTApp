package com.example.iotapp.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.iotapp.R
import com.example.iotapp.core.AppConfig
import com.example.iotapp.core.UiState
import com.example.iotapp.data.AppContainer
import com.example.iotapp.data.mqtt.MqttManager
import com.example.iotapp.data.mqtt.MqttState
import com.example.iotapp.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

/**
 * 设置页：改服务器地址 / 设备 ID。
 *
 * 这是必须有的——手机热点重连后电脑 IP 会变，没有这个页面就要改代码重新编译。
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSettingsBinding.bind(view)
        val settings = AppContainer.settings

        // 设置已从底部 Tab 移到首页右上角，作为二级页压栈，返回键必须能退
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.etBaseUrl.setText(settings.baseUrl)
        binding.etDeviceId.setText(settings.deviceId)
        showCurrent(settings.baseUrl)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MqttManager.state.collect { renderMqtt(it) }
            }
        }

        binding.btnSave.setOnClickListener {
            settings.baseUrl = binding.etBaseUrl.text?.toString().orEmpty()
            settings.deviceId = binding.etDeviceId.text?.toString().orEmpty()

            // 地址变了必须重建 Repository，否则还在用旧地址的 Retrofit 实例
            AppContainer.resetRepository()

            // 设备 ID 变了要重新订阅主题，否则还在收旧设备的推送
            MqttManager.connect(settings.mqttUrl, settings.deviceId, settings.mqttClientId)

            binding.etBaseUrl.setText(settings.baseUrl)
            showCurrent(settings.baseUrl)
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()

            // 让所有页面重新拿新的 ViewModel / Repository
            requireActivity().recreate()
        }

        binding.btnTest.setOnClickListener {
            // 测试前先把输入框里的地址存下来，用新地址去连。
            // 只在地址真的变了才重建 Repository —— 否则会把首页「最后一次数据」缓存冲掉，
            // 表现为：进过设置页再回首页，设备离线时卡片只能显示 "--"。
            val before = settings.baseUrl
            settings.baseUrl = binding.etBaseUrl.text?.toString().orEmpty()
            if (settings.baseUrl != before) {
                AppContainer.resetRepository()
            }

            binding.btnTest.isEnabled = false
            binding.tvTestResult.text = getString(R.string.settings_testing)

            viewLifecycleOwner.lifecycleScope.launch {
                val result = AppContainer.repository().ping()
                binding.tvTestResult.text = when (result) {
                    is UiState.Success -> getString(R.string.settings_test_ok, settings.baseUrl)
                    is UiState.Error -> getString(R.string.settings_test_fail, result.message)
                    is UiState.Offline -> getString(R.string.state_offline)
                    UiState.Loading -> ""
                }
                binding.btnTest.isEnabled = true
            }
        }
    }

    /** 实时推送的连接状态，用来判断首页走的是 MQTT 还是 HTTP 兜底 */
    private fun renderMqtt(state: MqttState) {
        binding.tvMqtt.text = when (state) {
            is MqttState.Connected -> getString(R.string.mqtt_connected)
            MqttState.Connecting -> getString(R.string.mqtt_connecting)
            MqttState.Disconnected -> getString(R.string.mqtt_disconnected)
            is MqttState.Lost -> getString(R.string.mqtt_failed, state.reason)
        }
        binding.tvMqtt.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (state is MqttState.Connected) R.color.state_ok else R.color.state_bad,
            ),
        )
    }

    private fun showCurrent(baseUrl: String) {
        val healthz = baseUrl.trimEnd('/') + "/" + AppConfig.PATH_HEALTHZ
        binding.tvCurrent.text = getString(
            R.string.settings_current,
            baseUrl,
            AppContainer.settings.deviceId,
            AppContainer.settings.mqttUrl,
            healthz,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
