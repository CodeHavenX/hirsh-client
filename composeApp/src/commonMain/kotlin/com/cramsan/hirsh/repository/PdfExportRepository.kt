package com.cramsan.hirsh.repository

import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.network.dto.CreatePdfExportRequest
import com.cramsan.hirsh.network.dto.PdfExportAcceptedResponse
import com.cramsan.hirsh.network.dto.PdfExportStatusResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay

/** What can be exported as a PDF (HCEM-14). */
sealed interface PdfExportTarget {
    val hospId: String

    /** One signed evolucion. */
    data class Evolucion(override val hospId: String, val evoId: String) : PdfExportTarget

    /** The stay's signed Historia Clinica. */
    data class HistoriaClinica(override val hospId: String) : PdfExportTarget

    /** Every document of the stay; the backend refuses while any of them is a draft. */
    data class FullRecord(override val hospId: String) : PdfExportTarget
}

class PdfFile(val filename: String, val bytes: ByteArray)

/** The backend tried and couldn't generate the file; [reason] is its own explanation. */
class PdfExportFailedException(val reason: String?) : Exception("PDF export failed: $reason")

/** Still not generated after the whole wait. */
class PdfExportTimeoutException : Exception("PDF export timed out")

interface PdfExportRepository {
    /** Requests the export, waits for the backend to generate it, and downloads it. */
    suspend fun export(target: PdfExportTarget): PdfFile
}

/** How long to wait between status checks: growing from [initialMillis] to [maxMillis], for at most [timeoutMillis]. */
data class PdfExportPolling(
    val initialMillis: Long = 500,
    val maxMillis: Long = 3_000,
    val timeoutMillis: Long = 90_000,
)

/**
 * Real implementation over `/api/v1/pdf-exports`: generation is asynchronous, so the export is
 * queued, its status polled with a growing backoff, and the file fetched once it's `COMPLETED`.
 * Every download is written to the patient's access trail by the backend.
 */
class KtorPdfExportRepository(
    private val httpClient: HttpClient,
    private val clinicalNoteRepository: ClinicalNoteRepository,
    private val polling: PdfExportPolling = PdfExportPolling(),
) : PdfExportRepository {

    override suspend fun export(target: PdfExportTarget): PdfFile {
        val request = when (target) {
            is PdfExportTarget.Evolucion ->
                CreatePdfExportRequest("SINGLE_PROGRESS_NOTE", target.hospId, versionId(target.hospId, target.evoId))
            is PdfExportTarget.HistoriaClinica ->
                CreatePdfExportRequest("ADMISSION_HISTORY", target.hospId, versionId(target.hospId, null))
            is PdfExportTarget.FullRecord -> CreatePdfExportRequest("FULL_RECORD", target.hospId)
        }
        val accepted = httpClient.post("/api/v1/pdf-exports") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<PdfExportAcceptedResponse>()
        val done = awaitGenerated(accepted.id)
        val bytes = httpClient.get("/api/v1/pdf-exports/${accepted.id}/content").bodyAsBytes()
        return PdfFile(done.filename, bytes)
    }

    private suspend fun versionId(hospId: String, evoId: String?): String =
        clinicalNoteRepository.latestVersionId(hospId, evoId) ?: throw ApiException(ApiError.NotFound(evoId ?: hospId))

    private suspend fun awaitGenerated(exportId: String): PdfExportStatusResponse {
        var waited = 0L
        var pause = polling.initialMillis
        while (true) {
            val status = httpClient.get("/api/v1/pdf-exports/$exportId").body<PdfExportStatusResponse>()
            when (status.status) {
                "COMPLETED" -> return status
                "FAILED" -> throw PdfExportFailedException(status.failureReason)
            }
            if (waited >= polling.timeoutMillis) throw PdfExportTimeoutException()
            delay(pause)
            waited += pause
            pause = (pause * 3 / 2).coerceAtMost(polling.maxMillis)
        }
    }
}
