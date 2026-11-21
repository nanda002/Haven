package com.app.haven

import android.app.Application
import com.app.haven.notifications.createNotificationChannels

//Created once when the app starts; holds the container that builds the data layer
class HavenApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }
}
