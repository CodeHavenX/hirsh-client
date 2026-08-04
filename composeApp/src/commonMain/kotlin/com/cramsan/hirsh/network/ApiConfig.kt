package com.cramsan.hirsh.network

/**
 * The backend HISS service lives in a separate repo and isn't wired up yet.
 * Point this at that service once it has a real address (and move it to a
 * build-variant-specific value -- debug/staging/prod -- instead of a constant).
 */
object ApiConfig {
    const val BASE_URL = "https://TODO-hiss-backend.example.com"
}
