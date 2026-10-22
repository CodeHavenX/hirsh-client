package com.cramsan.hirsh.di

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { Darwin }
    // Darwin has a real cookie jar via HttpCookies with no engine-specific config needed.
    single<HttpClientEngineTuning> { {} }
}
