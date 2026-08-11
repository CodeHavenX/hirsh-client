package com.cramsan.hirsh.ui.screens.historiaclinica

import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.Plan

enum class HcFieldType { TEXT, SELECT, TEXTAREA }

/**
 * One field in a Historia Clinica section form. [key] may be a dotted path
 * (e.g. `examenRegional.cabezaCuello`) for a nested domain field -- mirrors
 * SECTION_FIELDS' own key/getPath/setPath convention in prototype/shared/data.js,
 * so the generic [key]-keyed draft map used by HistoriaClinicaViewModel needs no
 * per-section shape of its own.
 */
data class HcFieldSpec(
    val key: String,
    val label: String,
    val type: HcFieldType,
    val options: List<String> = emptyList(),
    val rows: Int = 1,
)

/** Ported 1:1 from prototype/shared/data.js's SECTION_FIELDS. Covers every HcSectionKey with a plain field-form shape -- Motivo de Ingreso is handled separately (a checkbox grid, not a field list). */
val HC_SECTION_FIELDS: Map<HcSectionKey, List<HcFieldSpec>> = mapOf(
    HcSectionKey.FILIACION to listOf(
        HcFieldSpec("edad", "Edad", HcFieldType.TEXT),
        HcFieldSpec("fechaNacimiento", "Fecha de nacimiento", HcFieldType.TEXT),
        HcFieldSpec(
            "estadoCivil",
            "Estado civil",
            HcFieldType.SELECT,
            options = listOf("Soltero(a)", "Casado(a)", "Conviviente", "Viudo(a)", "Divorciado(a)"),
        ),
        HcFieldSpec("sexo", "Sexo", HcFieldType.SELECT, options = listOf("Masculino", "Femenino")),
        HcFieldSpec("dni", "DNI", HcFieldType.TEXT),
        HcFieldSpec("gradoInstruccion", "Grado de instruccion", HcFieldType.TEXT),
        HcFieldSpec("ocupacion", "Ocupacion", HcFieldType.TEXT),
        HcFieldSpec("lugarNacimiento", "Lugar de nacimiento", HcFieldType.TEXT),
        HcFieldSpec("lugarProcedencia", "Lugar de procedencia", HcFieldType.TEXT),
        HcFieldSpec("familiarResponsable", "Familiar responsable (nombre y telefono)", HcFieldType.TEXT),
        HcFieldSpec("direccion", "Direccion", HcFieldType.TEXT),
        HcFieldSpec("servicioIngreso", "Servicio de ingreso", HcFieldType.TEXT),
    ),
    HcSectionKey.ENFERMEDAD_ACTUAL to listOf(
        HcFieldSpec("tiempoEnfermedad", "Tiempo de enfermedad", HcFieldType.TEXT),
        HcFieldSpec("formaInicio", "Forma de inicio", HcFieldType.SELECT, options = listOf("Insidioso", "Agudo", "Subagudo")),
        HcFieldSpec("curso", "Curso", HcFieldType.SELECT, options = listOf("Progresivo", "Estacionario", "Remitente")),
        HcFieldSpec("duracionEpisodio", "Duracion del episodio actual", HcFieldType.TEXT),
        HcFieldSpec("relato", "Relato", HcFieldType.TEXTAREA, rows = 5),
    ),
    HcSectionKey.EXAMEN_FISICO to listOf(
        HcFieldSpec("pa", "PA (mmHg)", HcFieldType.TEXT),
        HcFieldSpec("fc", "FC (lpm)", HcFieldType.TEXT),
        HcFieldSpec("fr", "FR (rpm)", HcFieldType.TEXT),
        HcFieldSpec("temp", "T° (°C)", HcFieldType.TEXT),
        HcFieldSpec("peso", "Peso (kg)", HcFieldType.TEXT),
        HcFieldSpec("talla", "Talla (cm)", HcFieldType.TEXT),
        HcFieldSpec("imc", "IMC (kg/m2)", HcFieldType.TEXT),
        HcFieldSpec("estadoGeneral", "Estado general", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("examenRegional.cabezaCuello", "Cabeza y cuello", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("examenRegional.toraxPulmones", "Torax y pulmones", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("examenRegional.corazon", "Corazon", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("examenRegional.abdomen", "Abdomen", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("examenRegional.neurologico", "Neurologico", HcFieldType.TEXTAREA, rows = 2),
    ),
    HcSectionKey.DIAGNOSTICO to listOf(
        HcFieldSpec("ejeI", "Eje I — Trastornos Clinicos-Psiquiatricos", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("ejeII", "Eje II — Desarrollo y Personalidad", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("ejeIII", "Eje III — Enfermedades Fisicas", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("ejeIV", "Eje IV — Estresores Psicosociales", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("ejeV", "Eje V — Evaluacion Global del Funcionamiento (EEAG)", HcFieldType.TEXT),
    ),
    HcSectionKey.PLAN to listOf(
        HcFieldSpec("lugarHospitalizacion", "Lugar de hospitalizacion", HcFieldType.TEXT),
        HcFieldSpec("examenesSolicitados", "Examenes solicitados", HcFieldType.TEXTAREA, rows = 2),
        HcFieldSpec("psicofarmacos", "Via de psicofarmacos", HcFieldType.TEXT),
        HcFieldSpec("evaluacionesSolicitadas", "Evaluaciones solicitadas", HcFieldType.TEXTAREA, rows = 2),
    ),
)

/** Ported 1:1 from prototype/shared/data.js's MOTIVO_INGRESO_OPTIONS. */
data class MotivoIngresoOption(val key: String, val label: String)

val MOTIVO_INGRESO_OPTIONS = listOf(
    MotivoIngresoOption("riesgoSuicida", "Riesgo suicida"),
    MotivoIngresoOption("riesgoHomicida", "Riesgo homicida"),
    MotivoIngresoOption("heteroagresividad", "Heteroagresividad"),
    MotivoIngresoOption("agitacionPsicomotriz", "Agitacion psicomotriz"),
    MotivoIngresoOption("psicosis", "Psicosis"),
    MotivoIngresoOption("adicciones", "Adicciones"),
    MotivoIngresoOption("trastornoAfecto", "Trastorno del afecto"),
    MotivoIngresoOption("tca", "Trastorno de la conducta alimentaria"),
    MotivoIngresoOption("precisionDiagnostica", "Precision diagnostica"),
    MotivoIngresoOption("precisionTerapeutica", "Precision terapeutica"),
    MotivoIngresoOption("otros", "Otros"),
)

/**
 * The persisted section data for [key], as a `HcFieldSpec.key`-keyed map --
 * shared by [HistoriaClinicaViewModel] (form pre-fill) and
 * `HistoriaClinicaPrintable` (HISS-501's read-only summary), so both read the
 * same field<->form-value mapping rather than each deriving their own.
 */
fun fieldMapFor(key: HcSectionKey, historiaClinica: HistoriaClinica): Map<String, String>? = when (key) {
    HcSectionKey.FILIACION -> historiaClinica.filiacion.data?.toFieldMap()
    HcSectionKey.ENFERMEDAD_ACTUAL -> historiaClinica.enfermedadActual.data?.toFieldMap()
    HcSectionKey.EXAMEN_FISICO -> historiaClinica.examenFisico.data?.toFieldMap()
    HcSectionKey.DIAGNOSTICO -> historiaClinica.diagnostico.data?.toFieldMap()
    HcSectionKey.PLAN -> historiaClinica.plan.data?.toFieldMap()
    else -> null
}

private fun Filiacion.toFieldMap(): Map<String, String> = mapOf(
    "edad" to edad,
    "fechaNacimiento" to fechaNacimiento,
    "estadoCivil" to estadoCivil,
    "sexo" to sexo,
    "dni" to dni,
    "gradoInstruccion" to gradoInstruccion,
    "ocupacion" to ocupacion,
    "lugarNacimiento" to lugarNacimiento,
    "lugarProcedencia" to lugarProcedencia,
    "familiarResponsable" to familiarResponsable,
    "direccion" to direccion,
    "servicioIngreso" to servicioIngreso,
)

private fun EnfermedadActual.toFieldMap(): Map<String, String> = mapOf(
    "tiempoEnfermedad" to tiempoEnfermedad,
    "formaInicio" to formaInicio,
    "curso" to curso,
    "duracionEpisodio" to duracionEpisodio,
    "relato" to relato,
)

private fun ExamenFisico.toFieldMap(): Map<String, String> = mapOf(
    "pa" to pa,
    "fc" to fc,
    "fr" to fr,
    "temp" to temp,
    "peso" to peso,
    "talla" to talla,
    "imc" to imc,
    "estadoGeneral" to estadoGeneral,
    "examenRegional.cabezaCuello" to examenRegional.cabezaCuello,
    "examenRegional.toraxPulmones" to examenRegional.toraxPulmones,
    "examenRegional.corazon" to examenRegional.corazon,
    "examenRegional.abdomen" to examenRegional.abdomen,
    "examenRegional.neurologico" to examenRegional.neurologico,
)

private fun Diagnostico.toFieldMap(): Map<String, String> = mapOf(
    "ejeI" to ejeI,
    "ejeII" to ejeII,
    "ejeIII" to ejeIII,
    "ejeIV" to ejeIV,
    "ejeV" to ejeV,
)

private fun Plan.toFieldMap(): Map<String, String> = mapOf(
    "lugarHospitalizacion" to lugarHospitalizacion,
    "examenesSolicitados" to examenesSolicitados,
    "psicofarmacos" to psicofarmacos,
    "evaluacionesSolicitadas" to evaluacionesSolicitadas,
)

/** Shared by [HistoriaClinicaScreen]'s section nav and `HistoriaClinicaPrintable`'s per-section fallback copy. */
fun HistoriaClinica.isSectionComplete(key: HcSectionKey): Boolean = when (key) {
    HcSectionKey.FILIACION -> filiacion.complete
    HcSectionKey.MOTIVO_INGRESO -> motivoIngreso.complete
    HcSectionKey.ENFERMEDAD_ACTUAL -> enfermedadActual.complete
    HcSectionKey.EXAMEN_FISICO -> examenFisico.complete
    HcSectionKey.DIAGNOSTICO -> diagnostico.complete
    HcSectionKey.PLAN -> plan.complete
    else -> false
}

/** Shared by [HistoriaClinicaScreen]'s checkbox grid and `HistoriaClinicaPrintable`'s Motivo de Ingreso summary. */
fun MotivoIngreso.isChecked(key: String): Boolean = when (key) {
    "riesgoSuicida" -> riesgoSuicida
    "riesgoHomicida" -> riesgoHomicida
    "heteroagresividad" -> heteroagresividad
    "agitacionPsicomotriz" -> agitacionPsicomotriz
    "psicosis" -> psicosis
    "adicciones" -> adicciones
    "trastornoAfecto" -> trastornoAfecto
    "tca" -> tca
    "precisionDiagnostica" -> precisionDiagnostica
    "precisionTerapeutica" -> precisionTerapeutica
    "otros" -> otros
    else -> false
}
