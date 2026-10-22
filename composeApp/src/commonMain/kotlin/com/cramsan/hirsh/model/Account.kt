package com.cramsan.hirsh.model

import kotlin.time.Instant

/**
 * One staff account -- the backend's `UserResponse` (HISS-651). [name] is its assembled
 * `fullName`. [id] is the UUID every `/api/v1/users/{userId}` endpoint is keyed by; the API has
 * no lookup by [username].
 */
data class Account(
    val id: String,
    val name: String,
    val username: String,
    val roles: List<AccountRole>,
    val status: AccountStatus,
    /** The last successful login, stamped by the backend; null if the user never logged in. */
    val lastAccess: Instant?,
)

/**
 * One role the account holds, as `UserRoleResponse` carries it. [code] is one of the backend's
 * seeded roles (`SYSTEM_ADMIN`, `ATTENDING_PHYSICIAN`, `RESIDENT`, `NURSING`, `PSYCHOLOGIST`,
 * `AUDITOR`); [name] is its display name from the backend.
 */
data class AccountRole(val code: String, val name: String)

/** System administrators can't be edited or deactivated from the Accounts screen. */
val Account.isAdmin: Boolean get() = roles.any { it.code == SYSTEM_ADMIN_ROLE }

/** Every role's display name, or a dash for an account with none. */
val Account.rolesLabel: String get() = roles.joinToString(", ") { it.name }.ifEmpty { "—" }

const val SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN"

/** Mirrors the backend's `UserStatus`. [LOCKED] is set by repeated failed logins; reactivating releases it. */
enum class AccountStatus { ACTIVE, INACTIVE, LOCKED }

fun AccountStatus.toDisplayLabel(): String = when (this) {
    AccountStatus.ACTIVE -> "Activo"
    AccountStatus.INACTIVE -> "Inactivo"
    AccountStatus.LOCKED -> "Bloqueado"
}
