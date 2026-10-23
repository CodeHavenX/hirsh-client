package com.cramsan.hirsh.util

/**
 * Hands a downloaded file to the user the way their platform expects (HCEM-14). Each platform
 * binds its own in `di/PlatformModule.<platform>.kt`.
 */
interface FileSaver {
    /** False where there's no save hook yet, which hides the download actions. */
    val isSupported: Boolean get() = true

    /** Saves [bytes] as [filename]; returns where it went, to tell the user. */
    suspend fun save(filename: String, bytes: ByteArray): String
}

/** Android and iOS: no save hook yet. */
class UnsupportedFileSaver : FileSaver {
    override val isSupported: Boolean get() = false

    override suspend fun save(filename: String, bytes: ByteArray): String =
        throw UnsupportedOperationException("Saving files isn't supported on this platform yet")
}
