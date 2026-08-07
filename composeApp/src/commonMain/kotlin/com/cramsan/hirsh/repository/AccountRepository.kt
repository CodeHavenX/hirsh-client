package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface AccountRepository {
    val accounts: StateFlow<List<Account>>
    fun getAccount(username: String): Flow<Account?>

    /** No-op for an unknown [username], matching [PatientRepository.updatePatient]'s own no-op-for-unknown-id precedent. */
    suspend fun updateLastLogin(username: String, lastLogin: String)
}

/**
 * In-memory stand-in, seeded from prototype/shared/data.js's ACCOUNTS, so account
 * lookups (HISS-109's login, HISS-401's table) are demonstrable before the backend
 * service (separate repo) exists. Replace with an implementation backed by
 * [io.ktor.client.HttpClient] once that service exposes an accounts endpoint.
 */
class InMemoryAccountRepository : AccountRepository {

    private val _accounts = MutableStateFlow(
        listOf(
            Account(
                name = "Dr. Anita Patel",
                username = "apatel",
                role = Role.DOCTOR,
                status = AccountStatus.ACTIVE,
                lastLogin = "Hoy 08:12",
            ),
            Account(
                name = "Dr. Marco Reyes",
                username = "mreyes",
                role = Role.DOCTOR,
                status = AccountStatus.ACTIVE,
                lastLogin = "Ayer 17:40",
            ),
            Account(
                name = "Dr. Sara Lin",
                username = "slin",
                role = Role.DOCTOR,
                status = AccountStatus.ACTIVE,
                lastLogin = "28 May 2026",
            ),
            Account(
                name = "Administrador",
                username = "admin",
                role = Role.ADMIN,
                status = AccountStatus.ACTIVE,
                lastLogin = "Hoy 07:55",
            ),
            Account(
                name = "Dr. Tom Veer",
                username = "tveer",
                role = Role.DOCTOR,
                status = AccountStatus.INACTIVE,
                lastLogin = "—",
            ),
        ),
    )
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    override fun getAccount(username: String): Flow<Account?> =
        accounts.map { list -> list.find { it.username == username } }

    override suspend fun updateLastLogin(username: String, lastLogin: String) {
        _accounts.update { list ->
            list.map { if (it.username == username) it.copy(lastLogin = lastLogin) else it }
        }
    }
}
