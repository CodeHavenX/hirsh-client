package com.cramsan.hirsh.repository

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.ServiceRef
import com.cramsan.hirsh.model.UserRef
import com.cramsan.hirsh.network.getAllPages
import com.cramsan.hirsh.network.dto.BedBoardResponse
import com.cramsan.hirsh.network.dto.BedStatusDto
import com.cramsan.hirsh.network.dto.ServiceResponse
import com.cramsan.hirsh.network.dto.UserResponse
import com.cramsan.hirsh.network.dto.UserStatusDto
import com.cramsan.hirsh.network.dto.toBedRef
import com.cramsan.hirsh.network.dto.toServiceRef
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * The admission form's pickers (HISS-634). One-shot reads rather than flows, like
 * [Icd10Repository.search]: each is read once to fill a form, and nothing in the app mutates them.
 */
interface AdmissionCatalogRepository {
    /** Active clinical services of every type -- the backend admits into any of them. */
    suspend fun services(): List<ServiceRef>

    /** [serviceId]'s beds that can take a patient now; occupied, reserved and maintenance beds are left out. */
    suspend fun availableBeds(serviceId: String): List<BedRef>

    /** Active accounts holding the attending-physician role. */
    suspend fun attendingPhysicians(): List<UserRef>
}

/**
 * Real implementation over `GET /clinical-services`, its bed board and `GET /users`. No in-memory
 * counterpart: every caller is bound to the real backend, and tests and previews keep their own fakes.
 */
class KtorAdmissionCatalogRepository(
    private val httpClient: HttpClient,
) : AdmissionCatalogRepository {

    override suspend fun services(): List<ServiceRef> =
        httpClient.getAllPages<ServiceResponse>("/api/v1/clinical-services")
            .filter { it.active }
            .map { it.toServiceRef() }

    override suspend fun availableBeds(serviceId: String): List<BedRef> =
        httpClient.get("/api/v1/clinical-services/$serviceId/beds").body<BedBoardResponse>()
            .beds
            .filter { it.status == BedStatusDto.AVAILABLE }
            .map { it.toBedRef() }

    override suspend fun attendingPhysicians(): List<UserRef> =
        httpClient.getAllPages<UserResponse>("/api/v1/users") {
            parameter("roleCode", ATTENDING_PHYSICIAN_ROLE)
            parameter("status", UserStatusDto.ACTIVE)
        }.map { UserRef(id = it.id, fullName = it.fullName) }

    private companion object {
        const val ATTENDING_PHYSICIAN_ROLE = "ATTENDING_PHYSICIAN"
    }
}
