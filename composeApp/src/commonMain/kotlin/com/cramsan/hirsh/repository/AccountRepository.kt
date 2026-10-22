package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountProfile
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.NewAccount
import com.cramsan.hirsh.model.SYSTEM_ADMIN_ROLE
import com.cramsan.hirsh.network.dto.CreateUserRequest
import com.cramsan.hirsh.network.dto.PageResponse
import com.cramsan.hirsh.network.dto.PasswordResetResponse
import com.cramsan.hirsh.network.dto.PatchUserRequest
import com.cramsan.hirsh.network.dto.RoleAssignmentRequest
import com.cramsan.hirsh.network.dto.RoleResponse
import com.cramsan.hirsh.network.dto.UpdateUserStatusRequest
import com.cramsan.hirsh.network.dto.UserResponse
import com.cramsan.hirsh.network.dto.toDomain
import com.cramsan.hirsh.network.dto.toDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
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

    /** The roles an account can be given, for the create dialog's picker. */
    suspend fun roles(): List<AccountRole>

    /**
     * Opens an [AccountStatus.ACTIVE] account holding [NewAccount.roleCode]. The user must change
     * [temporaryPassword] at first login.
     */
    suspend fun addAccount(account: NewAccount, temporaryPassword: String): Account

    /** Saves [profile] over [id]'s; only changed fields are sent. No-op for an unknown [id]. */
    suspend fun updateAccount(id: String, profile: AccountProfile)

    /**
     * Replaces [id]'s password with a generated one-time password and returns it -- this is the
     * only time it can be read. Also releases a [AccountStatus.LOCKED] account.
     */
    suspend fun resetPassword(id: String): String

    /** Sets [id]'s status to [AccountStatus.INACTIVE]; the user can no longer log in. */
    suspend fun deactivate(id: String)

    /** Sets [id]'s status to [AccountStatus.ACTIVE] -- this also releases a [AccountStatus.LOCKED] account. */
    suspend fun reactivate(id: String)
}

/**
 * In-memory stand-in, seeded from prototype/shared/data.js's ACCOUNTS (with the backend's real
 * role codes and names). No longer bound since HISS-652 made every Accounts action real; kept for
 * [FakeAuthRepository] and tests.
 */
class InMemoryAccountRepository : AccountRepository {

    private val _accounts = MutableStateFlow(seedAccounts())
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private var nextId = _accounts.value.size + 1

    override suspend fun refresh() = Unit

    override suspend fun roles(): List<AccountRole> = SEEDED_ROLES

    override suspend fun addAccount(account: NewAccount, temporaryPassword: String): Account {
        val created = Account(
            id = "acc_${nextId++}",
            name = assembleFullName(account.firstName, account.lastName, account.secondLastName),
            username = account.username,
            roles = SEEDED_ROLES.filter { it.code == account.roleCode },
            status = AccountStatus.ACTIVE,
            lastAccess = null,
            profile = account.profile,
        )
        _accounts.update { it + created }
        return created
    }

    override suspend fun updateAccount(id: String, profile: AccountProfile) {
        _accounts.update { list -> list.map { if (it.id == id) it.copy(profile = profile) else it } }
    }

    override suspend fun resetPassword(id: String): String {
        _accounts.update { list ->
            list.map { if (it.id == id && it.status == AccountStatus.LOCKED) it.copy(status = AccountStatus.ACTIVE) else it }
        }
        return "Temp0ral-${id.takeLast(4)}"
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
 * Real implementation over `/api/v1/users` (HISS-651/652/653). [refresh] loads every page, like
 * [KtorPatientRepository.refresh]; every mutation writes its result back into [accounts], so an
 * open list shows it without a reload.
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

    /** The six seeded roles fit one page; a deployment with more than [PAGE_SIZE] roles would need paging here. */
    override suspend fun roles(): List<AccountRole> =
        httpClient.get("/api/v1/roles") {
            parameter("page", 0)
            parameter("size", PAGE_SIZE)
        }.body<PageResponse<RoleResponse>>().content.map { it.toDomain() }

    override suspend fun addAccount(account: NewAccount, temporaryPassword: String): Account {
        val created = httpClient.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(account.toRequest(temporaryPassword))
        }.body<UserResponse>().toDomain()
        _accounts.update { it + created }
        return created
    }

    /**
     * Sends only the fields that differ from the cached account: the backend reads a null field
     * as "leave it", and re-sending an unchanged CMP or RNE would re-run its uniqueness check.
     */
    override suspend fun updateAccount(id: String, profile: AccountProfile) {
        val current = _accounts.value.find { it.id == id }?.profile ?: AccountProfile()
        fun changed(new: String, old: String) = new.trim().takeIf { it != old }
        val request = PatchUserRequest(
            email = changed(profile.email, current.email),
            phone = changed(profile.phone, current.phone),
            medicalLicense = changed(profile.medicalLicense, current.medicalLicense),
            specialistRegistry = changed(profile.specialistRegistry, current.specialistRegistry),
        )
        if (request == PatchUserRequest()) return
        val updated = httpClient.patch("/api/v1/users/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<UserResponse>().toDomain()
        _accounts.update { list -> list.map { if (it.id == id) updated else it } }
    }

    override suspend fun resetPassword(id: String): String {
        val reset = httpClient.post("/api/v1/users/$id/password-reset").body<PasswordResetResponse>()
        _accounts.update { list ->
            list.map { if (it.id == id && it.status == AccountStatus.LOCKED) it.copy(status = AccountStatus.ACTIVE) else it }
        }
        return reset.temporaryPassword
    }

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

/** The backend's seeded roles, in its own order (by code). */
private val SEEDED_ROLES = listOf(
    ATTENDING_PHYSICIAN,
    AccountRole("AUDITOR", "Auditor"),
    AccountRole("NURSING", "Nursing"),
    AccountRole("PSYCHOLOGIST", "Psychologist"),
    AccountRole("RESIDENT", "Resident"),
    SYSTEM_ADMINISTRATOR,
)

private fun NewAccount.toRequest(temporaryPassword: String) = CreateUserRequest(
    username = username.trim(),
    temporaryPassword = temporaryPassword,
    firstName = firstName.trim(),
    lastName = lastName.trim(),
    secondLastName = secondLastName.trim().ifEmpty { null },
    documentType = documentType.name,
    documentNumber = documentNumber.trim(),
    email = profile.email.trim().ifEmpty { null },
    phone = profile.phone.trim().ifEmpty { null },
    medicalLicense = profile.medicalLicense.trim().ifEmpty { null },
    specialistRegistry = profile.specialistRegistry.trim().ifEmpty { null },
    roleAssignments = listOf(RoleAssignmentRequest(roleCode)),
)

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
