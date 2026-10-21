package com.cramsan.hirsh.network.dto

import com.cramsan.hirsh.model.AccessAction
import com.cramsan.hirsh.model.AccessLogEntry
import com.cramsan.hirsh.util.formatDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Schema `AccessLogEntry`. [action] stays a plain string on the wire and is mapped with a fallback
 * (see [AccessAction.OTHER]); the free-form `detail`/`userAgent`/`resource*` fields aren't shown
 * anywhere, so they're left to `ignoreUnknownKeys`.
 */
@Serializable
data class AccessLogEntryDto(
    val id: String,
    val userId: String? = null,
    val patientId: String? = null,
    val action: String,
    val sourceIp: String? = null,
    val occurredAt: String,
)

@OptIn(ExperimentalTime::class)
fun AccessLogEntryDto.toDomain(timeZone: TimeZone = TimeZone.currentSystemDefault()): AccessLogEntry = AccessLogEntry(
    id = id,
    userId = userId,
    action = AccessAction.entries.firstOrNull { it.name == action } ?: AccessAction.OTHER,
    occurredAt = formatDateTime(Instant.parse(occurredAt), timeZone),
    sourceIp = sourceIp,
)
