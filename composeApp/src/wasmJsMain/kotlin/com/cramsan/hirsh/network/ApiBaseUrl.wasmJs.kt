package com.cramsan.hirsh.network

import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The dev server's index.html has no `hiss-api-base-url` meta tag, so local web dev keeps
 * calling [ApiConfig.BASE_URL] cross-origin. The deployed image adds the tag (see
 * `deploy/web.Dockerfile`): a blank value means "this page's own origin", where nginx proxies
 * `/api` to the backend -- which keeps the `SameSite=Lax` session cookie first-party.
 */
actual fun apiBaseUrl(): String {
    val configured = document.querySelector("meta[name=hiss-api-base-url]")?.getAttribute("content")
        ?: return ApiConfig.BASE_URL
    return configured.ifBlank { window.location.origin }
}
