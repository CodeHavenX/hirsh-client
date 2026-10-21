package com.cramsan.hirsh.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HospitalizationTest {

    private val episode = Hospitalizacion(
        id = "h1",
        patientId = "p1",
        currentService = ServiceRef("s1", "Medicina General"),
        currentBed = BedRef("b1", "08"),
        attendingPhysician = UserRef("u1", "Dr. Patel"),
        startDate = Instant.parse("2026-04-10T09:00:00Z"),
        endDate = null,
        admissionReason = null,
        status = EpisodeStatus.ACTIVE,
        historiaClinica = HistoriaClinica(),
        evoluciones = emptyList(),
    )

    @Test
    fun `every status has its own label, including the third TRANSFERRED state`() {
        assertEquals("Activa", EpisodeStatus.ACTIVE.toDisplayLabel())
        assertEquals("Alta", EpisodeStatus.CLOSED.toDisplayLabel())
        assertEquals("Transferida", EpisodeStatus.TRANSFERRED.toDisplayLabel())
    }

    @Test
    fun `display names come from the references`() {
        assertEquals("Medicina General", episode.serviceName)
        assertEquals("08", episode.bedNumber)
        assertEquals("Dr. Patel", episode.physicianName)
    }

    @Test
    fun `a missing reference shows as a dash`() {
        val bare = episode.copy(currentService = null, currentBed = null, attendingPhysician = null)

        assertEquals("—", bare.serviceName)
        assertEquals("—", bare.bedNumber)
        assertEquals("—", bare.physicianName)
    }
}
