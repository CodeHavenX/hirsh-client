package com.cramsan.hirsh.repository

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
    fun `updatePatient with no actual changes does not bump jpaVersion`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }

        repository.updatePatient(id = PATIENT_ID, newValues = current.copy())

        assertEquals(current.jpaVersion, repository.patients.value.first { it.id == PATIENT_ID }.jpaVersion)
    }

    @Test
    fun `updatePatient bumps jpaVersion on a successful save`() = runTest {
        val repository = InMemoryPatientRepository()
        val current = repository.patients.value.first { it.id == PATIENT_ID }
        assertEquals(0L, current.jpaVersion)

        repository.updatePatient(
            id = PATIENT_ID,
            newValues = current.copy(phone = "999-999-999"),
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
