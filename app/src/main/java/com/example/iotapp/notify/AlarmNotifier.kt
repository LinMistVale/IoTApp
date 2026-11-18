package com.example.iotapp.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.iotapp.MainActivity
import com.example.iotapp.R
import com.example.iotapp.model.AlarmUi

/**
 * 告警通知（任务书 §7.5「新告警发本地通知」）。
 *
 * 三个必须注意的点：
 * 1. **Android 13+ 要运行时权限**。`POST_NOTIFICATIONS` 在 manifest 里只是"声明"，
 *    没申请到权限时 `notify()` 会被静默丢弃，表现出来就是"代码没错但就是不弹"。
 * 2. **渠道必须在 Android 8+ 建好**，否则通知不会显示。
 * 3. **每条用不同的通知 id**，否则后一条会覆盖前一条，用户只看到最后一条。
 */
object AlarmNotifier {

    const val CHANNEL_ID = "iot_alarm"

    private const val NOTIFY_ID_MOD = 100_000

    @Volatile
    private var channelReady = false

    fun ensureChannel(context: Context) {
        if (channelReady) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_alarm),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notify_channel_alarm_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
        channelReady = true
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun notifyAlarm(context: Context, alarm: AlarmUi, alarmId: Long) {
        if (!canNotify(context)) return
        ensureChannel(context)

        // 点通知直接进告警页：MainActivity 读这个 extra 后 openSecondary(AlarmFragment())
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ALARM, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            (alarmId % NOTIFY_ID_MOD).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.notify_alarm_title, alarm.metricLabel))
            .setContentText(
                context.getString(R.string.notify_alarm_text, alarm.valueText, alarm.limitText),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify((alarmId % NOTIFY_ID_MOD).toInt(), notification)
        } catch (e: SecurityException) {
            // 权限在这一刻被撤销，忽略即可
        }
    }
}
