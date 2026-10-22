package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountDocumentType
import com.cramsan.hirsh.model.AccountProfile
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One entry of `GET /api/v1/users`, and the body of the single-user endpoints -- schema
 * `UserResponse`. Only what [Account] shows is mapped; contact/registry/signature fields are left
 * to the account edit screen (HISS-653).
 */
@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val fullName: String,
    val status: UserStatusDto,
    /** An `OffsetDateTime`, e.g. `2026-06-26T13:30:00Z`. */
    val lastAccess: String? = null,
    val roles: List<UserRoleResponse> = emptyList(),
    val email: String? = null,
    val phone: String? = null,
    val medicalLicense: String? = null,
    val specialistRegistry: String? = null,
)

/** Schema `UserRoleResponse`. Its service scope isn't shown yet, so it isn't mapped. */
@Serializable
data class UserRoleResponse(
    val roleCode: String,
    val roleName: String,
)

/** Body of `POST /api/v1/users` -- schema `CreateUserRequest`. Specialty isn't sent: there is no picker for it yet. */
@Serializable
data class CreateUserRequest(
    val username: String,
    val temporaryPassword: String,
    val firstName: String,
    val lastName: String,
    val secondLastName: String?,
    /** An [AccountDocumentType] name -- they match the backend's `UserDocumentType` exactly. */
    val documentType: String,
    val documentNumber: String,
    val email: String?,
    val phone: String?,
    val medicalLicense: String?,
    val specialistRegistry: String?,
    val roleAssignments: List<RoleAssignmentRequest>,
)

/** Schema `RoleAssignmentRequest`; no `serviceId`, so the role is hospital-wide. */
@Serializable
data class RoleAssignmentRequest(val roleCode: String)

/** Body of `PATCH /api/v1/users/{userId}` -- schema `PatchUserRequest`. A null field is left unchanged. */
@Serializable
data class PatchUserRequest(
    val email: String? = null,
    val phone: String? = null,
    val medicalLicense: String? = null,
    val specialistRegistry: String? = null,
)

/** Response of `POST /api/v1/users/{userId}/password-reset` -- schema `PasswordResetResponse`. */
@Serializable
data class PasswordResetResponse(val temporaryPassword: String)

/** One entry of `GET /api/v1/roles` -- schema `RoleResponse`; its permissions aren't read. */
@Serializable
data class RoleResponse(val code: String, val name: String)

/** Body of `PUT /api/v1/users/{userId}/status` -- schema `UpdateUserStatusRequest`. */
@Serializable
data class UpdateUserStatusRequest(val status: UserStatusDto)

/** Wire values match [AccountStatus]'s constant names exactly (the backend's `UserStatus`). */
@Serializable
enum class UserStatusDto { ACTIVE, INACTIVE, LOCKED }

fun UserStatusDto.toDomain(): AccountStatus = when (this) {
    UserStatusDto.ACTIVE -> AccountStatus.ACTIVE
    UserStatusDto.INACTIVE -> AccountStatus.INACTIVE
    UserStatusDto.LOCKED -> AccountStatus.LOCKED
}

fun AccountStatus.toDto(): UserStatusDto = when (this) {
    AccountStatus.ACTIVE -> UserStatusDto.ACTIVE
    AccountStatus.INACTIVE -> UserStatusDto.INACTIVE
    AccountStatus.LOCKED -> UserStatusDto.LOCKED
}

fun UserResponse.toDomain(): Account = Account(
    id = id,
    name = fullName,
    username = username,
    roles = roles.map { AccountRole(code = it.roleCode, name = it.roleName) },
    status = status.toDomain(),
    lastAccess = lastAccess?.let(Instant::parse),
    profile = AccountProfile(
        email = email.orEmpty(),
        phone = phone.orEmpty(),
        medicalLicense = medicalLicense.orEmpty(),
        specialistRegistry = specialistRegistry.orEmpty(),
    ),
)

fun RoleResponse.toDomain(): AccountRole = AccountRole(code = code, name = name)
