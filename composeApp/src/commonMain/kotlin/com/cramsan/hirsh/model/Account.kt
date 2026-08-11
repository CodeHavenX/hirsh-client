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
