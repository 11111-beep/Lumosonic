package com.example.lumosonic

import android.app.Application
import android.content.Intent
import com.example.lumosonic.ipc.MusicService

class App : Application() {
    companion object {
        lateinit var instance: App
        private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 启动服务
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
    }
}