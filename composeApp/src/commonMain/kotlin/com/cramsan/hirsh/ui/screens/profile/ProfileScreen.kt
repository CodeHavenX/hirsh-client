package com.cramsan.hirsh.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall)
        Button(
            onClick = {
                viewModel.signOut()
                onSignedOut()
            },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Cerrar sesion")
        }
    }
}
