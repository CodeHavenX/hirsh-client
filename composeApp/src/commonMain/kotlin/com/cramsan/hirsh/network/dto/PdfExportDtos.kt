package com.cramsan.hirsh.network.dto

import kotlinx.serialization.Serializable

/** Body of `POST /api/v1/pdf-exports` -- schema `CreatePdfExportRequest`. */
@Serializable
data class CreatePdfExportRequest(
    val type: String,
    val episodeId: String,
    val documentVersionId: String? = null,
)

/** `202` body of `POST /api/v1/pdf-exports` -- schema `PdfExportAcceptedResponse`. */
@Serializable
data class PdfExportAcceptedResponse(
    val id: String,
    val status: String,
)

/** `GET /api/v1/pdf-exports/{id}` -- schema `PdfExportStatusResponse`. */
@Serializable
data class PdfExportStatusResponse(
    val id: String,
    val status: String,
    val filename: String,
    val failureReason: String? = null,
)
