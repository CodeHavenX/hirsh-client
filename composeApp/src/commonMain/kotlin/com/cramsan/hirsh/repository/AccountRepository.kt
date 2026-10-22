package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.SYSTEM_ADMIN_ROLE
import com.cramsan.hirsh.network.dto.PageResponse
import com.cramsan.hirsh.network.dto.UpdateUserStatusRequest
import com.cramsan.hirsh.network.dto.UserResponse
import com.cramsan.hirsh.network.dto.toDomain
import com.cramsan.hirsh.network.dto.toDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

interface AccountRepository {
    val accounts: StateFlow<List<Account>>

    /** Loads [accounts]; see [PatientRepository.refresh] for why callers trigger this explicitly. */
    suspend fun refresh()

    /**
     * The new account is always an attending physician and [AccountStatus.ACTIVE] -- matches
     * accounts.html's own "Rol fijo: Medico" copy. The real `CreateUserRequest` needs fields this
     * doesn't take (HISS-652).
     */
    suspend fun addAccount(name: String, username: String): Account

    /** No-op for an unknown [id]. The real API can't rename an account (HISS-653). */
    suspend fun updateAccount(id: String, name: String, username: String)

    /** Sets [id]'s status to [AccountStatus.INACTIVE]; the user can no longer log in. */
    suspend fun deactivate(id: String)

    /** Sets [id]'s status to [AccountStatus.ACTIVE] -- this also releases a [AccountStatus.LOCKED] account. */
    suspend fun reactivate(id: String)
}

/**
 * In-memory stand-in, seeded from prototype/shared/data.js's ACCOUNTS (with the backend's real
 * role codes and names). Still the bound implementation: the Accounts screen's add and edit
 * dialogs need HISS-652/653 before [KtorAccountRepository] can replace it.
 */
class InMemoryAccountRepository : AccountRepository {

    private val _accounts = MutableStateFlow(seedAccounts())
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private var nextId = _accounts.value.size + 1

    override suspend fun refresh() = Unit

    override suspend fun addAccount(name: String, username: String): Account {
        val created = Account(
            id = "acc_${nextId++}",
            name = name,
            username = username,
            roles = listOf(ATTENDING_PHYSICIAN),
            status = AccountStatus.ACTIVE,
            lastAccess = null,
        )
        _accounts.update { it + created }
        return created
    }

    override suspend fun updateAccount(id: String, name: String, username: String) {
        _accounts.update { list -> list.map { if (it.id == id) it.copy(name = name, username = username) else it } }
    }

    /** No-op for an unknown [id]. */
    override suspend fun deactivate(id: String) = setStatus(id, AccountStatus.INACTIVE)

    /** No-op for an unknown [id]. */
    override suspend fun reactivate(id: String) = setStatus(id, AccountStatus.ACTIVE)

    /** What the backend does on a successful login; only [FakeAuthRepository] calls this. No-op for an unknown [username]. */
    fun recordLogin(username: String, at: Instant) {
        _accounts.update { list -> list.map { if (it.username == username) it.copy(lastAccess = at) else it } }
    }

    private fun setStatus(id: String, status: AccountStatus) {
        _accounts.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }
}

/**
 * Real implementation over `/api/v1/users` (HISS-651). Not bound yet -- see
 * [InMemoryAccountRepository]. [addAccount]/[updateAccount] throw [UnsupportedOperationException]:
 * the real create and edit requests take different fields, which HISS-652/653 bring in.
 *
 * [refresh] loads every page, like [KtorPatientRepository.refresh]; a status change writes the
 * response back into [accounts], so an open list shows it without a reload.
 */
class KtorAccountRepository(
    private val httpClient: HttpClient,
) : AccountRepository {

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    override suspend fun refresh() {
        _accounts.value = fetchAllPages()
    }

    private suspend fun fetchAllPages(): List<Account> {
        val firstPage = fetchPage(0)
        val pages = buildList {
            addAll(firstPage.content)
            for (page in 1 until firstPage.page.totalPages) {
                addAll(fetchPage(page).content)
            }
        }
        return pages.map { it.toDomain() }
    }

    private suspend fun fetchPage(page: Int): PageResponse<UserResponse> =
        httpClient.get("/api/v1/users") {
            parameter("page", page)
            parameter("size", PAGE_SIZE)
        }.body()

    override suspend fun addAccount(name: String, username: String): Account =
        throw UnsupportedOperationException("Account creation needs CreateUserRequest's fields (HISS-652)")

    override suspend fun updateAccount(id: String, name: String, username: String): Unit =
        throw UnsupportedOperationException("The API can't rename an account (HISS-653)")

    override suspend fun deactivate(id: String) = setStatus(id, AccountStatus.INACTIVE)

    override suspend fun reactivate(id: String) = setStatus(id, AccountStatus.ACTIVE)

    private suspend fun setStatus(id: String, status: AccountStatus) {
        val updated = httpClient.put("/api/v1/users/$id/status") {
            contentType(ContentType.Application.Json)
            setBody(UpdateUserStatusRequest(status.toDto()))
        }.body<UserResponse>().toDomain()
        _accounts.update { list -> list.map { if (it.id == id) updated else it } }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

private val ATTENDING_PHYSICIAN = AccountRole("ATTENDING_PHYSICIAN", "Attending Physician")
private val SYSTEM_ADMINISTRATOR = AccountRole(SYSTEM_ADMIN_ROLE, "System Administrator")

/** Ported from prototype/shared/data.js's ACCOUNTS; its relative "last login" strings became fixed instants. */
private fun seedAccounts(): List<Account> = listOf(
    Account(
        id = "acc_1",
        name = "Dr. Anita Patel",
        username = "apatel",
        roles = listOf(ATTENDING_PHYSICIAN),
        status = AccountStatus.ACTIVE,
        lastAccess = Instant.parse("2026-06-18T13:12:00Z"),
    ),
    Account(
        id = "acc_2",
        name = "Dr. Marco Reyes",
        username = "mreyes",
        roles = listOf(ATTENDING_PHYSICIAN),
        status = AccountStatus.ACTIVE,
        lastAccess = Instant.parse("2026-06-17T22:40:00Z"),
    ),
    Account(
        id = "acc_3",
        name = "Dr. Sara Lin",
        username = "slin",
        roles = listOf(ATTENDING_PHYSICIAN),
        status = AccountStatus.ACTIVE,
        lastAccess = Instant.parse("2026-05-28T15:00:00Z"),
    ),
    Account(
        id = "acc_4",
        name = "Administrador",
        username = "admin",
        roles = listOf(SYSTEM_ADMINISTRATOR),
        status = AccountStatus.ACTIVE,
        lastAccess = Instant.parse("2026-06-18T12:55:00Z"),
    ),
    Account(
        id = "acc_5",
        name = "Dr. Tom Veer",
        username = "tveer",
        roles = listOf(ATTENDING_PHYSICIAN),
        status = AccountStatus.INACTIVE,
        lastAccess = null,
    ),
)
