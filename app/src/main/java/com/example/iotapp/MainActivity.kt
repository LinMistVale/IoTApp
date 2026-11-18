package com.example.iotapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.example.iotapp.databinding.ActivityMainBinding
import com.example.iotapp.ui.alarm.AlarmFragment
import com.example.iotapp.ui.chat.ChatFragment
import com.example.iotapp.ui.control.ControlFragment
import com.example.iotapp.ui.history.HistoryFragment
import com.example.iotapp.ui.home.HomeFragment

/**
 * 单 Activity + 底部导航。
 * 页面之间用 Fragment 切换，数据状态保存在各自的 ViewModel 里。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentItem = R.id.nav_home

    /**
     * Android 13+ 的通知权限。
     *
     * manifest 里声明只是"我可能会用"，不申请到就是发不出去 —— 而且不报错，
     * 表现为「代码没错但通知就是不弹」，是最容易卡住的一个点。
     * 用户拒绝也能继续用，只是不弹通知。
     */
    private val requestNotifyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 静默降级 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentItem = savedInstanceState?.getInt(KEY_ITEM, R.id.nav_home) ?: R.id.nav_home
        binding.bottomNav.selectedItemId = currentItem
        if (savedInstanceState == null) {
            show(currentItem)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId != currentItem) {
                currentItem = item.itemId
                show(item.itemId)
            }
            true
        }

        ensureNotificationPermission()

        // 从通知点进来：直接落在告警页
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALARM, false) != true) return
        // 消费掉，避免旋转屏幕重建时又跳一次
        intent.removeExtra(EXTRA_OPEN_ALARM)
        openSecondary(AlarmFragment())
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun show(itemId: Int) {
        // 切 Tab 前先清掉二级页（告警 / 设置），否则返回键会退到已经不显示的页面
        supportFragmentManager.popBackStackImmediate(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE,
        )

        val fragment: Fragment = when (itemId) {
            R.id.nav_history -> HistoryFragment()
            R.id.nav_control -> ControlFragment()
            R.id.nav_chat -> ChatFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, fragment)
        }
    }

    /**
     * 首页右上角的告警 / 设置入口（任务书 §7.1）。
     *
     * 做成压栈的二级页面：底部导航保持在原位，返回键退回首页。
     */
    fun openSecondary(fragment: Fragment) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, fragment)
            addToBackStack(null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ITEM, currentItem)
    }

    companion object {
        /** 通知点击时带过来的标记，见 [com.example.iotapp.notify.AlarmNotifier] */
        const val EXTRA_OPEN_ALARM = "extra_open_alarm"

        private const val KEY_ITEM = "current_item"
    }
}
