package com.suprxsidh.stride

import android.app.Application
import com.suprxsidh.stride.data.AppContainer
import com.suprxsidh.stride.reminders.NotificationChannels

class StrideApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Feature E (completeness pass, spec §6): channels must exist before any reminder can
        // post to them; creating on every app start is safe/idempotent (a no-op below API 26,
        // and NotificationManagerCompat.createNotificationChannelsCompat updates in place above it).
        NotificationChannels.createAll(this)
    }
}
