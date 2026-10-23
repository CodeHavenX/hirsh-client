package com.cramsan.hirsh.ui.components

import com.cramsan.hirsh.model.ActiveTreatment
import com.cramsan.hirsh.model.RouteOfAdministration
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.TreatmentRepository
import com.cramsan.hirsh.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val sertralina = ActiveTreatment(
    id = "t1",
    drugName = "Sertralina 50 mg · Tableta",
    dose = "50 mg",
    frequency = "cada 24 h",
    route = RouteOfAdministration.PO,
    episodeId = "h1",
    episodeStartDate = LocalDate(2026, 10, 20),
)

private data class Suspension(val treatmentId: String, val reason: String, val endDate: LocalDate)

private class FakeTreatmentRepository : TreatmentRepository {
    var failure: Throwable? = null
    var readFailure: Throwable? = null
    val suspensions = mutableListOf<Suspension>()

    override fun getActiveTreatments(patientId: String): Flow<List<ActiveTreatment>> = flow {
        readFailure?.let { throw it }
        emit(listOf(sertralina))
    }

    override suspend fun suspend(treatmentId: String, reason: String, endDate: LocalDate) {
        failure?.let { throw it }
        suspensions += Suspension(treatmentId, reason, endDate)
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private fun TestScope.newSuspender(repository: TreatmentRepository) =
    TreatmentSuspender(repository, FixedClock(Instant.parse("2026-10-23T15:00:00Z")), this)

class TreatmentSuspenderTest {

    @Test
    fun `the section starts loading and then shows the treatments`() = runTest {
        val sections = FakeTreatmentRepository().activeTreatmentsSection("p1").take(2).toList()

        assertEquals(listOf(ActiveTreatmentsSection.Loading, ActiveTreatmentsSection.Loaded(listOf(sertralina))), sections)
    }

    @Test
    fun `a failed read becomes the section's failed state`() = runTest {
        val repository = FakeTreatmentRepository().apply { readFailure = RuntimeException("Connection refused") }

        val sections = repository.activeTreatmentsSection("p1").toList()

        assertEquals(listOf(ActiveTreatmentsSection.Loading, ActiveTreatmentsSection.Failed), sections)
    }

    @Test
    fun `confirming suspends the treatment with the trimmed reason, dated today, and closes the dialog`() = runTest {
        val repository = FakeTreatmentRepository()
        val suspender = newSuspender(repository)

        suspender.open(sertralina)
        suspender.onReasonChange("  Sedación excesiva ")
        suspender.confirm()
        advanceUntilIdle()

        assertEquals(listOf(Suspension("t1", "Sedación excesiva", LocalDate(2026, 10, 23))), repository.suspensions)
        assertNull(suspender.state.value)
    }

    @Test
    fun `a blank reason is rejected before calling the backend`() = runTest {
        val repository = FakeTreatmentRepository()
        val suspender = newSuspender(repository)

        suspender.open(sertralina)
        suspender.onReasonChange("   ")
        suspender.confirm()
        advanceUntilIdle()

        assertEquals(emptyList(), repository.suspensions)
        assertEquals(TreatmentSuspension(sertralina, reason = "   ", error = SUSPENSION_REASON_REQUIRED_ERROR), suspender.state.value)
    }

    @Test
    fun `a treatment that is no longer active keeps the dialog open with a conflict message`() = runTest {
        val repository = FakeTreatmentRepository().apply { failure = ApiException(ApiError.Conflict("t1")) }
        val suspender = newSuspender(repository)

        suspender.open(sertralina)
        suspender.onReasonChange("Sedación excesiva")
        suspender.confirm()
        advanceUntilIdle()

        assertEquals(
            TreatmentSuspension(sertralina, reason = "Sedación excesiva", error = SUSPENSION_CONFLICT_ERROR),
            suspender.state.value,
        )
    }

    @Test
    fun `any other failure keeps the dialog open with a generic message`() = runTest {
        val repository = FakeTreatmentRepository().apply { failure = RuntimeException("Connection refused") }
        val suspender = newSuspender(repository)

        suspender.open(sertralina)
        suspender.onReasonChange("Sedación excesiva")
        suspender.confirm()
        advanceUntilIdle()

        assertEquals(SUSPENSION_ERROR, suspender.state.value?.error)
    }

    @Test
    fun `dismissing closes the dialog without suspending`() = runTest {
        val repository = FakeTreatmentRepository()
        val suspender = newSuspender(repository)

        suspender.open(sertralina)
        suspender.dismiss()

        assertNull(suspender.state.value)
        assertEquals(emptyList(), repository.suspensions)
    }
}
