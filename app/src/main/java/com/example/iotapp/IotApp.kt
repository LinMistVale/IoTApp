package com.example.iotapp

import android.app.Application
import com.example.iotapp.data.AppContainer

/** 应用入口：初始化依赖容器（读取本地保存的服务器地址）。 */
class IotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
