package com.cramsan.hirsh.di

import com.cramsan.hirsh.util.FileSaver
import com.cramsan.hirsh.util.UnsupportedFileSaver
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { OkHttp }
    // OkHttp has a real cookie jar via HttpCookies with no engine-specific config needed.
    single<HttpClientEngineTuning> { {} }
    single<FileSaver> { UnsupportedFileSaver() }
}
