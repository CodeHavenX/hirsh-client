package com.cramsan.hirsh.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One entry of `GET /api/v1/document-templates` -- schema `TemplateCard`. */
@Serializable
data class TemplateCard(
    val id: String,
    val code: String,
    val category: DocumentCategoryDto,
)

/** Body of `POST /api/v1/episodes/{episodeId}/documents` -- schema `CreateDocumentRequest`. `documentDate` is left to the server's clock. */
@Serializable
data class CreateDocumentRequest(
    val templateId: String,
    val initialContent: JsonObject,
)

/**
 * One entry of `GET /api/v1/episodes/{episodeId}/documents` -- schema `DocumentFolderEntry`.
 * [createdBy] and [latestVersionDetail] come only with `includeContent=true` (HCEM-77); a backend
 * without it leaves them out and the document has to be read on its own.
 */
@Serializable
data class DocumentFolderEntry(
    val id: String,
    val episodeId: String,
    val template: TemplateSummary,
    val documentStatus: DocumentStatusDto,
    val documentDate: String,
    val createdBy: UserRefDto? = null,
    val latestVersionDetail: DocumentVersionDetail? = null,
)

/** 200/201 body of the single-document endpoints -- schema `DocumentDetail`. */
@Serializable
data class DocumentDetail(
    val id: String,
    val episodeId: String,
    val template: TemplateSummary,
    val documentStatus: DocumentStatusDto,
    val documentDate: String,
    val versionHistory: List<VersionTrailEntry>,
    val latestVersion: DocumentVersionDetail,
)

/** Schema `TemplateSummary`. */
@Serializable
data class TemplateSummary(val id: String, val code: String, val category: DocumentCategoryDto)

/** Schema `VersionTrailEntry`, oldest first. Only the author of version 1 is mapped. */
@Serializable
data class VersionTrailEntry(
    val versionNumber: Int,
    val createdBy: UserRefDto? = null,
)

/**
 * Schema `DocumentVersionDetail`. [audit]'s `jpaVersion` is the lock token autosave and sign check
 * (hirsh#16 documents it); [content] is the form's answers, shaped like the `*Content` classes below.
 */
@Serializable
data class DocumentVersionDetail(
    val id: String,
    val content: JsonObject,
    val draftDeadline: String? = null,
    val audit: AuditBlock,
)

/** Body of `PUT /api/v1/document-versions/{versionId}/draft` -- schema `AutosaveDraftRequest`. Replaces the whole content. */
@Serializable
data class AutosaveDraftRequest(val content: JsonObject, val jpaVersion: Long)

/** 200 body of the autosave endpoint -- schema `AutosaveDraftResponse`. */
@Serializable
data class AutosaveDraftResponse(val jpaVersion: Long)

/** Body of `POST /api/v1/document-versions/{versionId}/sign` -- schema `SignDraftRequest`. */
@Serializable
data class SignDraftRequest(val jpaVersion: Long)

/** Wire values of the backend's `DocumentTemplateCategory`; only the two this client writes are used. */
@Serializable
enum class DocumentCategoryDto {
    ADMISSION,
    PROGRESS_NOTE,
    DISCHARGE_SUMMARY,
    INTERCONSULTATION,
    NURSING_NOTE,
    CONSENT,
    PSYCHOLOGICAL_REPORT,
    OTHER,
}

/** Wire values match [com.cramsan.hirsh.model.NoteStatus]'s constant names exactly (the backend's `DocumentStatus`). */
@Serializable
enum class DocumentStatusDto { DRAFT, SIGNED, VOIDED }

// --- Content ---------------------------------------------------------------------------------
// The seeded HC_PSIQ / EVOL_PSIQ templates (hirsh#17) use the client models' own field names.
// Every field has a default so a section written by an older or newer form still decodes.

@Serializable
data class FiliacionContent(
    val edad: String = "",
    val fechaNacimiento: String = "",
    val estadoCivil: String = "",
    val sexo: String = "",
    val dni: String = "",
    val gradoInstruccion: String = "",
    val ocupacion: String = "",
    val lugarNacimiento: String = "",
    val lugarProcedencia: String = "",
    val familiarResponsable: String = "",
    val direccion: String = "",
    val servicioIngreso: String = "",
)

@Serializable
data class MotivoIngresoContent(
    val riesgoSuicida: Boolean = false,
    val riesgoHomicida: Boolean = false,
    val heteroagresividad: Boolean = false,
    val agitacionPsicomotriz: Boolean = false,
    val psicosis: Boolean = false,
    val adicciones: Boolean = false,
    val trastornoAfecto: Boolean = false,
    val tca: Boolean = false,
    val precisionDiagnostica: Boolean = false,
    val precisionTerapeutica: Boolean = false,
    val otros: Boolean = false,
    val otrosDetalle: String = "",
)

@Serializable
data class EnfermedadActualContent(
    val tiempoEnfermedad: String = "",
    val formaInicio: String = "",
    val curso: String = "",
    val duracionEpisodio: String = "",
    val relato: String = "",
)

@Serializable
data class ExamenFisicoContent(
    val pa: String = "",
    val fc: String = "",
    val fr: String = "",
    val temp: String = "",
    val peso: String = "",
    val talla: String = "",
    val imc: String = "",
    val estadoGeneral: String = "",
    val examenRegional: ExamenRegionalContent = ExamenRegionalContent(),
)

@Serializable
data class ExamenRegionalContent(
    val cabezaCuello: String = "",
    val toraxPulmones: String = "",
    val corazon: String = "",
    val abdomen: String = "",
    val neurologico: String = "",
)

@Serializable
data class DiagnosticoContent(
    val ejeI: String = "",
    val ejeII: String = "",
    val ejeIII: String = "",
    val ejeIV: String = "",
    val ejeV: String = "",
)

@Serializable
data class PlanContent(
    val lugarHospitalizacion: String = "",
    val examenesSolicitados: String = "",
    val psicofarmacos: String = "",
    val evaluacionesSolicitadas: String = "",
)

/** [pronostico]/[resultado] carry the client enums' constant names. */
@Serializable
data class EvolucionContent(
    val vitals: VitalsContent = VitalsContent(),
    val diagnosticos: List<DiagnosticoCie10Content> = emptyList(),
    val subjective: String = "",
    val objective: String = "",
    val assessment: String = "",
    val plan: String = "",
    val rx: String = "",
    val pronostico: String? = null,
    val resultado: String? = null,
    val examenes: List<ExamenContent> = emptyList(),
    val examenesObs: String = "",
    val prescriptions: List<PrescriptionContent> = emptyList(),
)

/**
 * One formulary prescription (EVOL_PSIQ v2). The backend reads these paths when the evolucion is
 * signed and records a treatment, skipping any item without a drug name, dose and frequency;
 * [route] is a `RouteOfAdministration` name.
 */
@Serializable
data class PrescriptionContent(
    val drug: DrugRefContent = DrugRefContent(),
    val dose: String = "",
    val frequency: String = "",
    val route: String? = null,
    val duration: String = "",
    val instructions: String = "",
)

@Serializable
data class DrugRefContent(val id: String? = null, val name: String = "")

@Serializable
data class VitalsContent(
    val pa: String = "",
    val fc: String = "",
    val fr: String = "",
    val temp: String = "",
    val satO2: String = "",
    val fio2: String = "",
)

/** The EVOL_PSIQ extraction rule copies these into the backend's diagnosis table on signature; a blank code is skipped there. */
@Serializable
data class DiagnosticoCie10Content(val codigoCie10: String = "", val descripcion: String = "")

@Serializable
data class ExamenContent(
    val tipo: String = "",
    val nombre: String = "",
    val resultado: String = "",
    val unidad: String = "",
    val referencia: String = "",
    val fecha: String = "",
)
