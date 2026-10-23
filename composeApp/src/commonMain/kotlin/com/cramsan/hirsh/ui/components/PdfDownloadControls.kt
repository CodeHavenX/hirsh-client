package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import com.cramsan.hirsh.ui.theme.HissSuccess
import com.cramsan.hirsh.ui.theme.HissSuccessWash

/** "Descargar PDF" beside a screen's print action; reads "Generando PDF…" while the backend works. */
@Composable
fun PdfDownloadButton(state: PdfDownload, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val generating = state == PdfDownload.Generating
    OutlinedButton(
        onClick = onClick,
        enabled = !generating,
        shape = RoundedCornerShape(HissRadiusDefault),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier.testTag("pdf_download_button"),
    ) {
        Text(
            if (generating) "Generando PDF…" else "Descargar PDF",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Where the file went, or why it didn't; nothing while idle or generating. */
@Composable
fun PdfDownloadStatus(state: PdfDownload, modifier: Modifier = Modifier) {
    when (state) {
        is PdfDownload.Saved -> Text(
            "PDF guardado: ${state.location}",
            color = HissSuccess,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = modifier
                .background(HissSuccessWash, RoundedCornerShape(HissRadiusDefault))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("pdf_download_saved"),
        )
        is PdfDownload.Failed -> Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.testTag("pdf_download_error"),
        )
        PdfDownload.Idle, PdfDownload.Generating -> Unit
    }
}
