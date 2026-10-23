package com.cramsan.hirsh.ui.components

import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.PdfExportFailedException
import com.cramsan.hirsh.repository.PdfExportRepository
import com.cramsan.hirsh.repository.PdfExportTarget
import com.cramsan.hirsh.repository.PdfExportTimeoutException
import com.cramsan.hirsh.util.FileSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a "Descargar PDF" request is (HCEM-14). */
sealed interface PdfDownload {
    data object Idle : PdfDownload

    data object Generating : PdfDownload

    /** [location] is where the platform put the file. */
    data class Saved(val location: String) : PdfDownload

    data class Failed(val message: String) : PdfDownload
}

internal const val PDF_FORBIDDEN_ERROR = "Tu cuenta no tiene permiso para exportar PDF."
internal const val PDF_DRAFTS_ERROR = "Hay documentos sin firmar: solo se exporta contenido firmado."
internal const val PDF_TIMEOUT_ERROR = "El PDF esta tardando demasiado. Intenta de nuevo en unos minutos."
internal const val PDF_ERROR = "No se pudo descargar el PDF."

/**
 * Downloads an export on behalf of a screen's view model, in its scope; the evolucion, the
 * Historia Clinica and the stay share it.
 */
class PdfDownloader(
    private val pdfExportRepository: PdfExportRepository,
    private val fileSaver: FileSaver,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PdfDownload>(PdfDownload.Idle)
    val state: StateFlow<PdfDownload> = _state.asStateFlow()

    /** Platforms without a way to save a file don't offer the download. */
    val isAvailable: Boolean get() = fileSaver.isSupported

    fun download(target: PdfExportTarget) {
        if (_state.value == PdfDownload.Generating) return
        _state.value = PdfDownload.Generating
        scope.launch {
            _state.value = try {
                val file = pdfExportRepository.export(target)
                PdfDownload.Saved(fileSaver.save(file.filename, file.bytes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PdfDownload.Failed(e.toPdfMessage())
            }
        }
    }

    private fun Exception.toPdfMessage(): String = when (this) {
        is PdfExportFailedException -> reason?.let { "No se pudo generar el PDF: $it" } ?: PDF_ERROR
        is PdfExportTimeoutException -> PDF_TIMEOUT_ERROR
        is ApiException -> when (error) {
            ApiError.Forbidden -> PDF_FORBIDDEN_ERROR
            is ApiError.Conflict -> PDF_DRAFTS_ERROR
            else -> PDF_ERROR
        }
        else -> PDF_ERROR
    }
}
