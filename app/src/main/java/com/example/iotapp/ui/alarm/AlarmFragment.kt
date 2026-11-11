package com.example.iotapp.ui.alarm

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iotapp.R
import com.example.iotapp.core.UiState
import com.example.iotapp.databinding.FragmentAlarmBinding
import com.example.iotapp.model.AlarmListUi
import com.example.iotapp.ui.common.VmFactory
import kotlinx.coroutines.launch

/**
 * 告警页（任务书 §7.5）：列表 + 未读统计。
 *
 * 以二级页面形式从首页右上角进入，不属于底部导航的 4 个 Tab。
 * 点击单条即调用 `POST /alarms/{id}/ack` 标记已读（契约 §2.6）。
 */
class AlarmFragment : Fragment(R.layout.fragment_alarm) {

    private var _binding: FragmentAlarmBinding? = null
    private val binding get() = _binding!!

    private val vm: AlarmViewModel by viewModels { VmFactory() }
    private lateinit var adapter: AlarmAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAlarmBinding.bind(view)

        adapter = AlarmAdapter { vm.ack(it) }
        binding.rvAlarm.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAlarm.adapter = adapter

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.swipe.setOnRefreshListener { vm.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch { vm.events.collect { handleEvent(it) } }
            }
        }
    }

    private fun render(state: UiState<AlarmListUi>) {
        binding.swipe.isRefreshing = false

        when (state) {
            is UiState.Loading -> showBanner(getString(R.string.state_loading))

            is UiState.Success -> {
                bindList(state.data)
                hideBanner()
            }

            is UiState.Offline -> {
                // 和首页一致：有缓存就继续显示，但明确告诉用户这不是最新的
                val last = state.last
                if (last != null) {
                    bindList(last)
                    showBanner(getString(R.string.state_offline))
                } else {
                    bindList(AlarmListUi(emptyList(), null))
                    showBanner(getString(R.string.state_offline_no_data))
                }
            }

            is UiState.Error -> showBanner(getString(R.string.state_error, state.message))
        }
    }

    private fun bindList(data: AlarmListUi) {
        adapter.submit(data.items)

        val empty = data.items.isEmpty()
        binding.llEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvAlarm.visibility = if (empty) View.GONE else View.VISIBLE

        binding.tvUnread.text = if (data.unreadCount > 0) {
            getString(R.string.alarm_unread_count, data.unreadCount)
        } else {
            getString(R.string.alarm_all_read)
        }
    }

    private fun handleEvent(event: AlarmEvent) {
        val text = when (event) {
            AlarmEvent.AckOk -> getString(R.string.alarm_ack_done)
            is AlarmEvent.AckFail -> getString(
                R.string.alarm_ack_fail,
                event.detail ?: getString(R.string.state_offline),
            )
        }
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun showBanner(text: String) {
        binding.tvBanner.text = text
        binding.tvBanner.visibility = View.VISIBLE
    }

    private fun hideBanner() {
        binding.tvBanner.visibility = View.GONE
    }

    override fun onDestroyView() {
        binding.rvAlarm.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
