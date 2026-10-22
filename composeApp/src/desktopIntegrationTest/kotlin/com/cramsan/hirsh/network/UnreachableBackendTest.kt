package com.cramsan.hirsh.network

import com.cramsan.hirsh.repository.KtorPatientRepository
import com.cramsan.hirsh.ui.components.LoadError
import com.cramsan.hirsh.ui.components.toLoadError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertEquals

/**
 * [toLoadError] reads "not an [ApiException]" as "no response arrived" (HISS-663). The unit tests
 * can only fake that with a plain exception; this checks what the real CIO engine and the
 * production plugin stack actually throw when nothing is listening. Needs no backend, so it runs
 * even when the rest of this suite skips.
 */
class UnreachableBackendTest {

    @Test
    fun refusedConnectionReadsAsUnreachable() = runTest {
        val closedPort = ServerSocket(0).use { it.localPort }
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            install(HttpCookies)
            install(XsrfHeaderPlugin)
            installApiErrorValidator(onUnauthorized = {})
            defaultRequest { url("http://localhost:$closedPort") }
        }

        val failure = runCatching { KtorPatientRepository(client).refresh() }.exceptionOrNull()

        assertEquals(LoadError.UNREACHABLE, failure?.toLoadError())
    }
}
