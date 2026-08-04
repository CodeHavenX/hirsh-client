package com.cramsan.hirsh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.cramsan.hirsh.ui.components.AppScaffold
import com.cramsan.hirsh.ui.components.NavItem
import com.cramsan.hirsh.ui.screens.login.LoginScreen
import com.cramsan.hirsh.ui.screens.patientlist.PatientListScreen
import com.cramsan.hirsh.ui.screens.patientrecord.PatientRecordScreen
import com.cramsan.hirsh.ui.screens.profile.ProfileScreen

private val sidebarItems = listOf(
    NavItem(label = "Pacientes", destination = Routes.PATIENTS),
    NavItem(label = "Perfil", destination = Routes.PROFILE),
)

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.PATIENTS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.PATIENTS) {
            AppScaffold(
                items = sidebarItems,
                selectedDestination = Routes.PATIENTS,
                onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
            ) {
                PatientListScreen(onPatientSelected = { id -> navController.navigate(Routes.patientRecord(id)) })
            }
        }
        composable(
            route = Routes.PATIENT_RECORD,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.read { getString("patientId") }.orEmpty()
            AppScaffold(
                items = sidebarItems,
                selectedDestination = Routes.PATIENTS,
                onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
            ) {
                PatientRecordScreen(patientId = patientId)
            }
        }
        composable(Routes.PROFILE) {
            AppScaffold(
                items = sidebarItems,
                selectedDestination = Routes.PROFILE,
                onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
            ) {
                ProfileScreen(onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0)
                    }
                })
            }
        }
    }
}

private fun navigateToSidebarItem(navController: NavHostController, destination: String) {
    navController.navigate(destination) {
        launchSingleTop = true
        popUpTo(Routes.PATIENTS) { inclusive = false }
    }
}
