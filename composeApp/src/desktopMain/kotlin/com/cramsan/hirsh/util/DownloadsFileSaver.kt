package com.cramsan.hirsh.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves into the user's Downloads folder, like a browser does, numbering the name when a file of
 * that name is already there. The `hirsh.downloads.dir` system property overrides the folder.
 */
class DownloadsFileSaver(
    private val directory: File = System.getProperty("hirsh.downloads.dir")?.let(::File)
        ?: File(System.getProperty("user.home"), "Downloads"),
) : FileSaver {

    override suspend fun save(filename: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = freeName(File(filename).name)
        target.writeBytes(bytes)
        target.absolutePath
    }

    private fun freeName(name: String): File {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").let { if (it.isEmpty()) "" else ".$it" }
        return generateSequence(1) { it + 1 }
            .map { n -> File(directory, if (n == 1) name else "$base ($n)$extension") }
            .first { !it.exists() }
    }
}
