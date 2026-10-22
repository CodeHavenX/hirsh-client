package com.cramsan.hirsh.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

private const val PATIENT_ID = "07c98942-3654-4034-b960-f3265814e214"
private const val EPISODE_ID = "8d1c3a52-0a4e-4f7e-9d61-2b7f1f3c9e10"
private const val NOTE_ID = "b41f7e0c-2d6a-4c1e-8f3b-5a9d0e7c6b21"

class RoutesTest {

    @Test
    fun `backend UUIDs go into every route unchanged`() {
        assertEquals("patient_record/$PATIENT_ID", Routes.patientRecord(PATIENT_ID))
        assertEquals("patient_edit/$PATIENT_ID", Routes.patientEdit(PATIENT_ID))
        assertEquals("patient_history/$PATIENT_ID", Routes.patientHistory(PATIENT_ID))
        assertEquals("admision/$PATIENT_ID", Routes.admision(PATIENT_ID))
        assertEquals("hospitalization/$PATIENT_ID/$EPISODE_ID", Routes.hospitalization(PATIENT_ID, EPISODE_ID))
        assertEquals("discharge/$PATIENT_ID/$EPISODE_ID", Routes.discharge(PATIENT_ID, EPISODE_ID))
        assertEquals("historia_clinica/$PATIENT_ID/$EPISODE_ID", Routes.historiaClinica(PATIENT_ID, EPISODE_ID))
        assertEquals("evolucion_new/$PATIENT_ID/$EPISODE_ID", Routes.evolucionNew(PATIENT_ID, EPISODE_ID))
        assertEquals(
            "evolucion_view/$PATIENT_ID/$EPISODE_ID/$NOTE_ID",
            Routes.evolucionView(PATIENT_ID, EPISODE_ID, NOTE_ID),
        )
    }

    @Test
    fun `an id with reserved URI characters is percent-encoded and decodes back`() {
        val route = Routes.patientRecord("#00142/a b")

        assertEquals("patient_record/%2300142%2Fa%20b", route)
        assertEquals("#00142/a b", decodeRouteSegment(route.removePrefix("patient_record/")))
    }

    @Test
    fun `decoding a UUID is a no-op`() {
        assertEquals(PATIENT_ID, decodeRouteSegment(PATIENT_ID))
    }
}
