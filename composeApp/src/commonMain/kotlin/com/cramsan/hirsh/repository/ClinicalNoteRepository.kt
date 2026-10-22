package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.Diagnostico
import com.cramsan.hirsh.model.DiagnosticoCie10
import com.cramsan.hirsh.model.EnfermedadActual
import com.cramsan.hirsh.model.Evolucion
import com.cramsan.hirsh.model.EvolucionResultado
import com.cramsan.hirsh.model.Examen
import com.cramsan.hirsh.model.ExamenFisico
import com.cramsan.hirsh.model.ExamenRegional
import com.cramsan.hirsh.model.Filiacion
import com.cramsan.hirsh.model.HcSection
import com.cramsan.hirsh.model.HcSectionKey
import com.cramsan.hirsh.model.HistoriaClinica
import com.cramsan.hirsh.model.MotivoIngreso
import com.cramsan.hirsh.model.NoteStatus
import com.cramsan.hirsh.model.Plan
import com.cramsan.hirsh.model.Pronostico
import com.cramsan.hirsh.model.Vitals
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A hospital stay's Historia Clinica and evoluciones, keyed by its id (HISS-641). On the backend
 * they are clinical documents (HCEM-74): the Historia Clinica is the stay's one ADMISSION document
 * and each evolucion a PROGRESS_NOTE, both created as drafts and frozen once signed.
 *
 * Keyed by the stay id alone: callers resolve the stay through
 * [EpisodeRepository.getHospitalization] (patient and stay together) first, and show nothing from
 * here unless that resolved.
 */
interface ClinicalNoteRepository {
    /** An empty [HistoriaClinica] (null [HistoriaClinica.status]) for a stay with no notes yet, such as one just admitted. */
    fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica>

    /** Newest first. Voided notes are left out. */
    fun getEvoluciones(hospId: String): Flow<List<Evolucion>>

    fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?>

    /**
     * Marks the [key] section complete with [data], creating the stay's Historia Clinica as a
     * draft if it has none yet. [data]'s runtime type must match [key] (e.g.
     * [HcSectionKey.FILIACION] requires a [Filiacion]) -- throws [IllegalArgumentException]
     * otherwise. [key] must be one of the currently implemented sections
     * ([HcSectionKey.implemented]); the other four have no digital form yet.
     *
     * Throws [com.cramsan.hirsh.network.ApiException] wrapping
     * [com.cramsan.hirsh.network.ApiError.Conflict] when someone else saved since this
     * repository last read the document, or when the document is no longer a draft. The flow
     * from [getHistoriaClinica] already shows the newer content by then, so the caller only has
     * to tell the user and let them save again -- nothing is retried over the other edit.
     */
    suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any)

    /** Signs the draft as the signed-in user. Same conflict rule as [saveHistoriaClinicaSection]. */
    suspend fun signHistoriaClinica(hospId: String)

    /** Saves [evolucion] as a new draft; its own `id` and `status` are ignored. */
    suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion

    /** Signs a draft evolucion as the signed-in user. */
    suspend fun signEvolucion(hospId: String, evoId: String)
}

/** Everything [ClinicalNoteRepository] holds for one stay. */
private data class ClinicalNotes(
    val historiaClinica: HistoriaClinica = HistoriaClinica(),
    val evoluciones: List<Evolucion> = emptyList(),
)

/**
 * Seeded with the prototype's notes for [InMemoryEpisodeRepository]'s seeded stays. Any other stay
 * id starts empty, so notes can be written for it. No longer bound since [KtorClinicalNoteRepository]
 * (HCEM-79); kept for tests that need seeded notes. Seeded evoluciones are signed and the seeded
 * Historias Clinicas, all partly filled in, drafts.
 */
class InMemoryClinicalNoteRepository(
    private val clock: Clock,
) : ClinicalNoteRepository {

    private val notes = MutableStateFlow(seedClinicalNotes())

    private fun notesFor(hospId: String): Flow<ClinicalNotes> = notes.map { it[hospId] ?: ClinicalNotes() }

    override fun getHistoriaClinica(hospId: String): Flow<HistoriaClinica> = notesFor(hospId).map { it.historiaClinica }

    override fun getEvoluciones(hospId: String): Flow<List<Evolucion>> = notesFor(hospId).map { it.evoluciones }

    override fun getEvolucion(hospId: String, evoId: String): Flow<Evolucion?> =
        notesFor(hospId).map { notes -> notes.evoluciones.find { it.id == evoId } }

    override suspend fun saveHistoriaClinicaSection(hospId: String, key: HcSectionKey, data: Any) {
        require(key.implemented) { "HistoriaClinica section $key has no digital form yet" }
        update(hospId) { notes ->
            val historiaClinica = notes.historiaClinica
            if (historiaClinica.status != null && historiaClinica.status != NoteStatus.DRAFT) {
                throw ApiException(ApiError.Conflict(hospId))
            }
            notes.copy(historiaClinica = historiaClinica.withSection(key, data).copy(status = NoteStatus.DRAFT))
        }
    }

    override suspend fun signHistoriaClinica(hospId: String) {
        update(hospId) { notes ->
            if (notes.historiaClinica.status != NoteStatus.DRAFT) throw ApiException(ApiError.Conflict(hospId))
            notes.copy(historiaClinica = notes.historiaClinica.copy(status = NoteStatus.SIGNED, draftDeadline = null))
        }
    }

    override suspend fun addEvolucion(hospId: String, evolucion: Evolucion): Evolucion {
        val newEvolucion = evolucion.copy(id = "evo_" + clock.now().toEpochMilliseconds(), status = NoteStatus.DRAFT)
        update(hospId) { it.copy(evoluciones = listOf(newEvolucion) + it.evoluciones) }
        return newEvolucion
    }

    override suspend fun signEvolucion(hospId: String, evoId: String) {
        update(hospId) { notes ->
            val evolucion = notes.evoluciones.find { it.id == evoId } ?: throw ApiException(ApiError.NotFound(evoId))
            if (evolucion.status != NoteStatus.DRAFT) throw ApiException(ApiError.Conflict(evoId))
            notes.copy(evoluciones = notes.evoluciones.map { if (it.id == evoId) it.copy(status = NoteStatus.SIGNED) else it })
        }
    }

    private fun update(hospId: String, transform: (ClinicalNotes) -> ClinicalNotes) {
        notes.update { it + (hospId to transform(it[hospId] ?: ClinicalNotes())) }
    }
}

private fun HistoriaClinica.withSection(key: HcSectionKey, data: Any): HistoriaClinica = when (key) {
    HcSectionKey.FILIACION -> copy(filiacion = HcSection(complete = true, data = data as Filiacion))
    HcSectionKey.MOTIVO_INGRESO -> copy(motivoIngreso = HcSection(complete = true, data = data as MotivoIngreso))
    HcSectionKey.ENFERMEDAD_ACTUAL -> copy(enfermedadActual = HcSection(complete = true, data = data as EnfermedadActual))
    HcSectionKey.EXAMEN_FISICO -> copy(examenFisico = HcSection(complete = true, data = data as ExamenFisico))
    HcSectionKey.DIAGNOSTICO -> copy(diagnostico = HcSection(complete = true, data = data as Diagnostico))
    HcSectionKey.PLAN -> copy(plan = HcSection(complete = true, data = data as Plan))
    HcSectionKey.ANTECEDENTES,
    HcSectionKey.HISTORIA_PERSONAL,
    HcSectionKey.INVENTARIO_SINTOMAS,
    HcSectionKey.EXAMEN_PSICOPATOLOGICO,
    -> error("unreachable: guarded by require(key.implemented) in saveHistoriaClinicaSection")
}

/** Ported 1:1 from prototype/shared/data.js's HOSPITALIZATIONS, keyed by stay id. */
private fun seedClinicalNotes(): Map<String, ClinicalNotes> = mapOf(
    "h_ricaldi_1" to ClinicalNotes(
        historiaClinica = HistoriaClinica(
            status = NoteStatus.DRAFT,
            filiacion = HcSection(
                complete = true,
                data = Filiacion(
                    edad = "13 anos",
                    fechaNacimiento = "15/10/2012",
                    estadoCivil = "Soltera",
                    sexo = "Femenino",
                    dni = "70083906",
                    gradoInstruccion = "Secundaria Incompleta (1ero de secundaria)",
                    ocupacion = "Estudiante",
                    lugarNacimiento = "Hospital Edgardo Rebagliati Martins",
                    lugarProcedencia = "Villa el Salvador",
                    familiarResponsable = "Madre: Sandra Sedano Montalvo · 998984134",
                    direccion = "Manzana I Lote 34 3era etapa urb Pachacamac, Villa el Salvador",
                    servicioIngreso = "Emergencia de Pediatria, luego UHSMA",
                ),
            ),
            motivoIngreso = HcSection(
                complete = true,
                data = MotivoIngreso(
                    riesgoSuicida = false,
                    riesgoHomicida = false,
                    heteroagresividad = true,
                    agitacionPsicomotriz = false,
                    psicosis = true,
                    adicciones = true,
                    trastornoAfecto = false,
                    tca = false,
                    precisionDiagnostica = true,
                    precisionTerapeutica = false,
                    otros = false,
                    otrosDetalle = "",
                ),
            ),
            enfermedadActual = HcSection(
                complete = true,
                data = EnfermedadActual(
                    tiempoEnfermedad = "2 anos",
                    formaInicio = "Insidioso",
                    curso = "Progresivo",
                    duracionEpisodio = "4 dias",
                    relato = "Episodio actual de heteroagresividad hacia los padres, con conducta psicotica emergente y " +
                        "antecedente de consumo de sustancias. Episodios previos descritos por la madre desde hace 2 " +
                        "anos, con empeoramiento progresivo en los ultimos 4 dias.",
                ),
            ),
            examenFisico = HcSection(
                complete = true,
                data = ExamenFisico(
                    pa = "110/70",
                    fc = "88",
                    fr = "18",
                    temp = "36.6",
                    peso = "48",
                    talla = "158",
                    imc = "19.2",
                    estadoGeneral = "Aparente regular estado general. Independiente, funcional con dificultades sociales.",
                    examenRegional = ExamenRegional(
                        cabezaCuello = "Normocefalo. Cuello cilindrico, sin adenopatias.",
                        toraxPulmones = "MV pasa bien por ACP. No ruidos agregados.",
                        corazon = "RCR BI. No soplos.",
                        abdomen = "Blando, depresible. RHA+. No doloroso.",
                        neurologico = "Despierta. OTEP. EG 15. No focalizacion. No signos meningeos.",
                    ),
                ),
            ),
            diagnostico = HcSection(
                complete = true,
                data = Diagnostico(
                    ejeI = "Psicosis aguda (F29.X); D/C Esquizofrenia infantil (F20.0)",
                    ejeII = "D/C Trastorno del Espectro Autista (F84.0)",
                    ejeIII = "—",
                    ejeIV = "Apoyo familiar inadecuado (Z63.2); Social (Z73.4)",
                    ejeV = "EEAG 78%",
                ),
            ),
            plan = HcSection(
                complete = true,
                data = Plan(
                    lugarHospitalizacion = "UHSMA: Area de Damas",
                    examenesSolicitados = "Examenes de laboratorio basales",
                    psicofarmacos = "EV, VO, IM",
                    evaluacionesSolicitadas = "Evaluacion por psicologia",
                ),
            ),
        ),
        evoluciones = emptyList(),
    ),
    "h_mendoza_1" to ClinicalNotes(
        historiaClinica = HistoriaClinica(
            status = NoteStatus.DRAFT,
            filiacion = HcSection(
                complete = true,
                data = Filiacion(
                    edad = "35 anos",
                    fechaNacimiento = "10/08/1990",
                    estadoCivil = "Soltero(a)",
                    sexo = "Masculino",
                    dni = "70567572",
                    gradoInstruccion = "Superior",
                    ocupacion = "Su casa",
                    lugarNacimiento = "Lurin",
                    lugarProcedencia = "San Bartolo, Lima",
                    familiarResponsable = "—",
                    direccion = "Asent.H. San Jose, San Bartolo",
                    servicioIngreso = "Emergencia - Topico de Medicina",
                ),
            ),
        ),
        evoluciones = listOf(
            Evolucion(
                id = "v5c",
                fecha = "18 Jun 2026",
                hora = "22:30",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "115/72", fc = "80", fr = "20", temp = "37.0", satO2 = "95", fio2 = "24"),
                diagnosticos = listOf(
                    DiagnosticoCie10("B59", "Neumonia por Pneumocystis"),
                    DiagnosticoCie10("B24", "VIH Estadio SIDA"),
                ),
                subjective = "Paciente refiere notable mejoria de disnea. Tolera destete progresivo de oxigeno.",
                objective = "PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%. MV mejor ventilado bilateral, crepitos " +
                    "en disminucion.",
                assessment = "Buena respuesta a tratamiento antimicrobiano. Resultado de BK pendiente aun.",
                plan = "Continuar TMP-SMX y corticoides en descenso. Reevaluar destete de O2. Mantener IC " +
                    "Neumologia activa.",
                rx = "TMP-SMX 15mg/kg/dia EV c/8h\nPrednisona 40mg VO c/12h (en descenso)\nO2 por CBN 1L",
                pronostico = Pronostico.FAVORABLE,
                resultado = EvolucionResultado.FAVORABLE,
                examenes = listOf(
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Gasometria arterial - pO2",
                        resultado = "76",
                        unidad = "mmHg",
                        referencia = "80 - 100",
                        fecha = "18 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "BK en esputo (seriado)",
                        resultado = "Pendiente",
                        unidad = "—",
                        referencia = "Negativo",
                        fecha = "18 Jun 2026",
                    ),
                ),
                examenesObs = "Mejoria gasometrica respecto al ingreso. BK de esputo aun pendiente de resultado final.",
            ),
            Evolucion(
                id = "v5",
                fecha = "16 Jun 2026",
                hora = "23:06",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "120/70", fc = "75", fr = "23", temp = "37.6", satO2 = "98", fio2 = "28"),
                diagnosticos = listOf(
                    DiagnosticoCie10("B59", "Neumonia por Pneumocystis"),
                    DiagnosticoCie10("B24", "VIH Estadio SIDA"),
                ),
                subjective = "Paciente aun con apoyo oxigenatorio CBN 2L. Enfermeria niega intercurrencias.",
                objective = "PA 120/70, FC 75, FR 23, SatO2 98% con FiO2 28%. MV pasa en ACP, crepitos difusos. EG 11/15.",
                assessment = "Hemodinamicamente estable. Ventila con apoyo O2. TEM muestra lesion intersticial difusa.",
                plan = "Espera resultados BK. IC Neumologia. Continuar soporte oxigenatorio y ATB.",
                rx = "TMP-SMX 15mg/kg/dia EV c/8h\nPrednisona 40mg VO c/12h\nO2 por CBN a FiO2 28%",
                pronostico = Pronostico.RESERVADO,
                resultado = EvolucionResultado.ESTACIONARIA,
                examenes = listOf(
                    Examen(
                        tipo = "Imagenologia",
                        nombre = "TEM de torax",
                        resultado = "Patron intersticial difuso bilateral",
                        unidad = "—",
                        referencia = "—",
                        fecha = "16 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "BK en esputo (seriado)",
                        resultado = "Pendiente",
                        unidad = "—",
                        referencia = "Negativo",
                        fecha = "16 Jun 2026",
                    ),
                ),
                examenesObs = "TEM de torax con infiltrado intersticial difuso bilateral en \"vidrio esmerilado\", " +
                    "compatible con neumonia por Pneumocystis jirovecii en paciente VIH positivo conocido. Se mantiene " +
                    "en espera de resultado de BK seriado.",
            ),
            Evolucion(
                id = "v5a",
                fecha = "15 Jun 2026",
                hora = "14:00",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "110/70", fc = "88", fr = "26", temp = "38.2", satO2 = "89", fio2 = "21"),
                diagnosticos = listOf(
                    DiagnosticoCie10("B59", "Neumonia por Pneumocystis"),
                    DiagnosticoCie10("B24", "VIH Estadio SIDA"),
                ),
                subjective = "Paciente refiere disnea progresiva y tos seca de 3 dias. Niega fiebre cuantificada en casa.",
                objective = "PA 110/70, FC 88, FR 26, SatO2 89% FiO2 21%. Polipnea, tiraje subcostal leve. MV " +
                    "disminuido difusamente.",
                assessment = "Insuficiencia respiratoria hipoxemica, sospecha PCP en paciente VIH positivo conocido.",
                plan = "Iniciar TMP-SMX EV, corticoides, O2 por CBN. Solicitar TEM torax y serologia. Aislamiento " +
                    "respiratorio.",
                rx = "TMP-SMX 15mg/kg/dia EV c/8h\nPrednisona 40mg VO c/12h\nO2 por CBN 2L",
                pronostico = Pronostico.RESERVADO,
                resultado = EvolucionResultado.DESFAVORABLE,
                examenes = listOf(
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Hemograma - Hemoglobina",
                        resultado = "13.8",
                        unidad = "g/dL",
                        referencia = "13.0 - 17.0",
                        fecha = "15 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Gasometria arterial - pO2",
                        resultado = "58",
                        unidad = "mmHg",
                        referencia = "80 - 100",
                        fecha = "15 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Gasometria arterial - SatO2",
                        resultado = "89",
                        unidad = "%",
                        referencia = "95 - 100",
                        fecha = "15 Jun 2026",
                    ),
                ),
                examenesObs = "Gasometria arterial compatible con insuficiencia respiratoria hipoxemica. Se solicita " +
                    "TEM de torax y serologia VIH confirmatoria.",
            ),
        ),
    ),
    "h_remon_1" to ClinicalNotes(
        historiaClinica = HistoriaClinica(),
        evoluciones = listOf(
            Evolucion(
                id = "v4b",
                fecha = "17 Jun 2026",
                hora = "09:00",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "125/75", fc = "78", fr = "18", temp = "36.6", satO2 = "97", fio2 = "21"),
                diagnosticos = listOf(DiagnosticoCie10("K92.0", "Hemorragia digestiva alta")),
                subjective = "Tolera dieta liquida. Sin nuevos episodios de hematemesis ni melena.",
                objective = "PA 125/75, FC 78. ABD blando, no doloroso. Sin palidez activa.",
                assessment = "VEDA evidencio ulcera duodenal Forrest III, ya tratada endoscopicamente. HB estable en 10.5.",
                plan = "Alta probable en las proximas 24h con omeprazol VO y control ambulatorio por gastroenterologia.",
                rx = "Omeprazol 40mg VO c/24h",
                pronostico = Pronostico.FAVORABLE,
                resultado = EvolucionResultado.FAVORABLE,
                examenes = listOf(
                    Examen(
                        tipo = "Otro",
                        nombre = "VEDA (endoscopia digestiva alta)",
                        resultado = "Ulcera duodenal Forrest III",
                        unidad = "—",
                        referencia = "—",
                        fecha = "17 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Hemograma - Hemoglobina",
                        resultado = "10.5",
                        unidad = "g/dL",
                        referencia = "13.0 - 17.0",
                        fecha = "17 Jun 2026",
                    ),
                ),
                examenesObs = "VEDA evidencio ulcera duodenal Forrest III, tratada endoscopicamente sin complicaciones. " +
                    "Hemoglobina estable en control.",
            ),
            Evolucion(
                id = "v4",
                fecha = "16 Jun 2026",
                hora = "08:40",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "140/70", fc = "91", fr = "20", temp = "36.8", satO2 = "96", fio2 = "21"),
                diagnosticos = listOf(DiagnosticoCie10("K92.0", "Hemorragia digestiva alta")),
                subjective = "Dolor abdominal en disminucion. Niega SAT.",
                objective = "PA 140/70, FC 91. Palidez +/+++. ABD globuloso, leve dolor epigastrio. EG 15/15.",
                assessment = "Paciente hemodinamicamente estable. HB 9.9 macrocitica hipercromica.",
                plan = "IC Gastroenterologia para VEDA. Continuar indicaciones medicas.",
                rx = "Omeprazol 40mg EV c/12h\nNaCl 0.9% 1000cc EV",
                pronostico = Pronostico.FAVORABLE,
                resultado = EvolucionResultado.FAVORABLE,
                examenes = listOf(
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Hemograma - Hemoglobina",
                        resultado = "9.9",
                        unidad = "g/dL",
                        referencia = "13.0 - 17.0",
                        fecha = "16 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Hemograma - VCM",
                        resultado = "102",
                        unidad = "fL",
                        referencia = "80 - 100",
                        fecha = "16 Jun 2026",
                    ),
                ),
                examenesObs = "Anemia macrocitica hipercromica en mejoria respecto al ingreso. Se mantiene IC a " +
                    "Gastroenterologia para VEDA.",
            ),
            Evolucion(
                id = "v4a",
                fecha = "14 Jun 2026",
                hora = "11:00",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "100/60", fc = "105", fr = "22", temp = "36.5", satO2 = "95", fio2 = "21"),
                diagnosticos = listOf(DiagnosticoCie10("K92.0", "Hemorragia digestiva alta")),
                subjective = "Paciente con hematemesis previa al ingreso, aprox 500ml en domicilio. Refiere mareos.",
                objective = "PA 100/60, FC 105, palidez +++. ABD blando, doloroso epigastrio leve.",
                assessment = "HDA activa, hemodinamicamente comprometido al ingreso. Requiere reanimacion con fluidos.",
                plan = "NaCl 0.9% EV, omeprazol EV en bolo, solicitar HB seriada y IC Gastroenterologia urgente.",
                rx = "Omeprazol 80mg EV bolo, luego infusion\nNaCl 0.9% 1000cc EV a chorro",
                pronostico = Pronostico.RESERVADO,
                resultado = EvolucionResultado.ESTACIONARIA,
                examenes = listOf(
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Hemograma - Hemoglobina",
                        resultado = "8.2",
                        unidad = "g/dL",
                        referencia = "13.0 - 17.0",
                        fecha = "14 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Grupo sanguineo y factor Rh",
                        resultado = "O+",
                        unidad = "—",
                        referencia = "—",
                        fecha = "14 Jun 2026",
                    ),
                ),
                examenesObs = "Hemoglobina baja al ingreso, compatible con hemorragia digestiva activa. Se solicita " +
                    "HB seriada y se reserva paquete globular.",
            ),
        ),
    ),
    "h_vasquez_1" to ClinicalNotes(
        historiaClinica = HistoriaClinica(),
        evoluciones = listOf(
            Evolucion(
                id = "v6b",
                fecha = "19 Jun 2026",
                hora = "08:10",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "145/62", fc = "76", fr = "24", temp = "36.8", satO2 = "91", fio2 = "45"),
                diagnosticos = listOf(
                    DiagnosticoCie10("J96.0", "Insuficiencia respiratoria aguda tipo 1"),
                    DiagnosticoCie10("—", "D/C Neoplasia pulmonar vs TBC"),
                ),
                subjective = "Paciente persiste con requerimiento oxigenatorio, refiere leve mejoria de disnea en reposo.",
                objective = "PA 145/62, FC 76, FR 24, SatO2 91% con FiO2 45%. MV persiste disminuido AHT, sin " +
                    "progresion de tirajes.",
                assessment = "Cuadro respiratorio en meseta. BFC programada por Neumologia pendiente de resultados.",
                plan = "Mantener soporte O2, esperar BFC y cultivo de esputo. Reevaluar diariamente.",
                rx = "Ceftriaxona 2g EV c/24h\nAzitromicina 500mg EV c/24h\nO2 por MV a FiO2 45%",
                pronostico = Pronostico.RESERVADO,
                resultado = EvolucionResultado.ESTACIONARIA,
                examenes = listOf(
                    Examen(
                        tipo = "Otro",
                        nombre = "Broncofibroscopia (BFC)",
                        resultado = "Pendiente",
                        unidad = "—",
                        referencia = "—",
                        fecha = "19 Jun 2026",
                    ),
                ),
                examenesObs = "Muestra obtenida por broncofibroscopia enviada a anatomia patologica y cultivo BK; " +
                    "resultado pendiente.",
            ),
            Evolucion(
                id = "v6",
                fecha = "17 Jun 2026",
                hora = "07:50",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "150/60", fc = "73", fr = "23", temp = "36.9", satO2 = "92", fio2 = "50"),
                diagnosticos = listOf(
                    DiagnosticoCie10("J96.0", "Insuficiencia respiratoria aguda tipo 1"),
                    DiagnosticoCie10("—", "D/C Neoplasia pulmonar vs TBC"),
                ),
                subjective = "Paciente refiere mejoria y menor falta de aire. Comoda con MV a 15L.",
                objective = "PA 150/60, FC 73, FR 23, SatO2 92% con FiO2 50%. MV disminuido AHT, uso musculatura " +
                    "accesoria. EG 14/15.",
                assessment = "Hemodinamicamente estable. TEM con multiples lesiones nodulares irregulares bilaterales. " +
                    "BK negativo x2.",
                plan = "BFC por Neumologia. Espera cultivo esputo. Continuar ATB y soporte O2.",
                rx = "Ceftriaxona 2g EV c/24h\nAzitromicina 500mg EV c/24h\nO2 por MV a FiO2 50%",
                pronostico = Pronostico.RESERVADO,
                resultado = EvolucionResultado.ESTACIONARIA,
                examenes = listOf(
                    Examen(
                        tipo = "Imagenologia",
                        nombre = "TEM de torax",
                        resultado = "Multiples lesiones nodulares irregulares bilaterales",
                        unidad = "—",
                        referencia = "—",
                        fecha = "17 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "BK en esputo (1ra muestra)",
                        resultado = "Negativo",
                        unidad = "—",
                        referencia = "Negativo",
                        fecha = "17 Jun 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "BK en esputo (2da muestra)",
                        resultado = "Negativo",
                        unidad = "—",
                        referencia = "Negativo",
                        fecha = "17 Jun 2026",
                    ),
                ),
                examenesObs = "TEM de torax con multiples lesiones nodulares irregulares bilaterales, sugerentes de " +
                    "proceso neoplasico vs TBC miliar. BK seriado negativo en 2 muestras. Se programa broncofibroscopia " +
                    "por Neumologia.",
            ),
            Evolucion(
                id = "v6a",
                fecha = "15 Jun 2026",
                hora = "21:00",
                medico = "Dr. Hirsh",
                vitals = Vitals(pa = "160/65", fc = "80", fr = "26", temp = "37.0", satO2 = "85", fio2 = "21"),
                diagnosticos = listOf(DiagnosticoCie10("J96.0", "Insuficiencia respiratoria aguda tipo 1")),
                subjective = "Paciente refiere disnea progresiva de 1 semana, tos seca, sin fiebre.",
                objective = "PA 160/65, FC 80, FR 26, SatO2 85% FiO2 21%. Uso de musculatura accesoria, MV disminuido " +
                    "difuso AHT.",
                assessment = "Insuficiencia respiratoria aguda hipoxemica. Se inicia soporte y se solicitan estudios " +
                    "de imagen.",
                plan = "O2 por mascara de Venturi a FiO2 35%, TEM torax, IC Neumologia.",
                rx = "O2 por MV a FiO2 35%\nCeftriaxona 2g EV c/24h",
                pronostico = Pronostico.RESERVADO,
                resultado = EvolucionResultado.ESTACIONARIA,
                examenes = emptyList(),
                examenesObs = "",
            ),
        ),
    ),
    "h_gonzalez_1" to ClinicalNotes(
        historiaClinica = HistoriaClinica(
            status = NoteStatus.DRAFT,
            filiacion = HcSection(
                complete = true,
                data = Filiacion(
                    edad = "37 anos",
                    fechaNacimiento = "14/03/1989",
                    estadoCivil = "Casada",
                    sexo = "Femenino",
                    dni = "45678901",
                    gradoInstruccion = "Superior",
                    ocupacion = "Independiente",
                    lugarNacimiento = "Lima",
                    lugarProcedencia = "Lima",
                    familiarResponsable = "—",
                    direccion = "—",
                    servicioIngreso = "Medicina General",
                ),
            ),
            diagnostico = HcSection(
                complete = true,
                data = Diagnostico(
                    ejeI = "—",
                    ejeII = "—",
                    ejeIII = "Bronquitis aguda (J20.9)",
                    ejeIV = "—",
                    ejeV = "—",
                ),
            ),
            plan = HcSection(
                complete = true,
                data = Plan(
                    lugarHospitalizacion = "Medicina General",
                    examenesSolicitados = "—",
                    psicofarmacos = "—",
                    evaluacionesSolicitadas = "—",
                ),
            ),
        ),
        evoluciones = listOf(
            Evolucion(
                id = "v1",
                fecha = "12 Abr 2026",
                hora = "10:15",
                medico = "Dr. Patel",
                vitals = Vitals(pa = "118/76", fc = "78", fr = "18", temp = "37.8", satO2 = "98", fio2 = "21"),
                diagnosticos = listOf(DiagnosticoCie10("J20.9", "Bronquitis aguda")),
                subjective = "Tos productiva por 5 dias, esputo amarillo-verdoso. Fiebre baja. Sin dolor toracico ni " +
                    "disnea en reposo.",
                objective = "T 37.8C, FR 18, SpO2 98%. Crepitos dispersos base derecha; sin sibilancias. Garganta " +
                    "levemente inyectada.",
                assessment = "Bronquitis aguda, probablemente bacteriana dado el esputo y hallazgos focales.",
                plan = "Antibioticos + cuidados de soporte. Revision en 7 dias o antes si empeora.",
                rx = "Amoxicilina 500mg — 1 capsula tres veces al dia — 7 dias\nParacetamol 500mg — PRN para " +
                    "fiebre, max 4x/dia",
                pronostico = Pronostico.FAVORABLE,
                resultado = EvolucionResultado.FAVORABLE,
                examenes = emptyList(),
                examenesObs = "",
            ),
        ),
    ),
    "h_gonzalez_2" to ClinicalNotes(
        historiaClinica = HistoriaClinica(),
        evoluciones = listOf(
            Evolucion(
                id = "v2",
                fecha = "02 Feb 2026",
                hora = "09:30",
                medico = "Dr. Patel",
                vitals = Vitals(pa = "138/88", fc = "72", fr = "16", temp = "36.5", satO2 = "99", fio2 = "21"),
                diagnosticos = listOf(DiagnosticoCie10("I10", "Hipertension arterial")),
                subjective = "Paciente refiere adherencia al tratamiento. Niega cefalea, mareos o vision borrosa.",
                objective = "PA 138/88 mmHg, FC 72 lpm. Examen cardiovascular sin alteraciones.",
                assessment = "HTA en control parcial. Buen cumplimiento terapeutico.",
                plan = "Continuar Lisinopril 10mg OD. Control en 3 meses. Dieta hiposodica.",
                rx = "Lisinopril 10mg — 1 tableta cada manana — continuo",
                pronostico = Pronostico.FAVORABLE,
                resultado = EvolucionResultado.FAVORABLE,
                examenes = listOf(
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Creatinina serica",
                        resultado = "0.8",
                        unidad = "mg/dL",
                        referencia = "0.6 - 1.2",
                        fecha = "02 Feb 2026",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Potasio serico",
                        resultado = "4.3",
                        unidad = "mEq/L",
                        referencia = "3.5 - 5.0",
                        fecha = "02 Feb 2026",
                    ),
                ),
                examenesObs = "Funcion renal y electrolitos dentro de limites normales en control de paciente con " +
                    "tratamiento antihipertensivo.",
            ),
        ),
    ),
    "h_gonzalez_3" to ClinicalNotes(
        historiaClinica = HistoriaClinica(),
        evoluciones = listOf(
            Evolucion(
                id = "v3",
                fecha = "15 Nov 2025",
                hora = "08:45",
                medico = "Dr. Reyes",
                vitals = Vitals(pa = "125/78", fc = "68", fr = "16", temp = "36.5", satO2 = "99", fio2 = "21"),
                diagnosticos = listOf(DiagnosticoCie10("Z00.0", "Control de salud de rutina")),
                subjective = "Paciente acude a control anual. Sin quejas actuales.",
                objective = "PA 125/78, FC 68, T 36.5. Examen fisico dentro de limites normales.",
                assessment = "Paciente en buen estado general. Sin hallazgos patologicos.",
                plan = "Solicitar hemograma, perfil lipidico, glucosa. Proximo control en 1 ano.",
                rx = "—",
                pronostico = Pronostico.FAVORABLE,
                resultado = EvolucionResultado.FAVORABLE,
                examenes = listOf(
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Hemograma completo",
                        resultado = "Normal",
                        unidad = "—",
                        referencia = "—",
                        fecha = "15 Nov 2025",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Perfil lipidico - Colesterol total",
                        resultado = "188",
                        unidad = "mg/dL",
                        referencia = "< 200",
                        fecha = "15 Nov 2025",
                    ),
                    Examen(
                        tipo = "Laboratorio",
                        nombre = "Glucosa en ayunas",
                        resultado = "92",
                        unidad = "mg/dL",
                        referencia = "70 - 100",
                        fecha = "15 Nov 2025",
                    ),
                ),
                examenesObs = "Resultados de control anual dentro de parametros normales. Se recomienda mantener " +
                    "habitos saludables y control en 1 ano.",
            ),
        ),
    ),
)
