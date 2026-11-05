package com.example.iotapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.iotapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 动态文案统一走 getString(R.string.xxx)，不要写死中文字面量
        binding.tvHello.text = getString(R.string.environment_ready)
    }
}
