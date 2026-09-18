package com.cramsan.hirsh.network.dto

import kotlinx.serialization.Serializable

/** Body of `POST /api/v1/auth/login` -- see the backend's `/v3/api-docs`, schema `LoginRequest`. */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/**
 * `POST /api/v1/auth/login`'s 200 body -- schema `LoginResponse`. Only [user] maps into this
 * client's [com.cramsan.hirsh.model.Session]; [sessionId]/[userId]/[sessionCreatedAt] exist for
 * server-side session-management UI (`GET /api/v1/auth/sessions`) this client doesn't have yet.
 */
@Serializable
data class LoginResponse(
    val sessionId: String,
    val userId: String,
    val username: String,
    val sessionCreatedAt: String,
    val user: UserProfile,
)

/**
 * Schema `UserProfile` -- the backend's own doc comment: "permissions is the flat list the UI
 * checks before rendering an action; roles is only there for display." This client still gates
 * on [roles] via a lossy heuristic (see [com.cramsan.hirsh.repository.KtorAuthRepository]) until
 * HISS-612 replaces [com.cramsan.hirsh.model.Role] with a real permissions-based model.
 */
@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val fullName: String,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)

/**
 * `GET /api/v1/auth/me`'s 200 body -- schema `MeResponse`, a superset of [UserProfile] with the
 * same [id]/[username]/[fullName]/[roles]/[permissions] fields this client currently maps.
 */
@Serializable
data class MeResponse(
    val id: String,
    val username: String,
    val fullName: String,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)

/** `POST /api/v1/auth/logout`'s 200 body -- schema `LogoutResponse`. Neither field is used; the call matters, not the response. */
@Serializable
data class LogoutResponse(
    val sessionId: String? = null,
    val message: String = "",
)
