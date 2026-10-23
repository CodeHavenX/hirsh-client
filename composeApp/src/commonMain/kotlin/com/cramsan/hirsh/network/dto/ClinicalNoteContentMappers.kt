package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Examen
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Prescripcion
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.model.Vitals

fun DocumentStatusDto.toDomain(): NoteStatus = when (this) {
    DocumentStatusDto.DRAFT -> NoteStatus.DRAFT
    DocumentStatusDto.SIGNED -> NoteStatus.SIGNED
    DocumentStatusDto.VOIDED -> NoteStatus.VOIDED
}

fun Filiacion.toContent() = FiliacionContent(
    edad, fechaNacimiento, estadoCivil, sexo, dni, gradoInstruccion, ocupacion, lugarNacimiento,
    lugarProcedencia, familiarResponsable, direccion, servicioIngreso,
)

fun FiliacionContent.toDomain() = Filiacion(
    edad, fechaNacimiento, estadoCivil, sexo, dni, gradoInstruccion, ocupacion, lugarNacimiento,
    lugarProcedencia, familiarResponsable, direccion, servicioIngreso,
)

fun MotivoIngreso.toContent() = MotivoIngresoContent(
    riesgoSuicida, riesgoHomicida, heteroagresividad, agitacionPsicomotriz, psicosis, adicciones,
    trastornoAfecto, tca, precisionDiagnostica, precisionTerapeutica, otros, otrosDetalle,
)

fun MotivoIngresoContent.toDomain() = MotivoIngreso(
    riesgoSuicida, riesgoHomicida, heteroagresividad, agitacionPsicomotriz, psicosis, adicciones,
    trastornoAfecto, tca, precisionDiagnostica, precisionTerapeutica, otros, otrosDetalle,
)

fun EnfermedadActual.toContent() = EnfermedadActualContent(tiempoEnfermedad, formaInicio, curso, duracionEpisodio, relato)

fun EnfermedadActualContent.toDomain() = EnfermedadActual(tiempoEnfermedad, formaInicio, curso, duracionEpisodio, relato)

fun ExamenFisico.toContent() = ExamenFisicoContent(
    pa, fc, fr, temp, peso, talla, imc, estadoGeneral,
    with(examenRegional) { ExamenRegionalContent(cabezaCuello, toraxPulmones, corazon, abdomen, neurologico) },
)

fun ExamenFisicoContent.toDomain() = ExamenFisico(
    pa, fc, fr, temp, peso, talla, imc, estadoGeneral,
    with(examenRegional) { ExamenRegional(cabezaCuello, toraxPulmones, corazon, abdomen, neurologico) },
)

fun Diagnostico.toContent() = DiagnosticoContent(ejeI, ejeII, ejeIII, ejeIV, ejeV)

fun DiagnosticoContent.toDomain() = Diagnostico(ejeI, ejeII, ejeIII, ejeIV, ejeV)

fun Plan.toContent() = PlanContent(lugarHospitalizacion, examenesSolicitados, psicofarmacos, evaluacionesSolicitadas)

fun PlanContent.toDomain() = Plan(lugarHospitalizacion, examenesSolicitados, psicofarmacos, evaluacionesSolicitadas)

fun Vitals.toContent() = VitalsContent(pa, fc, fr, temp, satO2, fio2)

fun VitalsContent.toDomain() = Vitals(pa, fc, fr, temp, satO2, fio2)

fun DiagnosticoCie10.toContent() = DiagnosticoCie10Content(codigoCie10, descripcion)

fun DiagnosticoCie10Content.toDomain() = DiagnosticoCie10(codigoCie10, descripcion)

fun Examen.toContent() = ExamenContent(tipo, nombre, resultado, unidad, referencia, fecha)

fun ExamenContent.toDomain() = Examen(tipo, nombre, resultado, unidad, referencia, fecha)

fun Prescripcion.toContent() = PrescriptionContent(
    drug = DrugRefContent(drugId, drugName),
    dose = dose,
    frequency = frequency,
    route = route?.name,
    duration = duration,
    instructions = instructions,
)

fun PrescriptionContent.toDomain() = Prescripcion(
    drugId = drug.id,
    drugName = drug.name,
    dose = dose,
    frequency = frequency,
    route = RouteOfAdministration.entries.find { it.name == route },
    duration = duration,
    instructions = instructions,
)

/**
 * Every note this client writes carries both. One written elsewhere without them falls back to
 * the most neutral value rather than failing the whole list.
 */
fun EvolucionContent.pronosticoOrDefault(): Pronostico =
    Pronostico.entries.find { it.name == pronostico } ?: Pronostico.RESERVADO

fun EvolucionContent.resultadoOrDefault(): EvolucionResultado =
    EvolucionResultado.entries.find { it.name == resultado } ?: EvolucionResultado.ESTACIONARIA
