package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.ActiveTreatment
import com.cramsan.hirsh.network.dto.ActiveTreatmentSummaryResponse
import com.cramsan.hirsh.network.dto.SuspendTreatmentRequest
import com.cramsan.hirsh.network.dto.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.LocalDate

/** What a patient is being given (HCEM-11): treatments come from signed evoluciones' prescriptions. */
interface TreatmentRepository {
    /**
     * The patient's running treatments across all their stays. Re-read whenever this client signs a
     * note or suspends a treatment, since either can change the list.
     */
    fun getActiveTreatments(patientId: String): Flow<List<ActiveTreatment>>

    /** Stops [treatmentId] with [endDate] as its last day; needs the TREATMENT_SUSPEND permission. */
    suspend fun suspend(treatmentId: String, reason: String, endDate: LocalDate)
}

/**
 * Real implementation over `/api/v1/patients/{id}/treatments/active` and
 * `/api/v1/prescribed-treatments/{id}/suspension`. The list isn't cached: the backend writes
 * treatments itself when a note is signed, so each change is followed by a fresh read rather than
 * patched in locally.
 */
class KtorTreatmentRepository(
    private val httpClient: HttpClient,
    private val clinicalNoteRepository: ClinicalNoteRepository,
) : TreatmentRepository {

    private val suspensions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun getActiveTreatments(patientId: String): Flow<List<ActiveTreatment>> =
        merge(clinicalNoteRepository.signatures, suspensions)
            .onStart { emit(Unit) }
            .map {
                httpClient.get("/api/v1/patients/$patientId/treatments/active")
                    .body<List<ActiveTreatmentSummaryResponse>>()
                    .map { it.toDomain() }
            }

    override suspend fun suspend(treatmentId: String, reason: String, endDate: LocalDate) {
        httpClient.put("/api/v1/prescribed-treatments/$treatmentId/suspension") {
            contentType(ContentType.Application.Json)
            setBody(SuspendTreatmentRequest(reason, endDate))
        }
        suspensions.tryEmit(Unit)
    }
}
