package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

private data class RecordedUserRequest(val method: HttpMethod, val path: String, val query: String, val body: String)

/** Same shape as [KtorPatientRepositoryTest]'s `mockClient`. */
private fun mockClient(
    recorded: MutableList<RecordedUserRequest> = mutableListOf(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient {
    val engine = MockEngine { request ->
        val bodyText = when (val content = request.body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }
        recorded += RecordedUserRequest(request.method, request.url.encodedPath, request.url.encodedQuery, bodyText)
        handler(request)
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }
}

private fun MockRequestHandleScope.jsonResponse(status: HttpStatusCode, body: String): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

private fun pageJson(content: List<String>, number: Int, totalPages: Int): String =
    """{"content": [${content.joinToString(",")}], "page": {"number": $number, "size": 100, "totalElements": 0, "totalPages": $totalPages}}"""

/** A trimmed real `UserResponse`, keeping the fields [KtorAccountRepository] ignores so decoding is exercised. */
private fun userJson(
    id: String = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
    username: String = "atorres",
    status: String = "ACTIVE",
    lastAccess: String? = "2026-06-26T08:30:00-05:00",
): String = """
    {
      "id": "$id",
      "username": "$username",
      "firstName": "Ana",
      "lastName": "Torres",
      "secondLastName": "Quispe",
      "fullName": "Ana Torres Quispe",
      "documentType": "NID",
      "documentNumber": "45821337",
      "email": null,
      "status": "$status",
      "lastAccess": ${lastAccess?.let { "\"$it\"" } ?: "null"},
      "roles": [
        {"userRoleId": "6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b90", "roleCode": "ATTENDING_PHYSICIAN",
         "roleName": "Attending Physician", "service": null}
      ],
      "audit": {"createdAt": "2026-01-01T00:00:00Z", "jpaVersion": 3}
    }
""".trimIndent()

class KtorAccountRepositoryTest {

    @Test
    fun `refresh maps a UserResponse onto Account`() = runTest {
        val recorded = mutableListOf<RecordedUserRequest>()
        val repository = KtorAccountRepository(
            mockClient(recorded) { jsonResponse(HttpStatusCode.OK, pageJson(listOf(userJson()), 0, 1)) },
        )

        repository.refresh()

        assertEquals(
            listOf(
                Account(
                    id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
                    name = "Ana Torres Quispe",
                    username = "atorres",
                    roles = listOf(AccountRole("ATTENDING_PHYSICIAN", "Attending Physician")),
                    status = AccountStatus.ACTIVE,
                    lastAccess = Instant.parse("2026-06-26T13:30:00Z"),
                ),
            ),
            repository.accounts.value,
        )
        assertEquals("/api/v1/users", recorded.single().path)
        assertEquals("page=0&size=100", recorded.single().query)
    }

    @Test
    fun `refresh merges every page in order`() = runTest {
        val repository = KtorAccountRepository(
            mockClient { request ->
                val page = request.url.parameters["page"]
                jsonResponse(HttpStatusCode.OK, pageJson(listOf(userJson(id = "u$page", username = "user$page")), 0, 3))
            },
        )

        repository.refresh()

        assertEquals(listOf("u0", "u1", "u2"), repository.accounts.value.map { it.id })
    }

    @Test
    fun `a never-logged-in and a locked account decode`() = runTest {
        val repository = KtorAccountRepository(
            mockClient {
                jsonResponse(HttpStatusCode.OK, pageJson(listOf(userJson(status = "LOCKED", lastAccess = null)), 0, 1))
            },
        )

        repository.refresh()

        val account = repository.accounts.value.single()
        assertEquals(AccountStatus.LOCKED, account.status)
        assertEquals(null, account.lastAccess)
    }

    @Test
    fun `deactivate PUTs INACTIVE and writes the response back into accounts`() = runTest {
        val recorded = mutableListOf<RecordedUserRequest>()
        val repository = KtorAccountRepository(
            mockClient(recorded) { request ->
                if (request.method == HttpMethod.Get) {
                    jsonResponse(HttpStatusCode.OK, pageJson(listOf(userJson(id = "u1"), userJson(id = "u2")), 0, 1))
                } else {
                    jsonResponse(HttpStatusCode.OK, userJson(id = "u2", status = "INACTIVE"))
                }
            },
        )
        repository.refresh()

        repository.deactivate("u2")

        val put = recorded.last()
        assertEquals(HttpMethod.Put, put.method)
        assertEquals("/api/v1/users/u2/status", put.path)
        assertEquals("""{"status":"INACTIVE"}""", put.body)
        assertEquals(
            listOf(AccountStatus.ACTIVE, AccountStatus.INACTIVE),
            repository.accounts.value.map { it.status },
        )
    }

    @Test
    fun `reactivate PUTs ACTIVE`() = runTest {
        val recorded = mutableListOf<RecordedUserRequest>()
        val repository = KtorAccountRepository(
            mockClient(recorded) { jsonResponse(HttpStatusCode.OK, userJson(id = "u1")) },
        )

        repository.reactivate("u1")

        assertEquals("/api/v1/users/u1/status", recorded.single().path)
        assertEquals("""{"status":"ACTIVE"}""", recorded.single().body)
    }

    @Test
    fun `a failed status change surfaces as ApiException and leaves accounts alone`() = runTest {
        val repository = KtorAccountRepository(
            mockClient { request ->
                if (request.method == HttpMethod.Get) {
                    jsonResponse(HttpStatusCode.OK, pageJson(listOf(userJson(id = "u1")), 0, 1))
                } else {
                    jsonResponse(HttpStatusCode.NotFound, """{"title": "Not Found", "status": 404}""")
                }
            },
        )
        repository.refresh()

        assertFailsWith<ApiException> { repository.deactivate("u1") }

        assertEquals(AccountStatus.ACTIVE, repository.accounts.value.single().status)
    }

    @Test
    fun `add and edit are not supported yet`() = runTest {
        val repository = KtorAccountRepository(mockClient { error("no request expected") })

        assertFailsWith<UnsupportedOperationException> { repository.addAccount("Ana", "atorres") }
        assertFailsWith<UnsupportedOperationException> { repository.updateAccount("u1", "Ana", "atorres") }
    }
}
