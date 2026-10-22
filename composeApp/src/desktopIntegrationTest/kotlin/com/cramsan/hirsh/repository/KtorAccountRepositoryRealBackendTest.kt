package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.network.ApiConfig
import com.cramsan.hirsh.network.XsrfHeaderPlugin
import com.cramsan.hirsh.network.dto.LoginRequest
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [KtorAccountRepository] against a real, separately-running backend (Tier 1 of
 * `.claude/skills/_shared/real-backend-check.md`). Creates its own user -- the repository can't
 * yet (HISS-652) -- then lists, deactivates and reactivates it. Skips like
 * [KtorEpisodeRepositoryRealBackendTest] when there's no backend or no credentials.
 */
class KtorAccountRepositoryRealBackendTest {

    private val username: String? = System.getenv("HIRSH_ADMIN_USERNAME")
    private val password: String? = System.getenv("HIRSH_ADMIN_PASSWORD")

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
        assumeTrue("No backend reachable at ${ApiConfig.BASE_URL}", reachable)
        assumeTrue("HIRSH_ADMIN_USERNAME/HIRSH_ADMIN_PASSWORD are not set", username != null && password != null)
    }

    /** The exact plugin stack from `di/AppModule.kt`. */
    private fun realHttpClient() = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(Logging) { level = LogLevel.INFO }
        install(HttpCookies)
        install(XsrfHeaderPlugin)
        installApiErrorValidator(onUnauthorized = {})
        defaultRequest { url(ApiConfig.BASE_URL) }
    }

    @Test
    fun listsDeactivatesAndReactivatesAUser() = runTest {
        val client = realHttpClient()
        client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username!!, password!!))
        }

        val suffix = Random.nextInt(100_000, 999_999).toString()
        val created = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("username", "e2e$suffix")
                    put("temporaryPassword", "Temp0ral-$suffix")
                    put("firstName", "Cuenta")
                    put("lastName", "Prueba")
                    put("documentNumber", "8$suffix")
                    putJsonArray("roleAssignments") {
                        add(buildJsonObject { put("roleCode", "ATTENDING_PHYSICIAN") })
                    }
                },
            )
        }.body<JsonObject>()
        val id = created.getValue("id").jsonPrimitive.content

        val repository = KtorAccountRepository(client)
        repository.refresh()

        val listed = assertNotNull(repository.accounts.value.find { it.id == id })
        assertEquals("e2e$suffix", listed.username)
        assertEquals("Cuenta Prueba", listed.name)
        assertEquals(listOf("ATTENDING_PHYSICIAN"), listed.roles.map(AccountRole::code))
        assertEquals(AccountStatus.ACTIVE, listed.status)
        assertNull(listed.lastAccess)
        // The logged-in admin has logged in, so its lastAccess must decode from a real OffsetDateTime.
        assertNotNull(repository.accounts.value.single { it.username == username }.lastAccess)

        repository.deactivate(id)
        assertEquals(AccountStatus.INACTIVE, repository.accounts.value.single { it.id == id }.status)

        repository.reactivate(id)
        assertEquals(AccountStatus.ACTIVE, repository.accounts.value.single { it.id == id }.status)

        repository.refresh()
        assertEquals(AccountStatus.ACTIVE, repository.accounts.value.single { it.id == id }.status)
    }
}
