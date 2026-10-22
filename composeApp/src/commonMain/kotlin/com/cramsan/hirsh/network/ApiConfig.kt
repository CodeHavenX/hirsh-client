package com.cramsan.hirsh.network

/**
 * The backend HISS service (`hirsh`, a separate repo) runs locally via `./gradlew bootRun`
 * on port 8080 -- see that repo's README. This one dev-only value is shared by all four
 * platform targets: desktop/iOS/Android talk to it directly (not subject to browser CORS),
 * and wasmJs also targets it directly rather than through a dev-server proxy, which is why
 * the backend must allow the wasmJs dev server's own origin, `http://localhost:3000`, via its
 * `CORS_ALLOWED_ORIGIN` (HISS-671) -- a same-origin-policy concern for the *page's* origin,
 * not this URL.
 *
 * Real per-build-variant values (debug/staging/prod, once a production backend actually
 * exists to point at) are still unscoped -- see HISS-672.
 */
object ApiConfig {
    const val BASE_URL = "http://localhost:8080"
}
