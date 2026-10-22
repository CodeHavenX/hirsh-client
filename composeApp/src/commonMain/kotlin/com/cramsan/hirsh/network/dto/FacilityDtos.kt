package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.BedRef
import com.cramsan.hirsh.model.ServiceRef
import kotlinx.serialization.Serializable

/** One entry of `GET /api/v1/clinical-services` -- schema `ServiceResponse`, trimmed to what the pickers use. */
@Serializable
data class ServiceResponse(
    val id: String,
    val name: String,
    val active: Boolean,
)

/** `GET /api/v1/clinical-services/{serviceId}/beds` -- schema `BedBoardResponse`; only its beds are read. */
@Serializable
data class BedBoardResponse(
    val beds: List<BedBoardBed> = emptyList(),
)

/** Schema `BedWithOccupant`. Its occupant isn't read: a picker only offers [BedStatusDto.AVAILABLE] beds. */
@Serializable
data class BedBoardBed(
    val id: String,
    val number: String,
    val status: BedStatusDto,
)

/** The backend's `BedStatus`. */
@Serializable
enum class BedStatusDto { AVAILABLE, OCCUPIED, MAINTENANCE, RESERVED }

fun ServiceResponse.toServiceRef(): ServiceRef = ServiceRef(id = id, name = name)

fun BedBoardBed.toBedRef(): BedRef = BedRef(id = id, number = number)
