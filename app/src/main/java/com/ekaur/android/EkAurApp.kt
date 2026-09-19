package com.ekaur.android

import android.app.Application
import com.ekaur.android.di.AppContainer

class EkAurApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Installed first thing: without ADB, an uncaught exception is otherwise
        // invisible and just looks like the app quietly stopping.
        container.crashReporter.install()
    }
}
