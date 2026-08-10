package com.cramsan.hirsh.ui.navigation

import androidx.compose.material3.Text
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
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.model.Session
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.components.AppScaffold
import com.cramsan.hirsh.ui.components.NavItem
import com.cramsan.hirsh.ui.screens.admision.AdmisionScreen
import com.cramsan.hirsh.ui.screens.hospitalization.HospitalizationScreen
import com.cramsan.hirsh.ui.screens.login.LoginScreen
import com.cramsan.hirsh.ui.screens.patientedit.EditPatientScreen
import com.cramsan.hirsh.ui.screens.patienthistory.PatientHistoryScreen
import com.cramsan.hirsh.ui.screens.patientlist.PatientListScreen
import com.cramsan.hirsh.ui.screens.patientrecord.PatientRecordScreen
import com.cramsan.hirsh.ui.screens.patientregister.RegisterPatientScreen
import com.cramsan.hirsh.ui.screens.profile.ProfileScreen
import org.koin.compose.koinInject

private fun sidebarItems(session: Session?): List<NavItem> = buildList {
    add(NavItem(label = "Pacientes", destination = Routes.PATIENTS))
    add(NavItem(label = "Perfil", destination = Routes.PROFILE))
    if (session?.role == Role.ADMIN) {
        add(NavItem(label = "Cuentas", destination = Routes.ACCOUNTS))
    }
}

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
                    PatientListScreen(
                        onPatientSelected = { id -> navController.navigate(Routes.patientRecord(id)) },
                        onRegisterPatient = { navController.navigate(Routes.PATIENT_REGISTER) },
                    )
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
                    PatientRecordScreen(
                        patientId = patientId,
                        onEditProfile = { navController.navigate(Routes.patientEdit(patientId)) },
                        onNewHospitalization = { navController.navigate(Routes.admision(patientId)) },
                        onHospitalizationSelected = { hospId ->
                            navController.navigate(Routes.hospitalization(patientId, hospId))
                        },
                        onViewHistory = { navController.navigate(Routes.patientHistory(patientId)) },
                    )
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
        composable(Routes.PATIENT_REGISTER) {
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
                    selectedDestination = Routes.PATIENTS,
                    onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                ) {
                    RegisterPatientScreen(
                        onRegistered = { id ->
                            navController.navigate(Routes.patientRecord(id)) {
                                popUpTo(Routes.PATIENT_REGISTER) { inclusive = true }
                            }
                        },
                        onCancel = { navController.popBackStack() },
                        onViewExistingPatient = { id ->
                            navController.navigate(Routes.patientRecord(id)) {
                                popUpTo(Routes.PATIENT_REGISTER) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
        composable(
            route = Routes.PATIENT_EDIT,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.read { getString("patientId") }.orEmpty()
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
                    selectedDestination = Routes.PATIENTS,
                    onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                ) {
                    EditPatientScreen(
                        patientId = patientId,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(
            route = Routes.PATIENT_HISTORY,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.read { getString("patientId") }.orEmpty()
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
                    selectedDestination = Routes.PATIENTS,
                    onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                ) {
                    PatientHistoryScreen(
                        patientId = patientId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(
            route = Routes.ADMISION,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.read { getString("patientId") }.orEmpty()
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
                    selectedDestination = Routes.PATIENTS,
                    onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                ) {
                    AdmisionScreen(
                        patientId = patientId,
                        onAdmitted = { hospId ->
                            navController.navigate(Routes.hospitalization(patientId, hospId)) {
                                popUpTo(Routes.ADMISION) { inclusive = true }
                            }
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(
            route = Routes.HOSPITALIZATION,
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("hospId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.read { getString("patientId") }.orEmpty()
            val hospId = backStackEntry.arguments?.read { getString("hospId") }.orEmpty()
            RequireSession(session, navController) {
                AppScaffold(
                    items = items,
                    selectedDestination = Routes.PATIENTS,
                    onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                ) {
                    HospitalizationScreen(
                        patientId = patientId,
                        hospId = hospId,
                        onNewEvolucion = { navController.navigate(Routes.evolucionNew(patientId, hospId)) },
                        onOpenHistoriaClinica = { navController.navigate(Routes.historiaClinica(patientId, hospId)) },
                        onEvolucionSelected = { evoId ->
                            navController.navigate(Routes.evolucionView(patientId, hospId, evoId))
                        },
                    )
                }
            }
        }
        composable(
            route = Routes.HISTORIA_CLINICA,
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("hospId") { type = NavType.StringType },
            ),
        ) {
            RequireSession(session, navController) {
                RoutePlaceholder(Routes.HISTORIA_CLINICA)
            }
        }
        composable(
            route = Routes.EVOLUCION_NEW,
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("hospId") { type = NavType.StringType },
            ),
        ) {
            RequireSession(session, navController) {
                RoutePlaceholder(Routes.EVOLUCION_NEW)
            }
        }
        composable(
            route = Routes.EVOLUCION_VIEW,
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("hospId") { type = NavType.StringType },
                navArgument("evoId") { type = NavType.StringType },
            ),
        ) {
            RequireSession(session, navController) {
                RoutePlaceholder(Routes.EVOLUCION_VIEW)
            }
        }
        composable(Routes.ACCOUNTS) {
            RequireSession(session, navController) {
                if (session?.role != Role.ADMIN) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Routes.PATIENTS) {
                            popUpTo(0)
                        }
                    }
                } else {
                    AppScaffold(
                        items = items,
                        selectedDestination = Routes.ACCOUNTS,
                        onNavigate = { destination -> navigateToSidebarItem(navController, destination) },
                    ) {
                        RoutePlaceholder(Routes.ACCOUNTS)
                    }
                }
            }
        }
    }
}

/**
 * Stand-in for a destination whose real Screen/ViewModel/Previews trio hasn't
 * been built yet -- deliberately not a `*Screen.kt` file so it never triggers
 * the ScreenMissingViewModel/ScreenMissingPreviews detekt rules for code every
 * owning ticket (HISS-201..401) throws away when it lands. Each such ticket
 * replaces its one call site above rather than editing this composable.
 */
@Composable
private fun RoutePlaceholder(route: String) {
    Text("TODO: $route")
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
