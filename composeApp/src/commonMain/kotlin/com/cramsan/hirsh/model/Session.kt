package com.cramsan.hirsh.model

data class Session(
    val username: String,
    val displayName: String,
    val role: Role,
)

enum class Role { DOCTOR, ADMIN }
