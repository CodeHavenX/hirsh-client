package com.cramsan.hirsh.di

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { OkHttp }
    single<Settings> {
        SharedPreferencesSettings(androidContext().getSharedPreferences("hirsh_prefs", Context.MODE_PRIVATE))
    }
}
