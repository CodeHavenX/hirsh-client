package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import org.junit.FixMethodOrder
import org.junit.Ignore
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
 * Each @Test method here runs against its OWN freshly-launched app instance -- see
 * [DesktopE2ETest]/[WebE2ETest]'s [Before][org.junit.Before]/[After][org.junit.After]
 * (not [BeforeClass][org.junit.BeforeClass]/[AfterClass][org.junit.AfterClass]) setup.
 * `InMemoryHospitalizationRepository`/`InMemoryAccountRepository` (wired in `AppModule.kt`) are
 * in-process Koin singletons with no external persistence, so a fresh process launch is a full,
 * free reset back to their seeded fixture data below. **As of HISS-611, `AuthRepository`** is
 * `KtorAuthRepository`, and **as of HISS-622, `PatientRepository`** is `KtorPatientRepository` --
 * both backed by a real, separately-running instance of this project's backend (see
 * `network/ApiConfig.kt`) -- this whole suite now needs that backend up and reachable to pass at
 * all, and login-/patient-dependent tests use whatever the backend actually has (or a patient a
 * test creates itself through the real API), not an in-process fixture that resets for free.
 * [FixMethodOrder] + zero-padded numeric prefixes are kept only to make failures easy to read in
 * a stable, logical order; they carry no dependency meaning anymore. This replaced an earlier
 * one-process-per-CLASS design (all tests sharing one continuous login session) after that
 * design's cascading failures made a single unrelated bug (a screen with a broken
 * `verticalScroll` container) look like a dozen -- see PR history for
 * `PatientListScreen.kt`/`AccountsScreen.kt`/`ProfileScreen.kt`.
 *
 * A consequence: any test that needs to be signed in calls [loginAsAdmin] itself as its first
 * step. Likewise, since HISS-622, any test that needs a patient calls [registerE2eTestPatient]
 * itself to create one through the real backend rather than assuming a seeded id still exists --
 * the same pattern the accounts CRUD tests (`test17`-`test19`) already used for
 * [createE2eTestDoctorAccount] (that repository is still fake, unaffected by HISS-611/622, but the
 * "don't assume an earlier test's fixture" discipline is the same). Where a step needs to
 * create+immediately use a dynamically-generated id (a new patient, hospitalization, or evolucion
 * id, which cmp-bridge has no way to read out of a URL the way a browser location bar would), it
 * either stays inline in ONE test method, or -- when a later step needs to find that same row
 * again from a list without knowing its id -- matches on rendered content instead via
 * [tagOfNodeContaining].
 *
 * Seeded fixture data still referenced below (from `InMemoryHospitalizationRepository`/
 * `InMemoryAccountRepository`, seeded from `prototype/shared/data.js`): accounts `admin`/ADMIN.
 * Patients are no longer seeded fixtures as of HISS-622 -- every patient-dependent test below
 * registers its own. `test02`/`test03` below are `@Ignore`d: they need a seeded inactive account
 * and a seeded non-admin account respectively, and only an admin account is seeded in the real
 * backend as of this ticket -- re-enable once account provisioning is real too (HISS-651+).
 * `test06`/`test13` below need a hospitalization with a specific state (populated/Activa) but
 * `InMemoryHospitalizationRepository` itself is still fake and unaffected by HISS-622 -- they get
 * there by driving the real Admision flow ([admitPatient]) against a patient they just registered,
 * rather than by depending on that repository's own (now-orphaned) seeded hospitalizations.
 *
 * It is fine for a test here to fail -- some flows (documented per-test below) are known
 * gaps in the app itself (e.g. no backend yet) or in cmp-bridge's own web-driver coverage
 * (some fields/scroll report zero bounds in the web accessibility DOM as of
 * cmp-bridge-driver 0.2.0.0) rather than bugs in this suite.
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
    @Ignore("needs a seeded inactive account in the real backend -- only admin is seeded as of HISS-611")
    fun test02_login_inactiveAccount_rejected() {
        driver.type("login_username_field", "tveer")
        driver.type("login_password_field", "whatever123")
        driver.clickTag("login_submit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("login_submit_button"), "an inactive seeded account must not be allowed in")
        assertTrue(hierarchy.containsText("Usuario o contrasena incorrectos"))
    }

    @Test
    @Ignore("needs a seeded non-admin account in the real backend -- only admin is seeded as of HISS-611")
    fun test03_login_doctorAccount_success_hidesAccountsNav() {
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("nav_profile"))
        assertFalse(hierarchy.containsTag("nav_accounts"), "a DOCTOR-role session must not see the Cuentas nav item")
    }

    @Test
    fun test04_login_adminAccount_success_showsAccountsNav() {
        driver.loginAsAdmin()
        assertTrue(driver.getHierarchy().containsTag("nav_accounts"), "ADMIN-role session must see the Cuentas nav item")
    }

    // --- Patient list / record -------------------------------------------------------------

    @Test
    fun test05_patientList_showsRegisteredPatient() {
        driver.loginAsAdmin()
        val fullName = driver.registerE2eTestPatient()
        driver.clickTag("nav_patients")
        driver.waitForTag("patient_search_field")
        assertTrue(driver.getHierarchy().containsText(fullName), "a freshly-registered patient must show up in the list")
    }

    @Test
    fun test06_patientRecord_populated_showsHospitalizationAndProfileCard() {
        driver.loginAsAdmin()
        val fullName = driver.registerE2eTestPatient()
        val motivo = "E2E motivo ${System.nanoTime()}"
        driver.admitPatient(motivo = motivo)
        // Admision lands on the new hospitalization's own screen, not back on the record --
        // find the record row by rendered content since its real id is server-generated.
        driver.clickTag("nav_patients")
        val patientRowTag = checkNotNull(driver.getHierarchy().tagOfNodeContaining(fullName, "patient_row_")) {
            "expected a patient_row_* tag rendering \"$fullName\""
        }
        driver.clickTag(patientRowTag)
        driver.waitForTag("record_edit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(fullName))
        assertTrue(hierarchy.containsTag("record_new_hospitalization_button"))
        assertTrue(hierarchy.containsText(motivo), "the hospitalization just admitted must render its own VisitCard")
        assertTrue(hierarchy.containsText("Activa"), "a freshly-admitted hospitalization must show as Activa")
    }

    @Test
    fun test07_patientRecord_emptyHospitalizations_showsEmptyState() {
        driver.loginAsAdmin()
        val fullName = driver.registerE2eTestPatient()
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(fullName))
        assertTrue(
            hierarchy.containsText("Sin hospitalizaciones registradas"),
            "a freshly-registered patient has no hospitalizations yet and must show the empty-state copy",
        )
    }

    @Test
    fun test08_patientRecord_viewHistory_navigatesAndBack() {
        driver.loginAsAdmin()
        val fullName = driver.registerE2eTestPatient()
        driver.clickTag("record_history_link")
        driver.waitForTag("history_back_button")
        assertTrue(driver.getHierarchy().containsText(fullName), "history screen must be scoped to the patient it was opened from")
        driver.clickTag("history_back_button")
        driver.waitForTag("record_edit_button")
    }

    // --- Register / edit patient ------------------------------------------------------------

    @Test
    fun test09_registerPatient_blankFields_showsValidationError() {
        driver.loginAsAdmin()
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
        driver.loginAsAdmin()
        val existingName = driver.registerE2eTestPatient(firstName = "Zzzexisting", lastName = "Dupe")
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_first_name_field")
        // Substring match against the patient just registered above -- see
        // RegisterPatientViewModel.checkDuplicate(). Blurring onto the last-name field is what
        // fires the first-name field's onFocusChanged(false) that triggers the check.
        driver.type("register_first_name_field", "Zzzexisting")
        driver.clickTag("register_last_name_field")
        driver.waitForTag("register_duplicate_view_existing_link")
        assertTrue(driver.getHierarchy().containsText("Posible duplicado"))
        driver.clickTag("register_duplicate_view_existing_link")
        driver.waitForTag("record_edit_button")
        assertTrue(driver.getHierarchy().containsText(existingName), "the duplicate link must land on the existing patient's own record")
    }

    @Test
    fun test11_registerPatient_success_createsPatientAndNavigatesToRecord() {
        driver.loginAsAdmin()
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_submit_button")
        // The exact fields this ticket (HISS-622) changed: a real, caller-supplied
        // medicalRecordNumber replacing the old generated #XXXXX id, and firstName/lastName/
        // secondLastName replacing a single name field -- both asserted to round-trip through
        // the real backend below, making this the suite's golden-path check for that migration.
        val mrn = "HC-E2E-${System.nanoTime()}"
        val newName = "Zzz E2E Test Patient"
        driver.type("register_mrn_field", mrn)
        driver.type("register_first_name_field", "Zzz")
        driver.type("register_last_name_field", "E2E")
        driver.type("register_second_last_name_field", "Test Patient")
        driver.type("register_dni_field", "E2E-${System.nanoTime()}")
        driver.type("register_dob_field", "01/01/1990")
        driver.type("register_phone_field", "555-0100")
        driver.selectOption("register_sex_field", 0)
        driver.clickTag("register_submit_button")
        driver.waitForTag("record_edit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(newName), "a successful registration must land on the new patient's own record")
        assertTrue(hierarchy.containsText(mrn), "the medicalRecordNumber entered at registration must round-trip through the real backend")
    }

    @Test
    fun test12_editPatient_updatesPhoneAndSaves() {
        driver.loginAsAdmin()
        driver.registerE2eTestPatient()
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
        driver.loginAsAdmin()
        driver.registerE2eTestPatient()
        driver.admitPatient()
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("hosp_discharge_button"), "an Activa hospitalization must show the discharge action")
        assertTrue(
            hierarchy.containsText("Sin evoluciones registradas"),
            "a hospitalization with zero evoluciones must show the empty-state copy",
        )
    }

    // --- Full clinical documentation lifecycle: admision -> HC -> evolucion -> discharge -----

    @Ignore("see the settle-delay stopgap noted inline below -- CodeHavenX/hirsh-client#31")
    @Test
    fun test14_admisionToDischarge_fullHospitalizationLifecycle() {
        driver.loginAsAdmin()
        val fullName = driver.registerE2eTestPatient()
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
        assertTrue(hierarchy.containsText(fullName))
        assertTrue(hierarchy.containsTag("hosp_discharge_button"), "a freshly-admitted hospitalization must be Activa")

        // Historia Clinica: fill the default (Filiacion) section, then Motivo de Ingreso's checkboxes.
        driver.clickTag("hosp_open_hc_button")
        driver.waitForTag("hc_field_edad")
        driver.type("hc_field_edad", "34")
        // Filiacion's own field list is long enough to push hc_save_section_button below the
        // fold too -- see scrollDown's own doc.
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("hc_save_section_button")
        driver.clickTag("hc_nav_MOTIVO_INGRESO")
        driver.waitForTag("hc_motivo_option_riesgoSuicida")
        driver.clickTag("hc_motivo_option_riesgoSuicida")
        // Motivo de Ingreso's checkbox list is long enough to push hc_save_section_button below
        // the fold -- see scrollDown's own doc.
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("hc_save_section_button")
        driver.clickTag("encounter_close_button")
        driver.waitForTag("hosp_discharge_button")

        // A click here right after the encounter_close_button pop can silently fail to navigate:
        // Navigation-Compose appears to drop a navigate() issued before the popped-back
        // NavBackStackEntry's lifecycle reaches RESUMED, even though the screen has already
        // rendered (waitForTag above already saw valid bounds). Confirmed via a sibling button
        // in the same header (hosp_discharge_button, a pure local state change -- no navigate())
        // working fine in the same spot, and this exact click succeeding once a settle delay is
        // added. Tracked at https://github.com/CodeHavenX/hirsh-client/issues/31 -- this sleep
        // is a stopgap, not a real fix.
        Thread.sleep(4_000)

        // Nueva evolucion: required fields are Subjetivo, Objetivo, >=1 diagnosis, Pronostico, Evolucion.
        driver.clickTag("hosp_new_evolucion_button")
        driver.waitForTag("evo_new_subjective_field")
        // Nueva evolucion's field list is long enough to push evo_new_save_button below the
        // fold -- see scrollDown's own doc.
        driver.scrollDown("screen_scroll_container")
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

    @Test
    fun test14b_patientList_filtersBySearch() {
        driver.loginAsAdmin()
        val matchName = driver.registerE2eTestPatient(firstName = "Zzzmatch")
        driver.clickTag("nav_patients")
        val otherName = driver.registerE2eTestPatient(firstName = "Zzzother")
        driver.clickTag("nav_patients")
        driver.waitForTag("patient_search_field")
        driver.type("patient_search_field", "Zzzmatch")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(matchName), "search must still show the matching patient")
        assertFalse(hierarchy.containsText(otherName), "search must filter out non-matching patients")
    }

    // --- Profile -------------------------------------------------------------------------

    @Test
    fun test15_profile_updatePassword_blankFields_showsValidationError() {
        driver.loginAsAdmin()
        driver.clickTag("nav_profile")
        driver.waitForTag("profile_current_password_field")
        // profile_update_password_button sits below the fold -- see scrollDown's own doc.
        driver.scrollDown("profile_scroll_container")
        driver.clickTag("profile_update_password_button")
        assertTrue(driver.getHierarchy().containsText("Completa los campos requeridos"))
    }

    /**
     * `SetText`'s clipboard-based paste (Ctrl+A / Ctrl+V) silently no-ops on any field using
     * `visualTransformation = PasswordVisualTransformation()` on Compose Desktop -- confirmed by
     * a controlled A/B test (removing `visualTransformation` makes the exact same paste work;
     * `keyboardType = Password` alone does not reproduce it). `ProfileScreen`'s 3 password
     * fields are the only `PasswordVisualTransformation` usage in the app, which is why nothing
     * else in this suite hits it. Tracked at https://github.com/CRamsan/cmp-bridge/issues/32 --
     * ignored here rather than deleted so this regains coverage the moment that's fixed.
     */
    @Ignore("cmp-bridge can't type into password-masked fields on desktop -- see CRamsan/cmp-bridge#32")
    @Test
    fun test15b_profile_updatePassword_showsSuccessMessage() {
        driver.loginAsAdmin()
        driver.clickTag("nav_profile")
        driver.waitForTag("profile_current_password_field")
        driver.type("profile_current_password_field", "whatever123")
        driver.type("profile_new_password_field", "newpassword123")
        driver.type("profile_confirm_password_field", "newpassword123")
        // profile_update_password_button sits below the fold -- see scrollDown's own doc.
        driver.scrollDown("profile_scroll_container")
        driver.clickTag("profile_update_password_button")
        assertTrue(driver.getHierarchy().containsText("Contrasena actualizada."))
    }

    // --- Accounts (admin only) ------------------------------------------------------------

    @Test
    fun test16_accounts_addDoctor_createsAccountRow() {
        driver.loginAsAdmin()
        driver.createE2eTestDoctorAccount()
        assertTrue(driver.getHierarchy().containsText("Dr. E2E Test"))
    }

    @Test
    fun test17_accounts_editDoctor_updatesRow() {
        driver.loginAsAdmin()
        driver.createE2eTestDoctorAccount()
        driver.clickTag("account_edit_e2etest")
        driver.waitForTag("account_edit_name_field")
        driver.type("account_edit_name_field", "Dr. E2E Test Editado")
        driver.clickTag("account_edit_confirm_button")
        driver.waitUntil { it.containsText("Dr. E2E Test Editado") }
        assertTrue(driver.getHierarchy().containsText("Dr. E2E Test Editado"))
    }

    @Test
    fun test18_accounts_resetPassword_showsTempPassword() {
        driver.loginAsAdmin()
        driver.createE2eTestDoctorAccount()
        driver.clickTag("account_reset_e2etest")
        driver.waitForTag("account_reset_confirm_button")
        assertTrue(driver.getHierarchy().containsText("Reset contraseña"))
        driver.clickTag("account_reset_confirm_button")
        driver.waitUntil { !it.containsTag("account_reset_confirm_button") }
        assertFalse(driver.getHierarchy().containsTag("account_reset_confirm_button"), "confirming reset must close the dialog")
    }

    @Test
    fun test19_accounts_deactivateThenReactivate_togglesStatus() {
        driver.loginAsAdmin()
        driver.createE2eTestDoctorAccount()
        driver.clickTag("account_deactivate_e2etest")
        driver.waitForTag("account_deactivate_confirm_button")
        driver.clickTag("account_deactivate_confirm_button")
        driver.waitForTag("account_reactivate_e2etest")
        assertTrue(driver.getHierarchy().containsTag("account_reactivate_e2etest"), "a deactivated account's row must offer Reactivar instead of Editar/Desactivar")

        driver.clickTag("account_reactivate_e2etest")
        // Reactivating swaps the row back from a single "Reactivar" link to the full
        // Editar/Reset clave/Desactivar set, which (per createE2eTestDoctorAccount's own doc)
        // renders taller at this column width -- the row can grow past the previous scroll
        // position, pushing account_edit_e2etest below the fold again.
        driver.scrollDown("screen_scroll_container")
        driver.waitForTag("account_edit_e2etest")
        assertTrue(driver.getHierarchy().containsTag("account_edit_e2etest"), "reactivating must restore the normal action set")
    }

    // --- Sign out ---------------------------------------------------------------------------

    @Test
    fun test20_profile_signOut_returnsToLogin() {
        driver.loginAsAdmin()
        driver.clickTag("nav_profile")
        // profile_sign_out_button sits below the fold -- see scrollDown's own doc.
        driver.scrollDown("profile_scroll_container")
        driver.waitForTag("profile_sign_out_button")
        driver.clickTag("profile_sign_out_button")
        driver.waitForTag("login_submit_button")
        assertFalse(driver.getHierarchy().containsTag("nav_patients"), "signing out must leave the authenticated shell entirely")
    }
}
