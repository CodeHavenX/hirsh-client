package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.AllergyType
import com.cramsan.hirsh.model.DocumentType
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.Severity
import com.cramsan.hirsh.model.Sex
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val PATIENT_ID = "07c98942-3654-4034-b960-f3265814e214"

/** A different seeded patient, with no allergies of its own. */
private const val OTHER_PATIENT_ID = "a21f8bfa-299c-42db-9681-c84f87a90ce4"

/** [PATIENT_ID]'s seeded Penicilina allergy. */
private const val PENICILINA_ID = "5b0f6c52-3a8e-4f4e-9d62-1c7a2e8b9f10"

class PatientRepositoryTest {

    @Test
    fun `updatePatient with one changed field logs exactly that field`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "999-999-999"),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        repository.getChangeLog(PATIENT_ID).test {
            val entries = awaitItem()
            val newest = entries.first()
            assertEquals("apatel", newest.changedBy)
            assertEquals("01 Ene 2027", newest.fecha)
            assertEquals("10:00", newest.hora)
            assertEquals(1, newest.fields.size)
            assertEquals("phone", newest.fields.single().field)
            assertEquals("Telefono de contacto", newest.fields.single().label)
            assertEquals(current.phone, newest.fields.single().oldValue)
            assertEquals("999-999-999", newest.fields.single().newValue)
        }
    }

    @Test
    fun `updatePatient with several changed fields groups them into one entry`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "999-999-999", bloodType = "AB-"),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        repository.getChangeLog(PATIENT_ID).test {
            val newest = awaitItem().first()
            assertEquals(2, newest.fields.size)
            assertEquals(setOf("phone", "bloodType"), newest.fields.map { it.field }.toSet())
        }
    }

    @Test
    fun `updatePatient with no actual changes does not append a log entry`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        // Seed fixture: 07c98942-3654-4034-b960-f3265814e214 starts with exactly 2 historical entries (see the
        // "seeded patients have their prototype change history" test below).
        repository.getChangeLog(PATIENT_ID).test {
            assertEquals(2, awaitItem().size)
        }
    }

    @Test
    fun `sequential updates prepend newest first`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "111-111-111"),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "09:00",
        )
        // Re-reads after the first update, not the original current -- a real caller reloads
        // between edits too, and reusing the pre-update snapshot's jpaVersion here would trip
        // the same stale-version conflict this ticket's `updatePatient throws ApiException...`
        // test below covers on purpose.
        val afterFirstUpdate = repository.patients.value.first { it.id == PATIENT_ID }
        repository.updatePatient(
            id = PATIENT_ID,
            newValues = afterFirstUpdate.copy(phone = "222-222-222"),
            changedBy = "mreyes",
            fecha = "02 Ene 2027",
            hora = "10:00",
        )

        repository.getChangeLog(PATIENT_ID).test {
            val entries = awaitItem()
            assertEquals("mreyes", entries[0].changedBy)
            assertEquals("apatel", entries[1].changedBy)
        }
    }

    @Test
    fun `getChangeLog for a patient with no history emits an empty list`() = runTest {
        val repository = InMemoryPatientRepository()

        repository.getChangeLog("1e0697d0-f3e4-4755-85ad-d888d2b15f9d").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `seeded patients have their prototype change history`() = runTest {
        val repository = InMemoryPatientRepository()

        repository.getChangeLog("07c98942-3654-4034-b960-f3265814e214").test {
            val entries = awaitItem()
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.changedBy == "apatel" && it.fields.single().field == "allergies" })
            assertTrue(entries.any { it.changedBy == "mreyes" && it.fields.single().field == "phone" })
        }
        repository.getChangeLog("caaedede-5d72-4a52-bb72-7532d7cdda83").test {
            val entries = awaitItem()
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.changedBy == "admin" && it.fields.single().field == "district" })
            assertTrue(entries.any { it.changedBy == "mreyes" && it.fields.single().field == "bloodType" })
        }
        repository.getChangeLog("a21f8bfa-299c-42db-9681-c84f87a90ce4").test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            assertEquals("documentNumber", entries.single().fields.single().field)
        }
    }

    @Test
    fun `updatePatient bumps jpaVersion on a successful save`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }
        assertEquals(0L, current.jpaVersion)

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "999-999-999"),
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        assertEquals(1L, repository.patients.value.first { it.id == PATIENT_ID }.jpaVersion)
    }

    @Test
    fun `updatePatient against a stale jpaVersion throws ApiException wrapping Conflict, without saving`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        val exception = assertFailsWith<ApiException> {
            repository.updatePatient(
                id = PATIENT_ID,
                newValues = current.copy(phone = "999-999-999", jpaVersion = current.jpaVersion + 1),
                changedBy = "apatel",
                fecha = "01 Ene 2027",
                hora = "10:00",
            )
        }

        assertEquals(ApiError.Conflict(PATIENT_ID), exception.error)
        assertEquals(current.phone, repository.patients.value.first { it.id == PATIENT_ID }.phone)
    }

    @Test
    fun `updatePatient is a no-op for an unknown patient id`() = runTest {
        val repository = InMemoryPatientRepository()
        val bogus = Patient(
            id = "does-not-exist",
            medicalRecordNumber = "HC-00000",
            documentType = DocumentType.NID,
            documentNumber = "00000000",
            fullName = "Nobody",
            birthDate = "01/01/2000",
            phone = "000-000-000",
            bloodType = "O+",
            sex = Sex.MALE,
        )

        repository.updatePatient(
            id = "does-not-exist",
            newValues = bogus,
            changedBy = "apatel",
            fecha = "01 Ene 2027",
            hora = "10:00",
        )

        assertTrue(repository.patients.value.none { it.id == "does-not-exist" })
    }

    @Test
    fun `addPatient generates a fresh UUID id and assembles fullName from the name parts`() = runTest {
        val repository = InMemoryPatientRepository()

        val created = repository.addPatient(
            medicalRecordNumber = "HC-2027-000001",
            firstName = "Nuevo",
            lastName = "Paciente",
            secondLastName = "Segundo",
            documentType = DocumentType.NID,
            documentNumber = "11223344",
            birthDate = "01/01/2000",
            phone = "999-999-999",
            sex = Sex.MALE,
            bloodType = "O+",
        )

        assertTrue(repository.patients.value.none { it !== created && it.id == created.id }, "id must be unique")
        assertEquals("HC-2027-000001", created.medicalRecordNumber)
        assertEquals("Nuevo Paciente Segundo", created.fullName)
    }

    @Test
    fun `addPatient appends the new patient to the patient list`() = runTest {
        val repository = InMemoryPatientRepository()
        val beforeCount = repository.patients.value.size

        val created = repository.addPatient(
            medicalRecordNumber = "HC-2027-000001",
            firstName = "Nuevo",
            lastName = "Paciente",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "11223344",
            birthDate = "01/01/2000",
            phone = "999-999-999",
            sex = Sex.MALE,
            bloodType = "O+",
        )

        assertEquals(beforeCount + 1, repository.patients.value.size)
        assertTrue(repository.patients.value.contains(created))
    }

    @Test
    fun `sequential addPatient calls each get a unique id`() = runTest {
        val repository = InMemoryPatientRepository()

        val first = repository.addPatient(
            medicalRecordNumber = "HC-2027-000001",
            firstName = "Primero",
            lastName = "Uno",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "11111111",
            birthDate = "01/01/2000",
            phone = "111-111-111",
            sex = Sex.MALE,
            bloodType = "O+",
        )
        val second = repository.addPatient(
            medicalRecordNumber = "HC-2027-000002",
            firstName = "Segundo",
            lastName = "Dos",
            secondLastName = "",
            documentType = DocumentType.NID,
            documentNumber = "22222222",
            birthDate = "01/01/2000",
            phone = "222-222-222",
            sex = Sex.FEMALE,
            bloodType = "O+",
        )

        assertTrue(first.id != second.id)
    }

    // --- allergies (HISS-623) ---------------------------------------------------------------

    private fun InMemoryPatientRepository.allergiesOf(patientId: String) =
        patients.value.single { it.id == patientId }.allergies

    @Test
    fun `addAllergy prepends the new allergy, trimmed, newest first`() = runTest {
        val repository = InMemoryPatientRepository()

        val added = repository.addAllergy(PATIENT_ID, AllergyType.FOOD, "  Mariscos ", severity = null, observations = "")

        assertEquals("Mariscos", added.description)
        assertEquals(listOf(added.id, PENICILINA_ID), repository.allergiesOf(PATIENT_ID).map { it.id })
    }

    @Test
    fun `addAllergy for an unknown patient throws NotFound`() = runTest {
        val repository = InMemoryPatientRepository()

        val error = assertFailsWith<ApiException> {
            repository.addAllergy("does-not-exist", AllergyType.FOOD, "Mariscos", severity = null, observations = "")
        }
        assertIs<ApiError.NotFound>(error.error)
    }

    @Test
    fun `updateAllergy revises only severity and observations`() = runTest {
        val repository = InMemoryPatientRepository()

        repository.updateAllergy(PATIENT_ID, PENICILINA_ID, Severity.MILD, "Revisado")

        val updated = repository.allergiesOf(PATIENT_ID).single()
        assertEquals(Severity.MILD, updated.severity)
        assertEquals("Revisado", updated.observations)
        assertEquals(AllergyType.MEDICATION, updated.allergyType)
        assertEquals("Penicilina", updated.description)
    }

    @Test
    fun `updateAllergy under the wrong patient throws NotFound and leaves the real owner untouched`() = runTest {
        val repository = InMemoryPatientRepository()

        val error = assertFailsWith<ApiException> {
            repository.updateAllergy(OTHER_PATIENT_ID, PENICILINA_ID, Severity.MILD, "")
        }
        assertIs<ApiError.NotFound>(error.error)
        assertEquals(Severity.SEVERE, repository.allergiesOf(PATIENT_ID).single().severity)
    }

    @Test
    fun `deleteAllergy removes only that allergy`() = runTest {
        val repository = InMemoryPatientRepository()
        val added = repository.addAllergy(PATIENT_ID, AllergyType.FOOD, "Mariscos", severity = null, observations = "")

        repository.deleteAllergy(PATIENT_ID, PENICILINA_ID)

        assertEquals(listOf(added.id), repository.allergiesOf(PATIENT_ID).map { it.id })
    }

    @Test
    fun `deleteAllergy under the wrong patient throws NotFound and deletes nothing`() = runTest {
        val repository = InMemoryPatientRepository()

        val error = assertFailsWith<ApiException> { repository.deleteAllergy(OTHER_PATIENT_ID, PENICILINA_ID) }
        assertIs<ApiError.NotFound>(error.error)
        assertEquals(listOf(PENICILINA_ID), repository.allergiesOf(PATIENT_ID).map { it.id })
    }
}
