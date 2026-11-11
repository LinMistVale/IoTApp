package com.example.iotapp.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间工具：后端给的是 Unix 秒，这里统一转成人能读的文字。 */
object TimeUtil {

    /** "刚刚" / "3 秒前" / "5 分钟前" / "2 小时前" / "3 天前" */
    fun friendly(tsSec: Long, nowSec: Long = nowSec()): String {
        val diff = nowSec - tsSec
        return when {
            diff <= 0 -> "刚刚"
            diff < 5 -> "刚刚"
            diff < 60 -> "${diff} 秒前"
            diff < 3600 -> "${diff / 60} 分钟前"
            diff < 86_400 -> "${diff / 3600} 小时前"
            else -> "${diff / 86_400} 天前"
        }
    }

    /** 图表 X 轴标签：HH:mm */
    fun clock(tsSec: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tsSec * 1000))

    /** 带日期的时间：MM-dd HH:mm */
    fun dateTime(tsSec: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(tsSec * 1000))

    fun nowSec(): Long = System.currentTimeMillis() / 1000
}
