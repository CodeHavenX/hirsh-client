package com.cramsan.hirsh.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val XSRF_TOKEN_HEADER = "X-XSRF-TOKEN"
private const val TEST_URL = "http://localhost/api/test"

class CsrfInterceptorTest {

    private fun buildClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(HttpCookies)
        install(XsrfHeaderPlugin)
    }

    @Test
    fun getRequestNeverGetsHeaderEvenWithCookiePresent() = runTest {
        val engine = MockEngine { request ->
            respond("", headers = headersOf(HttpHeaders.SetCookie, "XSRF-TOKEN=test-token; Path=/"))
        }
        val client = buildClient(engine)

        // First response hands out the XSRF-TOKEN cookie (mirrors a real login response).
        client.post(TEST_URL)
        client.get(TEST_URL)

        val lastRequest = engine.requestHistory.last()
        assertNull(lastRequest.headers[XSRF_TOKEN_HEADER])
    }

    @Test
    fun mutatingRequestWithCookiePresentGetsHeaderSetToCookieValue() = runTest {
        val engine = MockEngine { request ->
            respond("", headers = headersOf(HttpHeaders.SetCookie, "XSRF-TOKEN=test-token; Path=/"))
        }
        val client = buildClient(engine)

        client.post(TEST_URL)
        client.post(TEST_URL)

        val lastRequest = engine.requestHistory.last()
        assertEquals("test-token", lastRequest.headers[XSRF_TOKEN_HEADER])
    }

    @Test
    fun mutatingRequestWithNoCookieSendsNoHeaderAtAll() = runTest {
        // No Set-Cookie in the response -- mirrors the login request itself, before any
        // XSRF-TOKEN cookie exists in the jar.
        val engine = MockEngine { request -> respondOk() }
        val client = buildClient(engine)

        client.post(TEST_URL)

        val lastRequest = engine.requestHistory.last()
        assertNull(lastRequest.headers[XSRF_TOKEN_HEADER])
    }
}
