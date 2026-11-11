package com.example.iotapp.ui.history

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.iotapp.R
import com.example.iotapp.core.UiState
import com.example.iotapp.databinding.FragmentHistoryBinding
import com.example.iotapp.model.HistoryUi
import com.example.iotapp.ui.common.VmFactory
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch

/**
 * 历史曲线：折线图 + 指标切换 + 时间范围。
 * 降采样由后端做（bucket=auto），App 只负责画。
 */
class HistoryFragment : Fragment(R.layout.fragment_history) {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val vm: HistoryViewModel by viewModels { VmFactory() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHistoryBinding.bind(view)

        setupChart()

        binding.toggleMetric.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            vm.select(metric = when (checkedId) {
                R.id.btnTemp -> HistoryViewModel.METRIC_TEMP
                R.id.btnHumi -> HistoryViewModel.METRIC_HUMI
                else -> HistoryViewModel.METRIC_LUX
            })
        }
        binding.toggleRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            vm.select(hours = if (checkedId == R.id.btn1h) HistoryViewModel.RANGE_1H else HistoryViewModel.RANGE_24H)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun setupChart() = with(binding.chart) {
        description.isEnabled = false
        legend.isEnabled = false
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        axisRight.isEnabled = false
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        axisLeft.setDrawGridLines(true)
        setNoDataText(getString(R.string.history_empty))
    }

    private fun render(state: UiState<HistoryUi>) {
        when (state) {
            is UiState.Loading -> binding.tvHint.text = getString(R.string.state_loading)

            is UiState.Success -> {
                val ui = state.data
                binding.tvHint.text = if (ui.downsampled) {
                    getString(R.string.history_downsampled, ui.points.size)
                } else {
                    getString(R.string.history_points, ui.points.size)
                }
                if (ui.points.isEmpty()) {
                    binding.chart.clear()
                    binding.chart.invalidate()
                } else {
                    drawChart(ui)
                }
            }

            is UiState.Offline -> {
                binding.tvHint.text = getString(R.string.state_offline)
            }

            is UiState.Error -> {
                binding.tvHint.text = getString(R.string.state_error, state.message)
            }
        }
    }

    private fun drawChart(ui: HistoryUi) {
        val entries = ui.points.mapIndexed { index, point -> Entry(index.toFloat(), point.value) }
        val labels = ui.points.map { com.example.iotapp.util.TimeUtil.clock(it.ts) }

        val dataSet = LineDataSet(entries, ui.metricLabel).apply {
            color = Color.parseColor("#534AB7")
            setCircleColor(Color.parseColor("#534AB7"))
            circleRadius = 2.5f
            lineWidth = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#534AB7")
            fillAlpha = 40
        }

        binding.chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.chart.xAxis.granularity = 1f
        binding.chart.data = LineData(dataSet)
        binding.chart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
