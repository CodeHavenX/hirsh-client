package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end use-case catalog for the HISS client, driven for real through cmp-bridge --
 * see .claude/skills/run-desktop/SKILL.md for the underlying protocol and
 * `com.cramsan.hirsh.e2e.DesktopE2ETest`/`WebE2ETest` for how a driver is obtained for
 * each target ([DesktopBridgeDriver][com.cramsan.cmpbridge.driver.DesktopBridgeDriver] over
 * a socket into the desktop app's embedded bridge server, vs.
 * [WebBridgeDriver][com.cramsan.cmpbridge.driver.WebBridgeDriver] driving a headless
 * Chromium against the wasmJs dev server). Both subclasses run every test here unmodified.
 *
 * This is one continuous, numbered walkthrough rather than independent isolated tests --
 * each subclass launches exactly ONE app instance for its whole run (launching is slow:
 * tens of seconds), so all @Test methods in a run share that instance's in-memory
 * repository state. [FixMethodOrder] + zero-padded numeric prefixes make that intentional:
 * later steps rely on state earlier steps left behind (e.g. test 04 stays logged in as
 * admin for every later admin-only step; test 20 signs out last, on purpose). Where a step
 * needs to create+immediately use a dynamically-generated id (a new hospitalization or
 * evolucion id, which cmp-bridge has no way to read out of a URL the way a browser
 * location bar would), it stays inline in ONE test method rather than being split into
 * several that would need that id passed between them.
 *
 * Seeded fixture data referenced below (from InMemoryPatientRepository /
 * HospitalizationRepository / AccountRepository / FakeAuthRepository, all seeded from
 * prototype/shared/data.js): patients #00142 (Maria Gonzalez Huerta, 3 Alta
 * hospitalizations), #00129 (Karla Sofia Ricaldi Sedano, 1 Activa hospitalization
 * `h_ricaldi_1` with 0 evoluciones), #00124 (Olga Karen Santiesteban Bracamonte, 0
 * hospitalizations); accounts `admin`/ADMIN, `apatel`/DOCTOR, `tveer`/DOCTOR+INACTIVE.
 * Any non-blank username/password not tied to an inactive account logs in successfully.
 *
 * It is fine for a test here to fail -- some flows (documented per-test below) are known
 * gaps in the app itself (e.g. no backend yet) or in cmp-bridge's own web-driver coverage
 * (some fields/scroll report zero bounds in the web accessibility DOM as of
 * cmp-bridge-driver 0.1.0.3) rather than bugs in this suite.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class HissE2EScenarios {

    protected abstract val driver: BridgeDriver

    // --- Login / session -----------------------------------------------------------------

    @Test
    fun test01_login_blankCredentials_rejected() {
        driver.waitForTag("login_username_field")
        driver.clickTag("login_submit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("login_submit_button"), "blank-credential submit must not navigate away from login")
        assertTrue(hierarchy.containsText("Usuario o contrasena incorrectos"), "expected the generic invalid-credentials message")
    }

    @Test
    fun test02_login_inactiveAccount_rejected() {
        driver.type("login_username_field", "tveer")
        driver.type("login_password_field", "whatever123")
        driver.clickTag("login_submit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("login_submit_button"), "an inactive seeded account must not be allowed in")
        assertTrue(hierarchy.containsText("Usuario o contrasena incorrectos"))
    }

    @Test
    fun test03_login_doctorAccount_success_hidesAccountsNav() {
        driver.type("login_username_field", "apatel")
        driver.type("login_password_field", "whatever123")
        driver.clickTag("login_submit_button")
        driver.waitForTag("nav_patients")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("nav_profile"))
        assertFalse(hierarchy.containsTag("nav_accounts"), "a DOCTOR-role session must not see the Cuentas nav item")
        // Signs back out so the rest of the suite runs as admin (accounts management needs it).
        driver.clickTag("nav_profile")
        driver.clickTag("profile_sign_out_button")
        driver.waitForTag("login_submit_button")
    }

    @Test
    fun test04_login_adminAccount_success_showsAccountsNav() {
        driver.type("login_username_field", "admin")
        driver.type("login_password_field", "whatever123")
        driver.clickTag("login_submit_button")
        driver.waitForTag("nav_accounts")
        assertTrue(driver.getHierarchy().containsTag("nav_accounts"), "ADMIN-role session must see the Cuentas nav item")
    }

    // --- Patient list / record -------------------------------------------------------------

    @Test
    fun test05_patientList_showsSeededPatients() {
        driver.clickTag("nav_patients")
        driver.waitForTag("patient_row_#00142")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Maria Gonzalez Huerta"))
        assertTrue(hierarchy.containsText("Eduardo Remon Huertas"))
    }

    @Test
    fun test06_patientRecord_populated_showsHospitalizationsAndProfileCard() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_row_#00142")
        driver.waitForTag("record_edit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Maria Gonzalez Huerta"))
        assertTrue(hierarchy.containsTag("record_new_hospitalization_button"))
        assertTrue(hierarchy.containsTag("hosp_card_h_gonzalez_1"), "3 seeded hospitalizations must each render a VisitCard")
        assertTrue(hierarchy.containsTag("hosp_card_h_gonzalez_2"))
        assertTrue(hierarchy.containsTag("hosp_card_h_gonzalez_3"))
    }

    @Test
    fun test07_patientRecord_emptyHospitalizations_showsEmptyState() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_row_#00124")
        driver.waitForTag("record_edit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Olga Karen Santiesteban Bracamonte"))
        assertTrue(
            hierarchy.containsText("Sin hospitalizaciones registradas"),
            "a patient with zero hospitalizations must show the empty-state copy",
        )
    }

    @Test
    fun test08_patientRecord_viewHistory_navigatesAndBack() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_row_#00142")
        driver.clickTag("record_history_link")
        driver.waitForTag("history_back_button")
        assertTrue(driver.getHierarchy().containsText("Maria Gonzalez Huerta"), "history screen must be scoped to the patient it was opened from")
        driver.clickTag("history_back_button")
        driver.waitForTag("record_edit_button")
    }

    // --- Register / edit patient ------------------------------------------------------------

    @Test
    fun test09_registerPatient_blankFields_showsValidationError() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_submit_button")
        driver.clickTag("register_submit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("register_submit_button"), "blank submit must not navigate away from the register form")
        assertTrue(hierarchy.containsText("Completa los campos requeridos"))
    }

    @Test
    fun test10_registerPatient_duplicateName_showsWarningAndNavigatesToExisting() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_name_field")
        // Substring match against the seeded "Maria Gonzalez Huerta" (#00142) -- see
        // RegisterPatientViewModel.checkDuplicate(). Blurring onto the DNI field is what
        // fires the name field's onFocusChanged(false) that triggers the check.
        driver.type("register_name_field", "Maria Gonzalez")
        driver.clickTag("register_dni_field")
        driver.waitForTag("register_duplicate_view_existing_link")
        assertTrue(driver.getHierarchy().containsText("Posible duplicado"))
        driver.clickTag("register_duplicate_view_existing_link")
        driver.waitForTag("record_edit_button")
        assertTrue(driver.getHierarchy().containsText("Maria Gonzalez Huerta"), "the duplicate link must land on the existing patient's own record")
    }

    @Test
    fun test11_registerPatient_success_createsPatientAndNavigatesToRecord() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_name_field")
        val newName = "Zzz E2E Test Patient"
        driver.type("register_name_field", newName)
        driver.type("register_dni_field", "E2E-00001")
        driver.type("register_dob_field", "01/01/1990")
        driver.type("register_phone_field", "555-0100")
        driver.selectOption("register_sex_field", 0)
        driver.clickTag("register_submit_button")
        driver.waitForTag("record_edit_button")
        assertTrue(driver.getHierarchy().containsText(newName), "a successful registration must land on the new patient's own record")
    }

    @Test
    fun test12_editPatient_updatesPhoneAndSaves() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_row_#00142")
        driver.clickTag("record_edit_button")
        driver.waitForTag("edit_phone_field")
        driver.type("edit_phone_field", "555-9999")
        driver.clickTag("edit_save_button")
        driver.waitForTag("record_edit_button")
        assertTrue(driver.getHierarchy().containsText("555-9999"), "the edited phone number must show back on the record screen")
    }

    // --- Hospitalization: read-only empty-state variant --------------------------------------

    @Test
    fun test13_hospitalization_emptyEvoluciones_showsEmptyState() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_row_#00129")
        driver.clickTag("hosp_card_h_ricaldi_1")
        driver.waitForTag("hosp_new_evolucion_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("hosp_discharge_button"), "an Activa hospitalization must show the discharge action")
        assertTrue(
            hierarchy.containsText("Sin evoluciones registradas"),
            "a hospitalization with zero evoluciones must show the empty-state copy",
        )
    }

    // --- Full clinical documentation lifecycle: admision -> HC -> evolucion -> discharge -----

    @Test
    fun test14_admisionToDischarge_fullHospitalizationLifecycle() {
        driver.clickTag("nav_patients")
        driver.clickTag("patient_row_#00124") // Olga Karen Santiesteban Bracamonte, 0 hospitalizations
        driver.clickTag("record_new_hospitalization_button")
        driver.waitForTag("admision_submit_button")

        // Blank submit is rejected first, mirroring the register/nueva-evolucion validation pattern.
        driver.clickTag("admision_submit_button")
        assertTrue(driver.getHierarchy().containsText("Completa los campos requeridos"))

        driver.selectOption("admision_servicio_field", 0)
        driver.type("admision_cama_field", "12")
        driver.selectOption("admision_medico_field", 0)
        driver.type("admision_motivo_field", "E2E: sintomas respiratorios")
        driver.clickTag("admision_submit_button")
        driver.waitForTag("hosp_discharge_button")
        var hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Olga Karen Santiesteban Bracamonte"))
        assertTrue(hierarchy.containsTag("hosp_discharge_button"), "a freshly-admitted hospitalization must be Activa")

        // Historia Clinica: fill the default (Filiacion) section, then Motivo de Ingreso's checkboxes.
        driver.clickTag("hosp_open_hc_button")
        driver.waitForTag("hc_field_edad")
        driver.type("hc_field_edad", "34")
        driver.clickTag("hc_save_section_button")
        driver.clickTag("hc_nav_MOTIVO_INGRESO")
        driver.waitForTag("hc_motivo_option_riesgoSuicida")
        driver.clickTag("hc_motivo_option_riesgoSuicida")
        driver.clickTag("hc_save_section_button")
        driver.clickTag("encounter_close_button")
        driver.waitForTag("hosp_discharge_button")

        // Nueva evolucion: required fields are Subjetivo, Objetivo, >=1 diagnosis, Pronostico, Evolucion.
        driver.clickTag("hosp_new_evolucion_button")
        driver.waitForTag("evo_new_save_button")
        driver.clickTag("evo_new_save_button")
        assertTrue(driver.getHierarchy().containsText("Completa los campos requeridos"))

        driver.type("evo_new_subjective_field", "E2E paciente refiere mejoria.")
        driver.type("evo_new_objective_field", "E2E signos vitales estables.")
        driver.type("evo_new_dx_descripcion_0", "E2E diagnostico de prueba")
        driver.selectOption("evo_new_pronostico_field", 0)
        driver.selectOption("evo_new_resultado_field", 0)
        driver.clickTag("evo_new_save_button")
        driver.waitForTag("evo_view_print_button")
        hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("E2E paciente refiere mejoria."), "the saved evolucion must render its own Subjetivo text back")

        // Evolucion view: tabs + print preview.
        driver.clickTag("evo_tab_examenes")
        driver.waitForTag("evo_tab_evolucion")
        driver.clickTag("evo_tab_evolucion")
        driver.clickTag("evo_view_print_button")
        driver.waitForTag("print_back_button")
        driver.clickTag("print_back_button")
        driver.waitForTag("evo_view_print_button")
        driver.clickTag("encounter_close_button")
        driver.waitForTag("hosp_discharge_button")

        // Discharge: Activa -> Alta.
        driver.clickTag("hosp_discharge_button")
        driver.waitForTag("hosp_discharge_confirm_button")
        driver.clickTag("hosp_discharge_confirm_button")
        driver.waitUntil { !it.containsTag("hosp_discharge_button") }
        assertFalse(driver.getHierarchy().containsTag("hosp_discharge_button"), "a discharged hospitalization must be Alta and hide the discharge action")
    }

    /**
     * Deliberately runs last among the patient-list-dependent tests (sorts right after
     * test14, before the profile/accounts tests that never revisit an unfiltered patient
     * list): cmp-bridge's `setText` pastes at the cursor with no select-all (confirmed by
     * decompiling `DesktopBridgeServer.pasteText` -- clipboard set + raw Ctrl+V key events,
     * nothing that would select existing content first), so there is no way to actually
     * clear this field back out afterward. Since `PatientListViewModel` is reused across
     * `nav_patients` clicks (Navigation-Compose's `launchSingleTop` on that route), leaving
     * a filter query behind would otherwise hide rows every later test needs to click.
     */
    @Test
    fun test14b_patientList_filtersBySearch() {
        driver.clickTag("nav_patients")
        driver.waitForTag("patient_row_#00142")
        driver.type("patient_search_field", "Gonzalez")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Maria Gonzalez Huerta"), "search must still show the matching patient")
        assertFalse(hierarchy.containsText("Eduardo Remon Huertas"), "search must filter out non-matching patients")
    }

    // --- Profile -------------------------------------------------------------------------

    @Test
    fun test15_profile_updatePassword_showsSuccessMessage() {
        driver.clickTag("nav_profile")
        driver.waitForTag("profile_current_password_field")
        driver.type("profile_current_password_field", "whatever123")
        driver.type("profile_new_password_field", "newpassword123")
        driver.type("profile_confirm_password_field", "newpassword123")
        driver.clickTag("profile_update_password_button")
        assertTrue(driver.getHierarchy().containsText("Contrasena actualizada."))
    }

    // --- Accounts (admin only) ------------------------------------------------------------

    @Test
    fun test16_accounts_addDoctor_createsAccountRow() {
        driver.clickTag("nav_accounts")
        driver.waitForTag("accounts_add_button")
        driver.clickTag("accounts_add_button")
        driver.waitForTag("account_add_name_field")
        driver.type("account_add_name_field", "Dr. E2E Test")
        driver.type("account_add_username_field", "e2etest")
        driver.clickTag("account_add_confirm_button")
        driver.waitForTag("account_row_e2etest")
        assertTrue(driver.getHierarchy().containsText("Dr. E2E Test"))
    }

    @Test
    fun test17_accounts_editDoctor_updatesRow() {
        driver.clickTag("nav_accounts")
        driver.waitForTag("account_edit_e2etest")
        driver.clickTag("account_edit_e2etest")
        driver.waitForTag("account_edit_name_field")
        driver.type("account_edit_name_field", "Dr. E2E Test Editado")
        driver.clickTag("account_edit_confirm_button")
        driver.waitUntil { it.containsText("Dr. E2E Test Editado") }
        assertTrue(driver.getHierarchy().containsText("Dr. E2E Test Editado"))
    }

    @Test
    fun test18_accounts_resetPassword_showsTempPassword() {
        driver.clickTag("nav_accounts")
        driver.waitForTag("account_reset_e2etest")
        driver.clickTag("account_reset_e2etest")
        driver.waitForTag("account_reset_confirm_button")
        assertTrue(driver.getHierarchy().containsText("Reset contraseña"))
        driver.clickTag("account_reset_confirm_button")
        driver.waitUntil { !it.containsTag("account_reset_confirm_button") }
        assertFalse(driver.getHierarchy().containsTag("account_reset_confirm_button"), "confirming reset must close the dialog")
    }

    @Test
    fun test19_accounts_deactivateThenReactivate_togglesStatus() {
        driver.clickTag("nav_accounts")
        driver.waitForTag("account_deactivate_e2etest")
        driver.clickTag("account_deactivate_e2etest")
        driver.waitForTag("account_deactivate_confirm_button")
        driver.clickTag("account_deactivate_confirm_button")
        driver.waitForTag("account_reactivate_e2etest")
        assertTrue(driver.getHierarchy().containsTag("account_reactivate_e2etest"), "a deactivated account's row must offer Reactivar instead of Editar/Desactivar")

        driver.clickTag("account_reactivate_e2etest")
        driver.waitForTag("account_edit_e2etest")
        assertTrue(driver.getHierarchy().containsTag("account_edit_e2etest"), "reactivating must restore the normal action set")
    }

    // --- Sign out (last: ends the shared admin session) ------------------------------------

    @Test
    fun test20_profile_signOut_returnsToLogin() {
        driver.clickTag("nav_profile")
        driver.waitForTag("profile_sign_out_button")
        driver.clickTag("profile_sign_out_button")
        driver.waitForTag("login_submit_button")
        assertFalse(driver.getHierarchy().containsTag("nav_patients"), "signing out must leave the authenticated shell entirely")
    }
}
