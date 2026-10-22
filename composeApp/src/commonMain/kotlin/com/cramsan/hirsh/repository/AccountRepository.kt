package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountProfile
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.NewAccount
import com.cramsan.hirsh.network.getAllPages
import com.cramsan.hirsh.network.dto.CreateUserRequest
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
        _accounts.value = httpClient.getAllPages<UserResponse>("/api/v1/users").map { it.toDomain() }
    }

    override suspend fun roles(): List<AccountRole> =
        httpClient.getAllPages<RoleResponse>("/api/v1/roles").map { it.toDomain() }

    override suspend fun addAccount(account: NewAccount, temporaryPassword: String): Account {
        val created = httpClient.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(account.toRequest(temporaryPassword))
        }.body<UserResponse>().toDomain()
        _accounts.update { it + created }
        return created
    }

    /** Sends only the fields that differ from the cached account; the backend reads a null field as "leave it". */
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
}

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
