package com.cramsan.hirsh.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises the *actual* production `HttpClient` config (real CIO engine, the exact plugin stack
 * from `di/AppModule.kt`) against a real, separately-running instance of this project's backend
 * (see [ApiConfig]'s doc comment) -- never `ktor-client-mock`. See
 * `.claude/skills/_shared/real-backend-check.md`: HISS-604's bodyless-401 handling bug shipped
 * past 100% green mock-based tests and was only caught this way.
 *
 * Skips (doesn't fail) when no backend is reachable -- start it (see that repo's README), then
 * `./gradlew :composeApp:desktopIntegrationTest`. Deliberately not part of verifyLocal/verifyCi.
 */
class ApiErrorValidatorRealBackendTest {

    @Before
    fun checkBackendReachable() {
        val reachable = try {
            (URI("${ApiConfig.BASE_URL}/actuator/health").toURL().openConnection() as HttpURLConnection)
                .apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                .responseCode
            true
        } catch (e: Exception) {
            false
        }
        assumeTrue(
            "No backend reachable at ${ApiConfig.BASE_URL} -- start it (see that repo's README) " +
                "before running this suite.",
            reachable,
        )
    }

    private fun realHttpClient(onUnauthorized: suspend () -> Unit = {}) = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(Logging) { level = LogLevel.INFO }
        install(HttpCookies)
        install(XsrfHeaderPlugin)
        installApiErrorValidator(onUnauthorized)
        defaultRequest { url(ApiConfig.BASE_URL) }
    }

    @Test
    fun unauthenticatedRequestMapsToApiErrorUnauthorizedAndInvokesOnUnauthorized() = runTest {
        var onUnauthorizedCalls = 0
        val client = realHttpClient(onUnauthorized = { onUnauthorizedCalls++ })

        val exception = assertFailsWith<ApiException> { client.get("/actuator/health") }

        assertEquals(ApiError.Unauthorized, exception.error)
        assertEquals(1, onUnauthorizedCalls)
    }
}
