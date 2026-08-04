package com.cramsan.hirsh.model

/**
 * A single dated checkpoint within a hospitalizacion -- at least one per day. Field names
 * follow the prototype (`prototype/shared/data.js`) so the mapping from the real API
 * response, once it exists, stays obvious.
 */
data class Evolucion(
    val id: String,
    val fecha: String,
    val hora: String,
    val medico: String,
    val vitals: Vitals,
    val diagnosticos: List<DiagnosticoCie10>,
    val subjective: String,
    val objective: String,
    val assessment: String,
    val plan: String,
    val rx: String,
    val pronostico: Pronostico,
    // Named `resultado` rather than the prototype's own field name `evolucion` --
    // `evolucion.evolucion` would be confusing next to the `Evolucion` class itself.
    val resultado: EvolucionResultado,
    val examenes: List<Examen>,
    val examenesObs: String,
)

data class Vitals(
    val pa: String,
    val fc: String,
    val fr: String,
    val temp: String,
    val satO2: String,
    val fio2: String,
)

data class DiagnosticoCie10(
    val codigoCie10: String,
    val descripcion: String,
)

data class Examen(
    val tipo: String,
    val nombre: String,
    val resultado: String,
    val unidad: String,
    val referencia: String,
    val fecha: String,
)

enum class Pronostico { RESERVADO, FAVORABLE, MALO }
enum class EvolucionResultado { ESTACIONARIA, FAVORABLE, DESFAVORABLE }
