package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.AccessLogPage
import com.cramsan.hirsh.network.dto.AccessLogEntryDto
import com.cramsan.hirsh.network.dto.PageResponse
import com.cramsan.hirsh.network.dto.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A patient's access trail (HISS-624). Read-only: the backend writes entries itself, there's no
 * endpoint to create one. Requires `AUDIT_READ` -- callers without it get an
 * [com.cramsan.hirsh.network.ApiException] carrying [com.cramsan.hirsh.network.ApiError.Forbidden].
 */
interface AccessLogRepository {
    /**
     * The [ACCESS_LOG_PAGE_SIZE] most recent entries for [patientId], newest first. Only the
     * first page: the trail grows with every record view (reading it is itself logged), so
     * fetching all of it the way the patient list does would only get slower over time.
     */
    fun getPatientAccessLog(patientId: String): Flow<AccessLogPage>
}

const val ACCESS_LOG_PAGE_SIZE = 50

class KtorAccessLogRepository(private val httpClient: HttpClient) : AccessLogRepository {
    override fun getPatientAccessLog(patientId: String): Flow<AccessLogPage> = flow {
        val response = httpClient.get("/api/v1/patients/$patientId/access-logs") {
            parameter("page", 0)
            parameter("size", ACCESS_LOG_PAGE_SIZE)
        }.body<PageResponse<AccessLogEntryDto>>()
        emit(AccessLogPage(entries = response.content.map { it.toDomain() }, totalEntries = response.page.totalElements))
    }
}
