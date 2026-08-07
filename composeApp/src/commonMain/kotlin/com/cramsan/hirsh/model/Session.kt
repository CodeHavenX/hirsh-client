package com.cramsan.hirsh.model

data class Session(
    val username: String,
    val displayName: String,
    val role: Role,
)

enum class Role { DOCTOR, ADMIN }

/** Mirrors the prototype's raw role strings (`ACCOUNTS[].role`, `accounts.html`'s role column) -- not "Administrador", which is the seed admin account's name, not its role label. */
fun Role.toDisplayLabel(): String = when (this) {
    Role.DOCTOR -> "Medico"
    Role.ADMIN -> "Admin"
}
