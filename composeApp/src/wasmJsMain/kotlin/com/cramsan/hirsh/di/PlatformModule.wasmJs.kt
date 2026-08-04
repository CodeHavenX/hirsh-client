package com.cramsan.hirsh.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js
import kotlinx.browser.localStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { Js }
    single<Settings> { StorageSettings(localStorage) }
}
