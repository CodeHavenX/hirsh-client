package com.cramsan.hirsh.ui.components

import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.PdfExportFailedException
import com.cramsan.hirsh.repository.PdfExportRepository
import com.cramsan.hirsh.repository.PdfExportTarget
import com.cramsan.hirsh.repository.PdfExportTimeoutException
import com.cramsan.hirsh.repository.PdfFile
import com.cramsan.hirsh.util.FileSaver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val target = PdfExportTarget.FullRecord("h1")

private class FakePdfExportRepository : PdfExportRepository {
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0

    override suspend fun export(target: PdfExportTarget): PdfFile {
        calls++
        gate?.await()
        failure?.let { throw it }
        return PdfFile("registro-completo-HC-00142-20261023.pdf", byteArrayOf(1))
    }
}

private class RecordingFileSaver : FileSaver {
    val saved = mutableListOf<String>()

    override suspend fun save(filename: String, bytes: ByteArray): String {
        saved += filename
        return "/home/dr/Downloads/$filename"
    }
}

class PdfDownloaderTest {

    private fun TestScope.failingWith(failure: Exception): PdfDownload {
        val downloader = PdfDownloader(FakePdfExportRepository().apply { this.failure = failure }, RecordingFileSaver(), this)
        downloader.download(target)
        advanceUntilIdle()
        return downloader.state.value
    }

    @Test
    fun `generates, then saves the file under the backend's name`() = runTest {
        val repository = FakePdfExportRepository().apply { gate = CompletableDeferred() }
        val saver = RecordingFileSaver()
        val downloader = PdfDownloader(repository, saver, this)

        downloader.download(target)
        runCurrent()
        assertEquals(PdfDownload.Generating, downloader.state.value)
        downloader.download(target)
        runCurrent()
        assertEquals(1, repository.calls, "a second click while generating is ignored")

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("registro-completo-HC-00142-20261023.pdf"), saver.saved)
        assertEquals(PdfDownload.Saved("/home/dr/Downloads/registro-completo-HC-00142-20261023.pdf"), downloader.state.value)
    }

    @Test
    fun `each failure reads as what the user can do about it`() = runTest {
        assertEquals(
            PdfDownload.Failed("No se pudo generar el PDF: Sin documentos firmados"),
            failingWith(PdfExportFailedException("Sin documentos firmados")),
        )
        assertEquals(PdfDownload.Failed(PDF_TIMEOUT_ERROR), failingWith(PdfExportTimeoutException()))
        assertEquals(PdfDownload.Failed(PDF_FORBIDDEN_ERROR), failingWith(ApiException(ApiError.Forbidden)))
        assertEquals(PdfDownload.Failed(PDF_DRAFTS_ERROR), failingWith(ApiException(ApiError.Conflict("h1"))))
        assertEquals(PdfDownload.Failed(PDF_ERROR), failingWith(RuntimeException("Connection refused")))
    }
}
