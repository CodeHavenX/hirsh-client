package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Icd10Code
import com.cramsan.hirsh.network.dto.Icd10CodeResponse
import com.cramsan.hirsh.network.dto.PageResponse
import com.cramsan.hirsh.network.dto.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Typeahead over the backend's ICD-10 catalog, for diagnosis pickers (HISS-633). */
interface Icd10Repository {
    /** Codes whose code starts with, or whose description contains, [query] -- psychiatric (Chapter V) codes only. */
    suspend fun search(query: String): List<Icd10Code>
}

/**
 * Real implementation over `GET /api/v1/icd10-codes`. No in-memory counterpart: the endpoint
 * exists, and the catalog only holds placeholder codes until the MINSA Peru list is loaded
 * (hirsh#14), so a fake would only duplicate that placeholder list.
 */
class KtorIcd10Repository(
    private val httpClient: HttpClient,
) : Icd10Repository {

    override suspend fun search(query: String): List<Icd10Code> =
        httpClient.get("/api/v1/icd10-codes") {
            parameter("search", query)
            parameter("psychiatricOnly", true)
            parameter("size", RESULT_LIMIT)
        }.body<PageResponse<Icd10CodeResponse>>().content.map { it.toDomain() }

    private companion object {
        const val RESULT_LIMIT = 10
    }
}
