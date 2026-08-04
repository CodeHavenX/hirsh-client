package com.cramsan.hirsh

import android.app.Application
import com.cramsan.hirsh.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class HirshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@HirshApplication)
        }
    }
}
