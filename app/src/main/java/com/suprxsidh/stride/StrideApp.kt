package com.suprxsidh.stride

import android.app.Application
import com.suprxsidh.stride.data.AppContainer

class StrideApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
