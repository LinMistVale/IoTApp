package com.example.iotapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.iotapp.databinding.ActivityMainBinding
import com.example.iotapp.ui.chat.ChatFragment
import com.example.iotapp.ui.control.ControlFragment
import com.example.iotapp.ui.history.HistoryFragment
import com.example.iotapp.ui.home.HomeFragment
import com.example.iotapp.ui.settings.SettingsFragment

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
        val fragment: Fragment = when (itemId) {
            R.id.nav_history -> HistoryFragment()
            R.id.nav_control -> ControlFragment()
            R.id.nav_chat -> ChatFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, fragment)
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
