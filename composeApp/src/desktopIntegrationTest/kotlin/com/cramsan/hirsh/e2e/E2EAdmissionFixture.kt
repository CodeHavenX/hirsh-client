package com.cramsan.hirsh.e2e

import com.cramsan.hirsh.network.ApiConfig
import com.cramsan.hirsh.network.XsrfHeaderPlugin
import com.cramsan.hirsh.network.dto.LoginRequest
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Makes sure the Admision form has something to pick (HISS-634): the first active service the
 * service picker lists has an AVAILABLE bed, and there is an active attending physician. A fresh
 * backend has neither, and every admission occupies a bed, so this runs before each admission.
 * Goes straight to the API as the admin, since the client has no screens for services or beds.
 */
fun ensureAdmissionCatalog() = runBlocking {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpCookies)
        install(XsrfHeaderPlugin)
        installApiErrorValidator(onUnauthorized = {})
        defaultRequest { url(ApiConfig.BASE_URL) }
    }
    client.use {
        it.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(System.getenv("HIRSH_ADMIN_USERNAME")!!, System.getenv("HIRSH_ADMIN_PASSWORD")!!))
        }
        val suffix = System.nanoTime().toString().takeLast(9)

        // Same first page, in the same order, as KtorAdmissionCatalogRepository.services().
        val serviceId = it.get("/api/v1/clinical-services") {
            parameter("page", 0)
            parameter("size", 100)
        }.body<JsonObject>().content().firstOrNull { service -> service.getValue("active").jsonPrimitive.boolean }
            ?.string("id")
            ?: it.postJson(
                "/api/v1/clinical-services",
                buildJsonObject {
                    put("code", "E2E-$suffix")
                    put("name", "Servicio E2E")
                    put("type", "INPATIENT")
                },
            ).string("id")

        val hasFreeBed = it.get("/api/v1/clinical-services/$serviceId/beds").body<JsonObject>()
            .getValue("beds").jsonArray
            .any { bed -> bed.jsonObject.string("status") == "AVAILABLE" }
        if (!hasFreeBed) {
            it.postJson("/api/v1/clinical-services/$serviceId/beds", buildJsonObject { put("number", "E2E-$suffix") })
        }

        val hasPhysician = it.get("/api/v1/users") {
            parameter("roleCode", "ATTENDING_PHYSICIAN")
            parameter("status", "ACTIVE")
        }.body<JsonObject>().content().isNotEmpty()
        if (!hasPhysician) {
            it.postJson(
                "/api/v1/users",
                buildJsonObject {
                    put("username", "e2emed$suffix")
                    put("temporaryPassword", "Temp0ral-$suffix")
                    put("firstName", "Medico")
                    put("lastName", "E2E")
                    put("documentNumber", "7$suffix")
                    putJsonArray("roleAssignments") { add(buildJsonObject { put("roleCode", "ATTENDING_PHYSICIAN") }) }
                },
            )
        }
    }
}

private suspend fun HttpClient.postJson(path: String, body: JsonObject): JsonObject = post(path) {
    contentType(ContentType.Application.Json)
    setBody(body)
}.body()

private fun JsonObject.content(): List<JsonObject> = getValue("content").jsonArray.map { it.jsonObject }

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
