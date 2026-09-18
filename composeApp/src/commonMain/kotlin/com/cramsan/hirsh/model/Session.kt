package com.cramsan.hirsh.model

/**
 * [roles] is display-only (the backend's own `UserProfile`/`MeResponse` doc comments: "roles is
 * only there for display... prefer permissions in client code") -- [permissions] is what every
 * gating decision must use, via [can]. Neither is typed as [Permission] directly: the raw string
 * form is what the API actually returns, and a code this client doesn't yet know about (a future
 * addition to the backend's catalog) should fail closed (no matching [Permission] entry) rather
 * than fail to parse the whole session.
 */
data class Session(
    val username: String,
    val displayName: String,
    val roles: List<String> = emptyList(),
    val permissions: Set<String> = emptySet(),
)

fun Session.can(permission: Permission): Boolean = permissions.contains(permission.name)
