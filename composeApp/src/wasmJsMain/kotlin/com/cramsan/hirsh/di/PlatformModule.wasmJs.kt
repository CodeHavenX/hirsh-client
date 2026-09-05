package com.cramsan.hirsh.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js
import io.ktor.client.engine.js.JsClientEngineConfig
import kotlinx.browser.localStorage
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.js.toJsString

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { Js }
    single<Settings> { StorageSettings(localStorage) }
    single<HttpClientEngineTuning> {
        {
            // The cast is safe: this engine-tuning binding only ever runs against the Js
            // engine's own config (see the single<HttpClientEngineFactory<*>> above), just
            // erased to the base HttpClientEngineConfig at AppModule.kt's shared call site.
            //
            // Without this, the browser's fetch() call defaults `credentials` to
            // "same-origin", so JSESSIONID/XSRF-TOKEN are never sent or stored on a
            // cross-origin request (client and backend are always different origins in this
            // app -- see ApiConfig.BASE_URL) -- regardless of installing HttpCookies. Requires
            // Ktor 3.2+; JsClientEngineConfig.configureRequest doesn't exist before that.
            @Suppress("UNCHECKED_CAST")
            (this as JsClientEngineConfig).configureRequest {
                // Kotlin/Wasm's JS interop has no `dynamic` -- RequestInit.credentials is
                // JsAny?, not String, unlike the Kotlin/JS target's version of this same class.
                credentials = "include".toJsString()
            }
        }
    }
}
