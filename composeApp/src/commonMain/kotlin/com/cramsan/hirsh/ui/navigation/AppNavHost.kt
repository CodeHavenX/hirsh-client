package com.cramsan.hirsh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.AppScaffold
import com.cramsan.hirsh.ui.components.NavItem
import com.cramsan.hirsh.ui.screens.login.LoginScreen
import com.cramsan.hirsh.ui.screens.patientlist.PatientListScreen
import com.cramsan.hirsh.ui.screens.patientrecord.PatientRecordScreen
import com.cramsan.hirsh.ui.screens.profile.ProfileScreen
import org.koin.compose.koinInject

private fun sidebarItems(session: Session?): List<NavItem> = listOf(
    NavItem(label = "Pacientes", destination = Routes.PATIENTS),
    NavItem(label = "Perfil", destination = Routes.PROFILE),
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    sessionRepository: SessionRepository = koinInject(),
) {
    val session by sessionRepository.session.collectAsState()
    val items = sidebarItems(session)

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.PATIENTS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.PATIENTS) {
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
                    selectedDestination = Routes.PATIENTS,
                    onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                ) {
                    PatientListScreen(onPatientSelected = { id -> navController.navigate(Routes.patientRecord(id)) })
                }
            }
        }
        composable(
            route = Routes.PATIENT_RECORD,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.read { getString("patientId") }.orEmpty()
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
                    selectedDestination = Routes.PATIENTS,
                    onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                ) {
                    PatientRecordScreen(patientId = patientId)
                }
            }
        }
        composable(Routes.PROFILE) {
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
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
}

/**
 * Route guard shared by every non-login destination: redirects to login when
 * there's no session rather than rendering content for it. Matters most for
 * a route becoming directly addressable (a bookmark, a back-stack quirk, a
 * direct URL on the wasmJs target) rather than reached through normal
 * in-app navigation. HISS-106 generalizes this to the routes it adds rather
 * than re-deriving the mechanism.
 */
@Composable
private fun RequireSession(
    session: Session?,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    if (session == null) {
        LaunchedEffect(Unit) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0)
            }
        }
    } else {
        content()
    }
}

private fun navigateToSidebarItem(navController: NavHostController, destination: String) {
    navController.navigate(destination) {
        launchSingleTop = true
        popUpTo(Routes.PATIENTS) { inclusive = false }
    }
}
