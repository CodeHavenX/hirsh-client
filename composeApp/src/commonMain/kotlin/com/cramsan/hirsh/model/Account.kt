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
    val profile: AccountProfile = AccountProfile(),
)

/**
 * The part of an account the edit dialog can change -- exactly `PatchUserRequest`'s editable
 * fields minus specialty and signature, which have no picker yet (HISS-653). The API can't
 * rename an account or change its username or document. An empty string means "not set".
 */
data class AccountProfile(
    val email: String = "",
    val phone: String = "",
    /** CMP number. Without it the account can't sign documents. */
    val medicalLicense: String = "",
    /** RNE number, for specialists. */
    val specialistRegistry: String = "",
)

/** What `POST /api/v1/users` needs to open an account (HISS-652). The account gets one role, hospital-wide. */
data class NewAccount(
    val firstName: String,
    val lastName: String,
    val secondLastName: String,
    val username: String,
    val documentType: AccountDocumentType,
    val documentNumber: String,
    val roleCode: String,
    val profile: AccountProfile,
)

/** The backend's `UserDocumentType`. */
enum class AccountDocumentType { NID, FOREIGN_ID, PASSPORT }

fun AccountDocumentType.toDisplayLabel(): String = when (this) {
    AccountDocumentType.NID -> "DNI"
    AccountDocumentType.FOREIGN_ID -> "Carne de extranjeria"
    AccountDocumentType.PASSPORT -> "Pasaporte"
}

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
