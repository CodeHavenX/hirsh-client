package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.AccountDocumentType
import com.cramsan.hirsh.model.AccountProfile
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.NewAccount
import com.cramsan.hirsh.network.ApiConfig
import com.cramsan.hirsh.network.XsrfHeaderPlugin
import com.cramsan.hirsh.network.dto.LoginRequest
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [KtorAccountRepository] against a real, separately-running backend (Tier 1 of
 * `.claude/skills/_shared/real-backend-check.md`). Creates its own user through the repository,
 * then lists, edits, resets, deactivates and reactivates it. Skips like
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
    fun createsListsEditsResetsAndTogglesAUser() = runTest {
        val client = realHttpClient()
        client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username!!, password!!))
        }
        val repository = KtorAccountRepository(client)
        val suffix = Random.nextInt(100_000, 999_999).toString()

        val roles = repository.roles()
        assertTrue(
            roles.map(AccountRole::code).containsAll(
                listOf("SYSTEM_ADMIN", "ATTENDING_PHYSICIAN", "RESIDENT", "NURSING", "PSYCHOLOGIST", "AUDITOR"),
            ),
        )

        val created = repository.addAccount(
            NewAccount(
                firstName = "Cuenta",
                lastName = "Prueba",
                secondLastName = "Real",
                username = "e2e$suffix",
                documentType = AccountDocumentType.FOREIGN_ID,
                documentNumber = "8$suffix",
                roleCode = "RESIDENT",
                profile = AccountProfile(email = "e2e$suffix@hospital.pe", medicalLicense = "CMP-$suffix"),
            ),
            temporaryPassword = "Temp0ral-$suffix",
        )
        assertEquals("Cuenta Prueba Real", created.name)
        assertEquals(listOf("RESIDENT"), created.roles.map(AccountRole::code))
        assertEquals(AccountStatus.ACTIVE, created.status)
        assertNull(created.lastAccess)
        assertEquals("CMP-$suffix", created.profile.medicalLicense)

        repository.refresh()
        assertEquals(created, repository.accounts.value.single { it.id == created.id })
        // The logged-in admin has logged in, so its lastAccess must decode from a real OffsetDateTime.
        assertNotNull(repository.accounts.value.single { it.username == username }.lastAccess)

        repository.updateAccount(created.id, created.profile.copy(phone = "555-$suffix", specialistRegistry = "RNE-$suffix"))
        repository.refresh()
        val edited = repository.accounts.value.single { it.id == created.id }.profile
        assertEquals("555-$suffix", edited.phone)
        assertEquals("RNE-$suffix", edited.specialistRegistry)
        assertEquals("CMP-$suffix", edited.medicalLicense)

        val temporary = repository.resetPassword(created.id)
        assertTrue(temporary.length >= 8)

        repository.deactivate(created.id)
        assertEquals(AccountStatus.INACTIVE, repository.accounts.value.single { it.id == created.id }.status)

        repository.reactivate(created.id)
        assertEquals(AccountStatus.ACTIVE, repository.accounts.value.single { it.id == created.id }.status)
    }
}
