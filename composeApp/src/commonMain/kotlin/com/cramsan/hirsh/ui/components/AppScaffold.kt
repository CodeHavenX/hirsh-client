package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk2

data class NavItem(
    val label: String,
    val destination: String,
    val icon: @Composable () -> Unit = {},
)

/** Ported 1:1 from renderSidebar()'s hardcoded "Fase 2" roadmap teaser -- not real destinations, so not passed in as [NavItem]s. */
private val FASE_2_STUBS = listOf("Agenda", "Reportes", "Interconsultas")

/**
 * Fixed sidebar + content shell, modeled on renderAppShell()/renderSidebar()
 * in prototype/shared/components.js. Works as-is on desktop/web/tablet; a
 * narrow-width (phone) variant that collapses the sidebar behind a menu
 * button is a follow-up once real screens exist to test it against.
 */
@Composable
fun AppScaffold(
    items: List<NavItem>,
    selectedDestination: String,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp)) {
                Text(
                    text = "HISS",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = item.icon,
                        selected = item.destination == selectedDestination,
                        onClick = { onNavigate(item.destination) },
                        colors = NavigationDrawerItemDefaults.colors(),
                        modifier = Modifier.testTag("nav_${item.destination}"),
                    )
                }
                NavPhaseDivider("Fase 2")
                FASE_2_STUBS.forEach { label -> DisabledNavItem(label) }
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

/** Mirrors `.nav-divider`: a mono/uppercase label with a trailing rule filling the remaining width. */
@Composable
private fun NavPhaseDivider(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Text(
            label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.9.sp,
            color = HissInk2,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = HissFaint)
    }
}

/** Mirrors `.nav-item.disabled`: same nav-item shape, dimmed and non-interactive -- no `onClick`. */
@Composable
private fun DisabledNavItem(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().alpha(0.4f).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.5.dp, HissInk2, RoundedCornerShape(5.dp)),
        )
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HissInk2)
    }
}
