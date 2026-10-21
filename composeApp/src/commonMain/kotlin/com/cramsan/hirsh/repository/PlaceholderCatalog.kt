package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef

/**
 * Stand-in services and physicians, with fixed ids, for the in-memory hospitalizations and the
 * admission form until the real pickers exist (`GET /clinical-services`, `GET /users`: HISS-634).
 * The names are the prototype's own option lists.
 */
object PlaceholderCatalog {
    val services: List<ServiceRef> = listOf(
        ServiceRef("5a1c0b6e-0001-4e2a-9c1d-000000000001", "Emergencia - Topico de Medicina"),
        ServiceRef("5a1c0b6e-0001-4e2a-9c1d-000000000002", "Medicina General"),
        ServiceRef("5a1c0b6e-0001-4e2a-9c1d-000000000003", "Psiquiatria (UHSMA)"),
        ServiceRef("5a1c0b6e-0001-4e2a-9c1d-000000000004", "Pediatria"),
        ServiceRef("5a1c0b6e-0001-4e2a-9c1d-000000000005", "Gineco-Obstetricia"),
        ServiceRef("5a1c0b6e-0001-4e2a-9c1d-000000000006", "Cirugia"),
    )

    val physicians: List<UserRef> = listOf(
        UserRef("7d2e1f3a-0002-4b3c-8d4e-000000000001", "Dr. Patel"),
        UserRef("7d2e1f3a-0002-4b3c-8d4e-000000000002", "Dr. Reyes"),
        UserRef("7d2e1f3a-0002-4b3c-8d4e-000000000003", "Dr. Lin"),
        UserRef("7d2e1f3a-0002-4b3c-8d4e-000000000004", "Dr. Hirsh"),
    )

    fun service(name: String): ServiceRef = services.first { it.name == name }

    fun physician(fullName: String): UserRef = physicians.first { it.fullName == fullName }

    /** Beds are still typed by number on the admission form, so the id is derived from it. */
    fun bed(number: String): BedRef = BedRef(id = "bed-$number", number = number)
}
