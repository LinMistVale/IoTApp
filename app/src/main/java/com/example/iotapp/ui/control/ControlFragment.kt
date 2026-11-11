package com.example.iotapp.ui.control

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.iotapp.R
import com.example.iotapp.core.UiState
import com.example.iotapp.databinding.FragmentControlBinding
import com.example.iotapp.model.DeviceConfigUi
import com.example.iotapp.model.RangeUi
import com.example.iotapp.ui.common.VmFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 控制页：**所有控件都由 /config 动态生成**，XML 里不写死任何范围或选项。
 * 后端新增一个命令，只要 type 是已知的六种，这里会自动多出一个控件。
 */
class ControlFragment : Fragment(R.layout.fragment_control) {

    private var _binding: FragmentControlBinding? = null
    private val binding get() = _binding!!

    private val vm: ControlViewModel by viewModels { VmFactory() }

    /** 构建控件期间会触发 onCheckedChanged / onItemSelected，用它挡住误下发 */
    private var building = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentControlBinding.bind(view)

        binding.btnReload.setOnClickListener { vm.loadConfig(forceRefresh = true) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.config.collect { render(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.message.collect { text ->
                    Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun render(state: UiState<DeviceConfigUi>) {
        binding.progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE

        when (state) {
            is UiState.Success -> {
                binding.tvHint.visibility = View.GONE
                buildControls(state.data)
            }
            is UiState.Loading -> binding.tvHint.visibility = View.GONE
            is UiState.Offline -> showHint(getString(R.string.state_offline))
            is UiState.Error -> showHint(getString(R.string.state_error, state.message))
        }
    }

    private fun showHint(text: String) {
        binding.tvHint.text = text
        binding.tvHint.visibility = View.VISIBLE
    }

    private fun buildControls(config: DeviceConfigUi) {
        building = true
        binding.container.removeAllViews()

        config.commands.forEach { spec ->
            when (spec.type) {
                "switch" -> addSwitch(config)
                "threshold" -> addThresholds(config, spec.label)
                "select" -> addInterval(config, spec.label)
                "slider" -> addVolume(config, spec.label)
                "text" -> addSpeak(spec.label)
                "radio" -> addModes(config, spec.label)
                else -> Unit // 未知类型直接忽略，不崩
            }
        }

        building = false
    }

    // ---------- 各类控件 ----------

    private fun addSwitch(config: DeviceConfigUi) {
        binding.container.addView(title(getString(R.string.control_led)))
        val switch = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.control_led_hint)
        }
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (!building) vm.setLed(isChecked)
        }
        binding.container.addView(switch)
    }

    private fun addThresholds(config: DeviceConfigUi, label: String) {
        config.metrics.forEach { metric ->
            val range = config.thresholds[metric.key] ?: return@forEach
            addSlider(
                title = "$label · ${metric.label}",
                range = range,
                unit = metric.unit,
                onCommit = { value -> vm.setThreshold(metric.key, value) },
            )
        }
    }

    private fun addInterval(config: DeviceConfigUi, label: String) {
        binding.container.addView(title(label))
        val spinner = Spinner(requireContext())
        val labels = config.intervalOptions.map { "$it 秒" }
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )
        spinner.setSelection(config.intervalOptions.indexOf(config.intervalDefault).coerceAtLeast(0))

        var firstFire = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // 首次回调是初始选中项，跳过，避免一进页面就下发命令
                if (firstFire) {
                    firstFire = false
                    return
                }
                if (!building) vm.setInterval(config.intervalOptions[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.container.addView(spinner)
    }

    private fun addVolume(config: DeviceConfigUi, label: String) {
        addSlider(
            title = label,
            range = config.volume,
            unit = "",
            onCommit = { value -> vm.setVolume(value.toInt()) },
        )
    }

    private fun addSpeak(label: String) {
        binding.container.addView(title(label))
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.control_speak_hint)
        }
        val input = TextInputEditText(requireContext()).apply {
            maxLines = 2
        }
        inputLayout.addView(input)

        val button = MaterialButton(requireContext()).apply {
            text = getString(R.string.control_send)
        }
        button.setOnClickListener {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), R.string.control_speak_empty, Toast.LENGTH_SHORT).show()
            } else {
                vm.speak(text)
                input.setText("")
            }
        }

        binding.container.addView(inputLayout)
        binding.container.addView(button)
    }

    private fun addModes(config: DeviceConfigUi, label: String) {
        binding.container.addView(title(label))
        val group = RadioGroup(requireContext()).apply { orientation = RadioGroup.HORIZONTAL }
        config.modes.forEachIndexed { index, mode ->
            val radio = RadioButton(requireContext()).apply {
                id = 1000 + index
                text = mode.label
            }
            group.addView(radio)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            if (building) return@setOnCheckedChangeListener
            val mode = config.modes.getOrNull(checkedId - 1000) ?: return@setOnCheckedChangeListener
            vm.setMode(mode.value)
        }
        binding.container.addView(group)
    }

    // ---------- 通用 ----------

    private fun addSlider(title: String, range: RangeUi, unit: String, onCommit: (Float) -> Unit) {
        binding.container.addView(title(title))

        val valueLabel = TextView(requireContext()).apply {
            text = format(range.default, unit, range.step)
            textSize = 13f
            gravity = Gravity.END
        }

        val slider = Slider(requireContext()).apply {
            valueFrom = range.min
            valueTo = range.max
            // stepSize 必须能整除区间，否则 Slider 会抛异常；给个兜底
            stepSize = if (range.step > 0f) range.step else 0.1f
            value = range.default.coerceIn(range.min, range.max)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        slider.addOnChangeListener { _, value, _ -> valueLabel.text = format(value, unit, range.step) }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            // 手指抬起才下发，避免拖动过程中刷屏请求
            override fun onStopTrackingTouch(slider: Slider) {
                if (!building) onCommit(slider.value)
            }
        })

        binding.container.addView(slider)
        binding.container.addView(valueLabel)
    }

    private fun title(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(6))
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
    }

    /** 按 step 决定小数位：step≥1 显示整数（音量 60 而不是 60.0），否则保留对应位数 */
    private fun format(value: Float, unit: String, step: Float): String {
        val decimals = when {
            step >= 1f -> 0
            step >= 0.1f -> 1
            else -> 2
        }
        return String.format(Locale.US, "%.${decimals}f%s", value, unit)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
