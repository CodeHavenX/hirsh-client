package com.cramsan.hirsh.di

import io.ktor.client.engine.HttpClientEngineConfig

/**
 * Per-platform [io.ktor.client.HttpClient] engine tuning that can't be expressed generically in
 * [AppModule.kt]'s shared `HttpClient` builder, because [io.ktor.client.engine.HttpClientEngineFactory]
 * is injected there as a type-erased `HttpClientEngineFactory<*>` (see [platformModule]) -- so
 * `engine { ... }`'s block only sees the base [HttpClientEngineConfig], not an engine-specific
 * subtype like `JsClientEngineConfig`.
 *
 * Every platform module provides one binding for this. It's a no-op everywhere except wasmJs,
 * which needs it to set the fetch `credentials` mode -- without that, the browser never sends or
 * stores `JSESSIONID`/`XSRF-TOKEN` on a cross-origin request at all, regardless of any Ktor
 * cookie plugin. See `PlatformModule.wasmJs.kt`.
 */
typealias HttpClientEngineTuning = HttpClientEngineConfig.() -> Unit
