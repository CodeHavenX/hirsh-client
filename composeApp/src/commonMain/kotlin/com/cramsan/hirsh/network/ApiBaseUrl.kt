package com.cramsan.hirsh.network

/**
 * The base URL every request is resolved against. [ApiConfig.BASE_URL] on every platform except
 * wasmJs, which can also be deployed behind a reverse proxy on the backend's own origin (see
 * `deploy/` in this repo) -- there the page's own origin is the API's origin.
 */
expect fun apiBaseUrl(): String
