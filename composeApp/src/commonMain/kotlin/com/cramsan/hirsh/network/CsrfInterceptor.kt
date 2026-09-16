package com.cramsan.hirsh.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.plugins.cookies.get
import io.ktor.http.HttpMethod

private const val XSRF_TOKEN_COOKIE = "XSRF-TOKEN"
private const val XSRF_TOKEN_HEADER = "X-XSRF-TOKEN"

/**
 * Sets [XSRF_TOKEN_HEADER] from the [XSRF_TOKEN_COOKIE] cookie on every mutating request, per the
 * backend's CSRF double-submit-cookie scheme -- Ktor's [io.ktor.client.plugins.cookies.HttpCookies]
 * plugin sends the cookie itself automatically, but doesn't mirror it into this separate header.
 *
 * No explicit login exemption: before a successful login there's no [XSRF_TOKEN_COOKIE] cookie in
 * the jar yet (login's own response is what hands it out), so the lookup below is naturally absent
 * and no header gets set -- login is exempted by the cookie's absence, not a hardcoded endpoint
 * path this client has no other reason to know about.
 */
val XsrfHeaderPlugin = createClientPlugin("XsrfHeaderPlugin") {
    val httpClient = client
    onRequest { request, _ ->
        if (request.method != HttpMethod.Get) {
            httpClient.cookies(request.url.build())[XSRF_TOKEN_COOKIE]?.let { cookie ->
                request.headers.append(XSRF_TOKEN_HEADER, cookie.value)
            }
        }
    }
}
