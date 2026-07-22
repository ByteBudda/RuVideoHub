package com.example

import android.app.Application
import com.example.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SleekVideoHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@SleekVideoHubApplication)
                modules(appModule)
            }
        }
    }
}
