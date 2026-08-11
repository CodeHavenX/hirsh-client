package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.theme.HissAccent
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissPrintBackdrop
import com.cramsan.hirsh.ui.theme.HissPrintToolbarText

/**
 * One `print-sec` block -- [label] renders as the mock's mono/uppercase/accent
 * heading, [content] is a free composable slot rather than a plain string so
 * callers can render a KV list, a paragraph, or a bordered prescription box,
 * matching how different sections in print.html format their own body.
 */
data class PrintableSection(
    val label: String,
    val content: @Composable () -> Unit,
)

/**
 * Shared "printable summary" view -- mirrors print.html's `.print-frame` /
 * `.print-toolbar` / `.print-sheet` / `renderPrintSection` layout, reused by
 * both the Historia Clinica and evolucion screens (HISS-501) so there's a
 * real, shareable read view even before OS-level print/share wiring lands on
 * every platform (see that ticket's Plane comment for the deferred-per-platform
 * decision). [onBack] is this view's only action -- no in-preview "Imprimir"
 * trigger yet, since there's nothing for one to call until that follow-up work
 * lands.
 */
@Composable
fun PrintableSummary(
    title: String,
    onBack: () -> Unit,
    subtitle: String,
    metaItems: List<Pair<String, String>>,
    sections: List<PrintableSection>,
    signatureName: String,
) {
    Column(modifier = Modifier.fillMaxSize().background(HissPrintBackdrop)) {
        PrintableToolbar(title = title, onBack = onBack)
        Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            PrintableSheet(subtitle = subtitle, metaItems = metaItems, sections = sections, signatureName = signatureName)
        }
    }
}

@Composable
private fun PrintableToolbar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).background(HissInk).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "‹ Volver",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = HissPrintToolbarText,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = HissPrintToolbarText,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun PrintableSheet(
    subtitle: String,
    metaItems: List<Pair<String, String>>,
    sections: List<PrintableSection>,
    signatureName: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(600.dp).padding(vertical = 24.dp),
    ) {
        Column(modifier = Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PrintableClinicHeader(subtitle)
            HorizontalDivider(thickness = 2.dp, color = HissInk)
            PrintableMetaGrid(metaItems)
            HorizontalDivider(thickness = 1.dp, color = HissFaint)
            sections.forEach { section -> PrintableSectionBlock(section) }
            PrintableFooter(signatureName)
        }
    }
}

@Composable
private fun PrintableClinicHeader(subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hospital Maria Auxiliadora", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HissInk)
        Text(
            subtitle.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.08.sp,
            color = HissInk2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PrintableMetaGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) -> PrintableMetaCell(label, value, Modifier.weight(1f)) }
                if (row.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PrintableMetaCell(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.54.sp, color = HissInk2)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HissInk)
    }
}

@Composable
private fun PrintableSectionBlock(section: PrintableSection) {
    Column {
        Text(
            section.label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = HissAccent,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        section.content()
    }
}

/** Default section body for a plain paragraph or `<br>`-joined line list. */
@Composable
fun PrintableSectionText(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 20.8.sp, color = HissInk)
}

/** Default section body for a `formatSectionFields`-style KV line list, one "Label: value" per line. */
@Composable
fun PrintableKvLines(lines: List<Pair<String, String>>) {
    Column {
        lines.forEach { (label, value) ->
            Text(
                "$label: ${value.ifBlank { "—" }}",
                fontSize = 13.sp,
                lineHeight = 20.8.sp,
                color = HissInk,
            )
        }
    }
}

@Composable
private fun PrintableFooter(signatureName: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "Documento generado por HISS\nHospital Maria Auxiliadora\nLima, Peru",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 13.5.sp,
            color = HissInk2,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Column(modifier = Modifier.width(180.dp)) {
            HorizontalDivider(color = HissInk)
            Text(
                signatureName,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = HissInk2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}
