package com.cramsan.hirsh.model

/**
 * Field names follow the prototype (`prototype/shared/data.js`'s `ACCOUNTS`), same
 * convention as [Patient]. [status] and [role] translate the prototype's raw string
 * values (`'active'`/`'off'`, `'Medico'`/`'Admin'`) into typed values rather than
 * carrying the raw strings through.
 */
data class Account(
    val name: String,
    val username: String,
    val role: Role,
    val status: AccountStatus,
    val lastLogin: String,
)

enum class AccountStatus { ACTIVE, INACTIVE }

fun AccountStatus.toDisplayLabel(): String = when (this) {
    AccountStatus.ACTIVE -> "Activo"
    AccountStatus.INACTIVE -> "Inactivo"
}

/**
 * Relocated here from `model/Session.kt` (HISS-612): the real session's role/permission model is
 * now [Session.roles]/[Session.permissions], but the Accounts admin screen's still-fake CRUD
 * (`AccountRepository`, not touched until HISS-651+) keeps this simple two-value enum for its own
 * unrelated purposes.
 */
enum class Role { DOCTOR, ADMIN }

/** Mirrors the prototype's raw role strings (`ACCOUNTS[].role`, `accounts.html`'s role column) -- not "Administrador", which is the seed admin account's name, not its role label. */
fun Role.toDisplayLabel(): String = when (this) {
    Role.DOCTOR -> "Medico"
    Role.ADMIN -> "Admin"
}
