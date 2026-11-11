package com.example.iotapp.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.iotapp.MainActivity
import com.example.iotapp.R
import com.example.iotapp.core.UiState
import com.example.iotapp.data.AppContainer
import com.example.iotapp.databinding.FragmentHomeBinding
import com.example.iotapp.model.TelemetryUi
import com.example.iotapp.ui.alarm.AlarmFragment
import com.example.iotapp.ui.common.VmFactory
import com.example.iotapp.ui.settings.SettingsFragment
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch

/** 首页：只做渲染，四类状态各自处理（Loading / Success / Offline / Error）。 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by viewModels { VmFactory() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)

        binding.tvDevice.text = vm.deviceId
        binding.swipe.setOnRefreshListener { vm.refresh() }
        binding.btnRetry.setOnClickListener { vm.refresh() }

        // 右上角两个入口：告警（带未读角标）、设置。都在 MainActivity 里压栈成二级页
        binding.btnAlarm.setOnClickListener {
            (activity as? MainActivity)?.openSecondary(AlarmFragment())
        }
        binding.btnSettings.setOnClickListener {
            (activity as? MainActivity)?.openSecondary(SettingsFragment())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从告警页返回后角标要立刻反映最新未读数，所以放 onResume 而不是 onViewCreated
        refreshBadge()
    }

    /**
     * 未读角标（任务书 §7.1「右上角：告警入口 + 未读角标」）。
     *
     * 契约 v1.1 的 /alarms 不返回 unread 字段，只能拉列表自己数，
     * 所以这里请求一次 limit=50（内网毫秒级）。
     * 请求失败时保持原角标不动 —— 否则离线一进首页角标就被清零，等于谎报「没有未读」。
     */
    private fun refreshBadge() {
        val b = _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val state = AppContainer.repository().alarms(vm.deviceId).last()
            if (state !is UiState.Success) return@launch

            val count = state.data.unreadCount
            b.tvBadge.text = if (count > 99) {
                getString(R.string.alarm_badge_overflow)
            } else {
                count.toString()
            }
            b.tvBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    private fun render(state: UiState<TelemetryUi>) {
        binding.swipe.isRefreshing = false

        when (state) {
            is UiState.Loading -> {
                showBanner(getString(R.string.state_loading), showRetry = false)
            }

            is UiState.Success -> {
                bind(state.data)
                if (state.data.stale) {
                    showBanner(getString(R.string.state_stale), showRetry = true)
                } else {
                    hideBanner()
                }
            }

            is UiState.Offline -> {
                val last = state.last
                if (last != null) {
                    // 缓存里 online 当时是 true，但此刻确实连不上——状态必须显示「离线」，
                    // 否则顶部写「在线」、横幅写「设备离线」自相矛盾
                    bind(last, reachable = false)
                    showBanner(getString(R.string.state_offline), showRetry = true)
                } else {
                    // 没有任何缓存（例如刚改过服务器地址就离线）：别谎称"显示的是最后一次数据"
                    clearValues()
                    showBanner(getString(R.string.state_offline_no_data), showRetry = true)
                }
            }

            is UiState.Error -> {
                showBanner(getString(R.string.state_error, state.message), showRetry = true)
            }
        }
    }

    /** 没有任何可用数据时的占位（离线且无缓存） */
    private fun clearValues() {
        val na = getString(R.string.placeholder)
        binding.tvTemp.text = na
        binding.tvHumi.text = na
        binding.tvLux.text = na
        binding.tvUpdated.text = getString(R.string.updated_placeholder)
        binding.tvOnline.text = getString(R.string.offline)
        binding.tvOnline.setTextColor(ContextCompat.getColor(requireContext(), R.color.state_bad))
        binding.cardTemp.alpha = 0.45f
        binding.cardHumi.alpha = 0.45f
        binding.cardLux.alpha = 0.45f
    }

    /**
     * @param reachable 本次请求是否成功。离线兜底时传 false —— 即使缓存里写的是在线，
     *                  此刻设备已经连不上，状态必须显示「离线」。
     */
    private fun bind(data: TelemetryUi, reachable: Boolean = true) {
        binding.tvTemp.text = data.tempText
        binding.tvHumi.text = data.humiText
        binding.tvLux.text = data.luxText
        binding.tvUpdated.text = getString(R.string.updated_at, data.updatedText)

        val online = data.online && reachable
        binding.tvOnline.text = getString(if (online) R.string.online else R.string.offline)
        binding.tvOnline.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (online) R.color.state_ok else R.color.state_bad
            )
        )

        // 数据陈旧 / 本次没连上时整组卡片变灰，提示「别当实时值看」
        val alpha = if (data.stale || !reachable) 0.45f else 1f
        binding.cardTemp.alpha = alpha
        binding.cardHumi.alpha = alpha
        binding.cardLux.alpha = alpha
    }

    private fun showBanner(text: String, showRetry: Boolean) {
        binding.tvBanner.text = text
        binding.tvBanner.visibility = View.VISIBLE
        binding.btnRetry.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun hideBanner() {
        binding.tvBanner.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
