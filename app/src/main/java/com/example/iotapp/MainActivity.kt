package com.example.iotapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.example.iotapp.databinding.ActivityMainBinding
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

    private companion object {
        const val KEY_ITEM = "current_item"
    }
}
