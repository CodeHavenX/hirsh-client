package com.cramsan.hirsh.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { CIO }
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("com/cramsan/hirsh")) }
}
