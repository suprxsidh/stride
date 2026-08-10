package com.suprxsidh.deficit

import android.app.Application
import com.suprxsidh.deficit.data.AppContainer

class DeficitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
