package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.Icd10Code
import kotlinx.serialization.Serializable

/** One entry of `GET /api/v1/icd10-codes` -- schema `Icd10CodeResponse`. Only [code]/[description] are mapped. */
@Serializable
data class Icd10CodeResponse(
    val code: String,
    val description: String,
)

fun Icd10CodeResponse.toDomain(): Icd10Code = Icd10Code(code = code, description = description)
