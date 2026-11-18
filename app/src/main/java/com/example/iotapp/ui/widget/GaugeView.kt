package com.example.iotapp.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.iotapp.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 半圆仪表盘（任务书「实时数据、仪表盘、数据卡片、状态图标」里的仪表盘）。
 *
 * 为什么在数字卡片旁边还要画这个：卡片只能回答「是多少」，
 * 指针位置回答的是「这个值在量程里算高还是低」。
 * 再加上 /config 给的阈值刻线，一眼能看出离告警还有多远。
 *
 * 量程与阈值全部由 /config 的 thresholds 驱动（min / max / default），
 * 不在 XML 里写死，后端改范围这里自动跟着变。
 *
 * 只负责画，不做任何判定：数值为 null（离线、无缓存）时只画轨道和刻度。
 */
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val arcWidth = dp(11f)
    private val needleWidth = dp(3f)
    private val pivotRadius = dp(4.5f)
    private val labelSize = 10f * resources.displayMetrics.scaledDensity

    /** 量程，默认 0~100，拿到 /config 后会被 setRange 覆盖 */
    private var minValue = 0f
    private var maxValue = 100f

    /** 当前值；null 表示没有可用数据 */
    private var value: Float? = null

    /** 告警阈值；null 表示后端没给，不画刻线 */
    private var threshold: Float? = null

    private val arcColor: Int = attrs?.let { set ->
        val ta = context.obtainStyledAttributes(set, R.styleable.GaugeView)
        val c = ta.getColor(
            R.styleable.GaugeView_gaugeArcColor,
            ContextCompat.getColor(context, R.color.gauge_temp),
        )
        ta.recycle()
        c
    } ?: ContextCompat.getColor(context, R.color.gauge_temp)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = arcWidth
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.gauge_track)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = arcWidth
        strokeCap = Paint.Cap.ROUND
        color = arcColor
    }

    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.gauge_threshold)
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = needleWidth
        strokeCap = Paint.Cap.ROUND
        color = arcColor
    }

    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = arcColor
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = labelSize
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_secondary_hint)
    }

    /** [min] / [max] 取自 /config 的 thresholds[key]，刻度两端会画出来 */
    fun setRange(min: Float, max: Float) {
        if (minValue == min && maxValue == max) return
        minValue = min
        maxValue = max
        invalidate()
    }

    /** 告警阈值，对应 /config 的 thresholds[key].default */
    fun setThreshold(t: Float?) {
        if (threshold == t) return
        threshold = t
        invalidate()
    }

    /** 传 null 表示当前没有可用数据，只保留轨道 */
    fun setValue(v: Float?) {
        if (value == v) return
        value = v
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        if (hMode == MeasureSpec.EXACTLY) {
            setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec))
        } else {
            // 半圆 + 底部刻度文字，宽高比约 1 : 0.68
            setMeasuredDimension(w, (w * 0.68f).toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()

        val labelHeight = labelSize + dp(4f)
        val cy = bottom - labelHeight
        val cx = (left + right) / 2f
        val radius = min((right - left) / 2f, cy - paddingTop) - arcWidth / 2f
        if (radius <= 0f) return

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 180f, 180f, false, trackPaint)

        val ratio = ratioOf(value)
        if (ratio != null) {
            canvas.drawArc(rect, 180f, 180f * ratio, false, arcPaint)
        }

        ratioOf(threshold)?.let { drawThresholdTick(canvas, cx, cy, radius, it) }
        ratio?.let { drawNeedle(canvas, cx, cy, radius, it) }

        drawRangeLabels(canvas, cx, cy, radius, bottom)
    }

    private fun ratioOf(v: Float?): Float? {
        if (v == null || maxValue <= minValue) return null
        return ((v - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    }

    /** 阈值刻线：从弧的内侧伸到外侧，像仪表上的红区起点 */
    private fun drawThresholdTick(canvas: Canvas, cx: Float, cy: Float, r: Float, ratio: Float) {
        val rad = Math.toRadians((180f + 180f * ratio).toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val inner = r - arcWidth * 1.2f
        val outer = r + arcWidth * 0.9f
        canvas.drawLine(
            cx + inner * c, cy + inner * s,
            cx + outer * c, cy + outer * s,
            thresholdPaint,
        )
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, r: Float, ratio: Float) {
        val rad = Math.toRadians((180f + 180f * ratio).toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val tip = r - arcWidth * 1.7f
        canvas.drawLine(cx, cy, cx + tip * c, cy + tip * s, needlePaint)
        canvas.drawCircle(cx, cy, pivotRadius, pivotPaint)
    }

    private fun drawRangeLabels(canvas: Canvas, cx: Float, cy: Float, r: Float, bottom: Float) {
        val baseline = bottom - dp(1f)
        canvas.drawText(formatEdge(minValue), cx - r, baseline, labelPaint)
        canvas.drawText(formatEdge(maxValue), cx + r, baseline, labelPaint)
    }

    /** 15.0 显示成 15，15.5 保留一位 —— 刻度端点不需要多余的小数 */
    private fun formatEdge(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else String.format(Locale.US, "%.1f", v)
}
