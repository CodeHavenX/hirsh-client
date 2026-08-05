package com.cramsan.hirsh.ui.screens.patientlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cramsan.hirsh.model.Patient
import com.cramsan.hirsh.model.calculateAge
import com.cramsan.hirsh.ui.components.DataTable
import com.cramsan.hirsh.ui.components.DataTableColumn
import com.cramsan.hirsh.ui.theme.HissInk2
import com.cramsan.hirsh.ui.theme.HissRadiusDefault
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.compose.viewmodel.koinViewModel

private val CellFontSize = 13.sp

@OptIn(ExperimentalTime::class)
@Composable
fun PatientListScreen(
    onPatientSelected: (patientId: String) -> Unit,
    onRegisterPatient: () -> Unit,
    viewModel: PatientListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "Pacientes",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    "${uiState.patients.size} activos",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = HissInk2,
                )
            }
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        "Buscar paciente por nombre o DNI...",
                        fontSize = CellFontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Text("⌕", color = HissInk2) },
                shape = RoundedCornerShape(HissRadiusDefault),
                singleLine = true,
                modifier = Modifier.width(320.dp).padding(start = 24.dp).testTag("patient_search_field"),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onRegisterPatient,
                    shape = RoundedCornerShape(HissRadiusDefault),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("patient_register_button"),
                ) {
                    Text("+ Registrar paciente", fontSize = CellFontSize, fontWeight = FontWeight.Medium)
                }
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp)) {
            DataTable(
                columns = patientColumns(today = today),
                rows = uiState.patients,
                onRowClick = { patient -> onPatientSelected(patient.id) },
            )
        }
    }
}

private fun patientColumns(today: LocalDate): List<DataTableColumn<Patient>> = listOf(
    DataTableColumn(label = "HCL", weight = 0.7f) { patient -> Text(patient.id, fontSize = CellFontSize) },
    DataTableColumn(label = "Nombre", weight = 2f) { patient ->
        Text(patient.name, fontSize = CellFontSize, fontWeight = FontWeight.SemiBold)
    },
    DataTableColumn(label = "F. Nacimiento", weight = 1f) { patient -> Text(patient.dateOfBirth, fontSize = CellFontSize) },
    DataTableColumn(label = "Edad", weight = 0.5f) { patient ->
        Text(calculateAge(patient.dateOfBirth, today).toString(), fontSize = CellFontSize)
    },
    DataTableColumn(label = "Telefono", weight = 1f) { patient -> Text(patient.phone, fontSize = CellFontSize) },
    DataTableColumn(label = "Medico", weight = 0.9f) { patient -> Text(patient.assignedDoctor, fontSize = CellFontSize) },
    DataTableColumn(label = "Ult. ingreso", weight = 1f) { patient -> Text(patient.lastVisit, fontSize = CellFontSize) },
    DataTableColumn(label = "", weight = 0.2f) { Text("›", fontSize = CellFontSize, color = HissInk2) },
)
