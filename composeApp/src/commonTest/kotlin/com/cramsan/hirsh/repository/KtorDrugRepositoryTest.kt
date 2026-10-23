package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Drug
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.network.installApiErrorValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorDrugRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()

    private val client = HttpClient(
        MockEngine { request ->
            requests += request
            respond(
                """
                {"content":[
                  {"id":"d1","code":"PLACEHOLDER-013","genericName":"Haloperidol","concentration":"5 mg","pharmaceuticalForm":"Tableta",
                   "routeOfAdministration":"PO","therapeuticGroup":"Antipsicóticos típicos","isPsychotropic":true,"requiresSpecialPrescription":false,"active":true},
                  {"id":"d2","genericName":"Haloperidol decanoato","routeOfAdministration":"Intramuscular profunda","isPsychotropic":true}
                 ],"page":{"number":0,"size":10,"totalElements":2,"totalPages":1}}
                """.trimIndent(),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        installApiErrorValidator(onUnauthorized = {})
    }

    @Test
    fun `searches the formulary and keeps only routes it knows`() = runTest {
        val drugs = KtorDrugRepository(client).search("halo")

        assertEquals(
            Drug("d1", "Haloperidol", "5 mg", "Tableta", RouteOfAdministration.PO, "Antipsicóticos típicos", isPsychotropic = true),
            drugs.first(),
        )
        assertEquals(null, drugs.last().route, "a free-text route the client has no name for is dropped")
        assertEquals("Haloperidol 5 mg · Tableta", drugs.first().displayName)
        assertEquals("halo", requests.single().url.parameters["search"])
    }
}
