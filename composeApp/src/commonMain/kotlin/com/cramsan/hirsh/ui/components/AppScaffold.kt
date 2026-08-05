package com.cramsan.hirsh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class NavItem(
    val label: String,
    val destination: String,
    val icon: @Composable () -> Unit = {},
)

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
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}
