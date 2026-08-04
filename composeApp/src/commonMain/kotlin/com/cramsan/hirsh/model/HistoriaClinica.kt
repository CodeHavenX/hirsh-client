package com.cramsan.hirsh.model

/**
 * Filled in section-by-section (often over several days) rather than all at once.
 * Field names follow the prototype (`prototype/shared/data.js`'s SECTION_FIELDS) so
 * the mapping from the real API response, once it exists, stays obvious.
 */
data class HistoriaClinica(
    val filiacion: HcSection<Filiacion> = HcSection(),
    val motivoIngreso: HcSection<MotivoIngreso> = HcSection(),
    val enfermedadActual: HcSection<EnfermedadActual> = HcSection(),
    val examenFisico: HcSection<ExamenFisico> = HcSection(),
    val diagnostico: HcSection<Diagnostico> = HcSection(),
    val plan: HcSection<Plan> = HcSection(),
)

data class HcSection<T>(
    val complete: Boolean = false,
    val data: T? = null,
)

data class Filiacion(
    val edad: String,
    val fechaNacimiento: String,
    val estadoCivil: String,
    val sexo: String,
    val dni: String,
    val gradoInstruccion: String,
    val ocupacion: String,
    val lugarNacimiento: String,
    val lugarProcedencia: String,
    val familiarResponsable: String,
    val direccion: String,
    val servicioIngreso: String,
)

data class MotivoIngreso(
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

data class EnfermedadActual(
    val tiempoEnfermedad: String,
    val formaInicio: String,
    val curso: String,
    val duracionEpisodio: String,
    val relato: String,
)

data class ExamenFisico(
    val pa: String,
    val fc: String,
    val fr: String,
    val temp: String,
    val peso: String,
    val talla: String,
    val imc: String,
    val estadoGeneral: String,
    val examenRegional: ExamenRegional,
)

data class ExamenRegional(
    val cabezaCuello: String,
    val toraxPulmones: String,
    val corazon: String,
    val abdomen: String,
    val neurologico: String,
)

data class Diagnostico(
    val ejeI: String,
    val ejeII: String,
    val ejeIII: String,
    val ejeIV: String,
    val ejeV: String,
)

data class Plan(
    val lugarHospitalizacion: String,
    val examenesSolicitados: String,
    val psicofarmacos: String,
    val evaluacionesSolicitadas: String,
)

/**
 * The document's full, fixed shape -- including the 4 sections with no digital form yet
 * (`implemented = false`) -- so a section-nav UI can show the whole outline even though
 * only part of it is fillable today. Order matches the prototype's HC_SECTIONS.
 */
enum class HcSectionKey(val label: String, val implemented: Boolean) {
    FILIACION("Filiación", true),
    MOTIVO_INGRESO("Motivo de Ingreso", true),
    ENFERMEDAD_ACTUAL("Enfermedad Actual", true),
    ANTECEDENTES("Antecedentes", false),
    HISTORIA_PERSONAL("Historia Personal", false),
    EXAMEN_FISICO("Examen Físico", true),
    INVENTARIO_SINTOMAS("Inventario de Síntomas", false),
    EXAMEN_PSICOPATOLOGICO("Examen Psicopatológico", false),
    DIAGNOSTICO("Diagnóstico", true),
    PLAN("Plan de Trabajo", true),
}
