package com.cramsan.hirsh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.ui.theme.HissFaint
import com.cramsan.hirsh.ui.theme.HissInk
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusNavIcon

/** Width of the fixed sidebar rail, and of the slide-over panel when narrow-width collapses it. */
private val SidebarWidth = 240.dp

/**
 * Below this width the sidebar collapses behind a hamburger toggle and slides over the content
 * instead of sitting beside it -- mirrors `prototype/shared/styles.css`'s
 * `@media (max-width: 900px)` off-canvas nav breakpoint.
 */
private val NarrowNavBreakpoint = 900.dp

data class NavItem(
    val label: String,
    val destination: String,
    val icon: @Composable () -> Unit = {},
)

/** Ported 1:1 from renderSidebar()'s hardcoded "Fase 2" roadmap teaser -- not real destinations, so not passed in as [NavItem]s. */
private val FASE_2_STUBS = listOf("Agenda", "Reportes", "Interconsultas")

/**
 * Sidebar + content shell, modeled on renderAppShell()/renderSidebar() in
 * prototype/shared/components.js.
 *
 * At/above [NarrowNavBreakpoint] the sidebar sits fixed beside the content (unchanged from
 * pre-HISS-503 behavior). Below it, the sidebar collapses behind a hamburger toggle and slides
 * over the content as an overlay with a dismiss scrim, mirroring the prototype's
 * `toggleNav()`/`.nav-open`/`.sidebar-scrim` behavior. This is entirely self-contained here --
 * none of the 9 screens that call [AppScaffold] need any change, and the toggle state is local
 * `remember` state that naturally resets closed on navigation, since [AppNavHost] mounts a fresh
 * [AppScaffold] instance per route.
 */
@Composable
fun AppScaffold(
    items: List<NavItem>,
    selectedDestination: String,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= NarrowNavBreakpoint) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    items = items,
                    selectedDestination = selectedDestination,
                    onNavigate = onNavigate,
                    modifier = Modifier.width(SidebarWidth).fillMaxHeight(),
                )
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    content()
                }
            }
        } else {
            var navOpen by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxSize()) {
                NavToggleBar(onToggle = { navOpen = !navOpen })
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    content()
                }
            }
            AnimatedVisibility(visible = navOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HissInk.copy(alpha = 0.3f))
                        .clickable(onClick = { navOpen = false })
                        .testTag("nav_scrim"),
                )
            }
            AnimatedVisibility(
                visible = navOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
            ) {
                Sidebar(
                    items = items,
                    selectedDestination = selectedDestination,
                    onNavigate = { destination ->
                        onNavigate(destination)
                        navOpen = false
                    },
                    modifier = Modifier
                        .width(SidebarWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}

/** Mirrors `.nav-toggle`: a bordered hamburger button, shown only below [NarrowNavBreakpoint]. */
@Composable
private fun NavToggleBar(onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(1.5.dp, HissFaint, RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .testTag("nav_toggle_button"),
            contentAlignment = Alignment.Center,
        ) {
            Text("☰", color = HissInk2, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Sidebar(
    items: List<NavItem>,
    selectedDestination: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
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
                .border(1.5.dp, HissInk2, RoundedCornerShape(HissRadiusNavIcon)),
        )
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HissInk2)
    }
}
