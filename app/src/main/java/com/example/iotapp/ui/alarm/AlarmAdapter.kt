package com.example.iotapp.ui.alarm

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.iotapp.R
import com.example.iotapp.databinding.ItemAlarmBinding
import com.example.iotapp.model.AlarmUi

/**
 * 告警列表适配器。
 *
 * 只做「把已经拼好的文字塞进控件」，级别的颜色映射放在这里，
 * 因为颜色属于界面概念，不该出现在 UiModel 里。
 */
class AlarmAdapter(
    private val onItemClick: (AlarmUi) -> Unit,
) : RecyclerView.Adapter<AlarmAdapter.VH>() {

    private val items = mutableListOf<AlarmUi>()

    fun submit(list: List<AlarmUi>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AlarmUi) {
            val ctx = binding.root.context
            val color = ContextCompat.getColor(ctx, levelColor(item.level))

            binding.tvMetric.text = item.metricLabel
            binding.tvDetail.text = ctx.getString(R.string.alarm_detail, item.valueText, item.limitText)
            binding.tvTime.text = item.timeText

            binding.vLevel.backgroundTintList = ColorStateList.valueOf(color)
            binding.tvLevelTag.text = ctx.getString(levelLabel(item.level))
            binding.tvLevelTag.setTextColor(color)

            // 已读的整条变淡、去掉未读圆点，视觉上能一眼区分处理过没有
            binding.vUnread.visibility = if (item.ack) View.GONE else View.VISIBLE
            binding.root.alpha = if (item.ack) 0.55f else 1f

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private fun levelColor(level: String): Int = when (level) {
        "danger" -> R.color.level_danger
        "warn" -> R.color.level_warn
        else -> R.color.level_info
    }

    private fun levelLabel(level: String): Int = when (level) {
        "danger" -> R.string.alarm_level_danger
        "warn" -> R.string.alarm_level_warn
        else -> R.string.alarm_level_info
    }
}
