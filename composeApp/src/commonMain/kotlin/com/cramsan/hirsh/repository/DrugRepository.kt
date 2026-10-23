package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Drug
import com.cramsan.hirsh.network.dto.DrugResponse
import com.cramsan.hirsh.network.dto.PageResponse
import com.cramsan.hirsh.network.dto.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Typeahead over the hospital's drug formulary, for the evolucion's prescriptions (HCEM-11). */
interface DrugRepository {
    /** Active drugs whose generic name, brand name or code contains [query]. */
    suspend fun search(query: String): List<Drug>
}

/** Real implementation over `GET /api/v1/drugs`, which refuses to list the formulary without a search. */
class KtorDrugRepository(
    private val httpClient: HttpClient,
) : DrugRepository {

    override suspend fun search(query: String): List<Drug> =
        httpClient.get("/api/v1/drugs") {
            parameter("search", query)
            parameter("size", RESULT_LIMIT)
        }.body<PageResponse<DrugResponse>>().content.map { it.toDomain() }

    private companion object {
        const val RESULT_LIMIT = 10
    }
}
