package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors [com.cramsan.hirsh.network.ApiErrorMappingTest]'s `buildClient` -- a mock engine wrapped in the same plugin stack `KtorAuthRepository` actually runs behind (`installApiErrorValidator`), so a 401 is mapped by the real pipeline, not assumed. */
private fun clientRespondingWith(status: HttpStatusCode, body: String, contentType: Boolean = true): HttpClient {
    val engine = MockEngine {
        if (contentType) {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        } else {
            respond(body, status)
        }
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }
}

class KtorAuthRepositoryTest {

    @Test
    fun `login success maps LoginResponse user into a Session`() = runTest {
        val body = """
            {"sessionId":"s1","userId":"u1","username":"atorres","sessionCreatedAt":"2026-01-01T00:00:00Z",
             "user":{"id":"u1","username":"atorres","fullName":"Ana Torres","roles":["PSYCHIATRIST"],"permissions":["PATIENT_READ"]}}
        """.trimIndent()
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.OK, body))

        val session = repository.login("atorres", "S3cure-P4ssw0rd").getOrThrow()

        assertEquals("atorres", session.username)
        assertEquals("Ana Torres", session.displayName)
        assertEquals(Role.DOCTOR, session.role)
    }

    @Test
    fun `login maps a role code containing ADMIN to Role Admin`() = runTest {
        val body = """
            {"sessionId":"s1","userId":"u1","username":"admin","sessionCreatedAt":"2026-01-01T00:00:00Z",
             "user":{"id":"u1","username":"admin","fullName":"Admin","roles":["SYSTEM_ADMIN"],"permissions":[]}}
        """.trimIndent()
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.OK, body))

        val session = repository.login("admin", "whatever123").getOrThrow()

        assertEquals(Role.ADMIN, session.role)
    }

    @Test
    fun `login maps a role code without ADMIN to the default Role Doctor`() = runTest {
        val body = """
            {"sessionId":"s1","userId":"u1","username":"atorres","sessionCreatedAt":"2026-01-01T00:00:00Z",
             "user":{"id":"u1","username":"atorres","fullName":"Ana Torres","roles":["PSYCHIATRIST"],"permissions":[]}}
        """.trimIndent()
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.OK, body))

        val session = repository.login("atorres", "S3cure-P4ssw0rd").getOrThrow()

        assertEquals(Role.DOCTOR, session.role)
    }

    @Test
    fun `login with blank credentials fails without making a network call`() = runTest {
        var networkCalls = 0
        val engine = MockEngine {
            networkCalls++
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            installApiErrorValidator(onUnauthorized = {})
        }
        val repository = KtorAuthRepository(client)

        val result = repository.login("", "x")

        assertTrue(result.isFailure)
        assertEquals(0, networkCalls)
    }

    @Test
    fun `login 401 maps to the generic invalid-credentials message, not the raw ApiException`() = runTest {
        val body = """{"type":"t","title":"Unauthorized","status":401,"detail":"Invalid credentials or no active session"}"""
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.Unauthorized, body))

        val result = repository.login("atorres", "wrong-password")

        assertTrue(result.isFailure)
        assertEquals("Usuario o contrasena incorrectos", result.exceptionOrNull()?.message)
    }

    @Test
    fun `restoreSession 200 maps MeResponse into a Session`() = runTest {
        val body = """{"id":"u1","username":"atorres","fullName":"Ana Torres","roles":["PSYCHIATRIST"],"permissions":[]}"""
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.OK, body))

        val session = repository.restoreSession()

        assertEquals("atorres", session?.username)
        assertEquals("Ana Torres", session?.displayName)
    }

    @Test
    fun `restoreSession 401 returns null instead of throwing`() = runTest {
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.Unauthorized, "", contentType = false))

        assertNull(repository.restoreSession())
    }

    @Test
    fun `logout success does not throw`() = runTest {
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.OK, """{"message":"Logged out"}"""))

        repository.logout()
    }

    @Test
    fun `logout without a session does not throw either`() = runTest {
        // The backend's own docs: "Calling it without a session is not an error."
        val repository = KtorAuthRepository(clientRespondingWith(HttpStatusCode.Unauthorized, "", contentType = false))

        repository.logout()
    }
}
