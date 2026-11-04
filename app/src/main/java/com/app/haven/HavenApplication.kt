package com.app.haven

import android.app.Application

// Created once when the app starts; holds the container that builds the data layer
class HavenApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
