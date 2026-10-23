package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.test.assertEquals
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
 * Nothing the app shows lives only in-process any more (HCEM-79 moved the clinical notes onto the
 * backend too), so a fresh launch resets only client state. **As of HISS-611, `AuthRepository`** is
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
 * the same pattern the accounts CRUD tests (`test16`-`test19`) use with [createE2eTestAccount],
 * which since HISS-652 creates a real, uniquely named account on the backend. Where a step needs to
 * create+immediately use a dynamically-generated id (a new patient, hospitalization, or evolucion
 * id, which cmp-bridge has no way to read out of a URL the way a browser location bar would), it
 * either stays inline in ONE test method, or -- when a later step needs to find that same row
 * again from a list without knowing its id -- matches on rendered content instead via
 * [tagOfNodeContaining].
 *
 * The only fixture referenced below is the backend's bootstrap admin account.
 * Patients are no longer seeded fixtures as of HISS-622 -- every patient-dependent test below
 * registers its own. `test02`/`test03` below are `@Ignore`d: they need a seeded inactive account
 * and a seeded non-admin account respectively, and only an admin account is seeded in the real
 * backend as of this ticket -- re-enable once account provisioning is real too (HISS-651+).
 * `test06`/`test13` below need a hospitalization with a specific state (populated/Activa) -- they
 * get there by driving the real Admision flow ([admitPatient]) against a patient they just
 * registered. Since HISS-634 that admission is real too ([ensureAdmissionCatalog] provides the
 * service, bed and physician it picks).
 *
 * It is fine for a test here to fail -- some flows (documented per-test below) are known
 * gaps in the app itself (e.g. no backend yet) or in cmp-bridge's own web-driver coverage
 * (some fields/scroll report zero bounds in the web accessibility DOM as of
 * cmp-bridge-driver 0.2.0.0) rather than bugs in this suite.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class HissE2EScenarios {

    protected abstract val driver: BridgeDriver

    /** The text on the clipboard the app writes to, or null where this JVM can't read it (a browser's). */
    protected open fun systemClipboardText(): String? = null

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
        val patient = driver.registerE2eTestPatient()
        driver.clickTag("nav_patients")
        driver.waitForTag("patient_search_field")
        assertTrue(driver.getHierarchy().containsText(patient.fullName), "a freshly-registered patient must show up in the list")
    }

    @Test
    fun test06_patientRecord_populated_showsHospitalizationAndProfileCard() {
        driver.loginAsAdmin()
        val patient = driver.registerE2eTestPatient()
        val motivo = "E2E motivo ${System.nanoTime()}"
        driver.admitPatient(motivo = motivo)
        // Admision lands on the new hospitalization's own screen, not back on the record --
        // reopenPatientRecord's own doc explains why this goes through a search rather than
        // clicking a row found in the unfiltered list.
        driver.reopenPatientRecord(patient)
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(patient.fullName))
        assertFalse(
            hierarchy.containsTag("record_new_hospitalization_button"),
            "a patient with an open stay can't be admitted again (HISS-634)",
        )
        assertTrue(hierarchy.containsText(motivo), "the hospitalization just admitted must render its own VisitCard")
        assertTrue(hierarchy.containsText("Activa"), "a freshly-admitted hospitalization must show as Activa")
    }

    @Test
    fun test07_patientRecord_emptyHospitalizations_showsEmptyState() {
        driver.loginAsAdmin()
        val patient = driver.registerE2eTestPatient()
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(patient.fullName))
        assertTrue(
            hierarchy.containsText("Sin hospitalizaciones registradas"),
            "a freshly-registered patient has no hospitalizations yet and must show the empty-state copy",
        )
    }

    @Test
    fun test08_patientRecord_viewAccessLog_showsRealTrailAndBack() {
        // HISS-624: the history screen reads the backend's own access trail. Opening the record
        // (a GET) and editing it (a PATCH) should each leave an entry the audit filter wrote --
        // asserting both checks the client's action mapping against what the server really logs.
        driver.loginAsAdmin()
        val patient = driver.registerE2eTestPatient()
        driver.clickTag("record_edit_button")
        driver.waitForTag("edit_phone_field")
        driver.type("edit_phone_field", "555-7777")
        driver.clickTag("edit_save_button")
        driver.waitForTag("record_edit_button")
        // Same exit-transition gap as test12: wait for the edit form to leave before clicking on.
        driver.waitUntil { !it.containsTag("edit_phone_field") }

        driver.clickTag("record_history_link")
        driver.waitForTag("history_back_button")
        driver.waitUntil { it.containsTag("history_empty") || it.tagsWithPrefix("history_row_").isNotEmpty() || it.containsTag("history_error") }
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(patient.fullName), "the access log must be scoped to the patient it was opened from")
        assertFalse(hierarchy.containsTag("history_forbidden"), "an admin holds AUDIT_READ and must see the trail")
        assertFalse(hierarchy.containsTag("history_error"), "the real access-log request must succeed")
        assertTrue(hierarchy.containsText("Consulta del registro"), "opening the record must show up as a RECORD_READ entry")
        assertTrue(hierarchy.containsText("Modificacion de datos"), "saving the edit must show up as a PATIENT_MODIFIED entry")

        driver.clickTag("history_back_button")
        driver.waitForTag("record_edit_button")
    }

    // --- Register / edit patient ------------------------------------------------------------

    @Test
    fun test09_registerPatient_blankFields_showsValidationError() {
        driver.loginAsAdmin()
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_mrn_field")
        // register_submit_button sits below the fold at this window size -- see scrollDown's own doc.
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("register_submit_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsTag("register_submit_button"), "blank submit must not navigate away from the register form")
        assertTrue(hierarchy.containsText("Completa los campos requeridos"))
    }

    @Ignore(
        "registering a second patient in the same process re-lands on the register screen still " +
            "showing the FIRST registration's own field values -- confirmed via a live run that " +
            "this is stale/duplicated composition (the prior register screen's nodes are still " +
            "present and interactable, not merely a rendering artifact), not a test-authoring " +
            "issue. Root cause is in Navigation-Compose/composition disposal, not PatientRepository " +
            "-- out of scope for HISS-622; needs its own investigation before re-enabling.",
    )
    @Test
    fun test10_registerPatient_duplicateName_showsWarningAndNavigatesToExisting() {
        driver.loginAsAdmin()
        val existingPatient = driver.registerE2eTestPatient(firstName = "Zzzexisting", lastName = "Dupe")
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
        assertTrue(driver.getHierarchy().containsText(existingPatient.fullName), "the duplicate link must land on the existing patient's own record")
    }

    @Test
    fun test11_registerPatient_success_createsPatientAndNavigatesToRecord() {
        driver.loginAsAdmin()
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_mrn_field")
        // The exact fields this ticket (HISS-622) changed: a real, caller-supplied
        // medicalRecordNumber replacing the old generated #XXXXX id, and firstName/lastName/
        // secondLastName replacing a single name field -- both asserted to round-trip through
        // the real backend below, making this the suite's golden-path check for that migration.
        // Truncated the same way registerE2eTestPatient() does -- the real backend rejects a
        // medicalRecordNumber/documentNumber this long (confirmed via a live run: submitting the
        // untruncated System.nanoTime() surfaced "No se pudo registrar el paciente").
        val suffix = System.nanoTime().toString().takeLast(9)
        val mrn = "HC-E2E-$suffix"
        val newName = "Zzz E2E Test Patient"
        driver.type("register_mrn_field", mrn)
        driver.type("register_first_name_field", "Zzz")
        driver.type("register_last_name_field", "E2E")
        driver.type("register_second_last_name_field", "Test Patient")
        driver.type("register_dni_field", "E2E-$suffix")
        driver.type("register_dob_field", "01/01/1990")
        driver.type("register_phone_field", "555-0100")
        // register_sex_field/register_submit_button sit below (or right at) the fold at this
        // window size -- see scrollDown's own doc.
        driver.scrollDown("screen_scroll_container")
        driver.selectOption("register_sex_field", 0)
        driver.clickTag("register_submit_button")
        driver.waitForTag("record_edit_button")
        // The outgoing register form stays in the tree during its exit transition, still holding
        // the typed MRN/phone -- asserting before it's gone would pass even if the backend never
        // stored them (confirmed: the phone check below passed with HISS-626's fix reverted).
        driver.waitUntil { !it.containsTag("register_phone_field") }
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(newName), "a successful registration must land on the new patient's own record")
        assertTrue(hierarchy.containsText(mrn), "the medicalRecordNumber entered at registration must round-trip through the real backend")
        assertTrue(hierarchy.containsText("555-0100"), "the phone entered at registration must round-trip through the real backend")
    }

    @Test
    fun test11b_registerPatient_withAllergies_savesEachOnTheNewRecord() {
        // HISS-625: allergies entered at registration stay local until the patient exists, then
        // each is posted to the real /patients/{id}/allergies sub-resource as its own entry.
        driver.loginAsAdmin()
        driver.clickTag("nav_patients")
        driver.clickTag("patient_register_button")
        driver.waitForTag("register_mrn_field")
        val suffix = System.nanoTime().toString().takeLast(9)
        val medication = "E2E medicamento $suffix"
        val food = "E2E alimento $suffix"
        driver.type("register_mrn_field", "HC-E2E-$suffix")
        driver.type("register_first_name_field", "Zzz")
        driver.type("register_last_name_field", "E2E")
        driver.type("register_second_last_name_field", "Allergies")
        driver.type("register_dni_field", "E2E-$suffix")
        driver.type("register_dob_field", "01/01/1990")
        driver.type("register_phone_field", "555-0100")

        driver.clickTag("allergy_add_button")
        driver.waitForTag("allergy_description_field")
        driver.selectOption("allergy_type_field", 0) // Medicamento
        driver.type("allergy_description_field", medication)
        driver.selectOption("allergy_severity_field", 3) // Sin graduar, Leve, Moderada, Severa
        driver.clickTag("allergy_save_button")
        driver.waitUntil { it.containsText(medication) && !it.containsTag("allergy_form") }

        driver.clickTag("allergy_add_button")
        driver.waitForTag("allergy_description_field")
        driver.selectOption("allergy_type_field", 1) // Alimento
        driver.type("allergy_description_field", food)
        driver.clickTag("allergy_save_button")
        driver.waitUntil { it.containsText(food) && !it.containsTag("allergy_form") }

        driver.scrollDown("screen_scroll_container")
        driver.selectOption("register_sex_field", 0)
        driver.clickTag("register_submit_button")
        driver.waitForTag("record_edit_button")
        // Same exit-transition gap as test11: the departing form still renders both local entries.
        driver.waitUntil { !it.containsTag("register_phone_field") }
        driver.waitUntil { it.containsText(medication) && it.containsText(food) }
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Severa"), "the medication's severity must round-trip through the real backend")
        assertTrue(hierarchy.containsText("Sin graduar"), "an allergy left ungraded must stay ungraded, not default to a real grade")
        assertEquals(
            "allergy_row_0",
            hierarchy.tagOfNodeContaining(medication, "allergy_row_"),
            "the record lists allergies newest first, so registration must post them in reverse to keep entry order",
        )
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
        // Same exit-transition gap as test12b: until the edit form leaves the tree, its field still
        // holds the typed phone, and the check below passed with the phone dropped from the PATCH.
        driver.waitUntil { !it.containsTag("edit_phone_field") }
        assertTrue(driver.getHierarchy().containsText("555-9999"), "the edited phone number must show back on the record screen")
    }

    @Test
    fun test12b_editPatient_addsEditsAndRemovesAllergy_reflectedOnRecord() {
        // HISS-623: each allergy action saves immediately against the real
        // /patients/{id}/allergies sub-resource -- "Cancelar" (not "Guardar cambios") is used to
        // leave the edit screen below precisely to prove the allergy was saved on its own.
        driver.loginAsAdmin()
        driver.registerE2eTestPatient()
        val suffix = System.nanoTime().toString().takeLast(9)
        val agent = "E2E alergia $suffix"
        val observations = "E2E revisado $suffix"

        driver.clickTag("record_edit_button")
        driver.waitForTag("edit_phone_field")
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("allergy_add_button")
        driver.waitForTag("allergy_description_field")
        driver.selectOption("allergy_type_field", 0) // Medicamento
        driver.type("allergy_description_field", agent)
        driver.scrollDown("screen_scroll_container")
        driver.selectOption("allergy_severity_field", 3) // Sin graduar, Leve, Moderada, Severa
        driver.clickTag("allergy_save_button")
        driver.waitUntil { it.containsText(agent) && !it.containsTag("allergy_form") }

        driver.scrollDown("screen_scroll_container")
        driver.clickTag("edit_cancel_button")
        driver.waitForTag("record_edit_button")
        driver.waitUntil { it.containsText(agent) }
        assertTrue(driver.getHierarchy().containsText("Severa"), "the allergy's severity must round-trip through the real backend")
        // The outgoing edit screen stays in the tree for its exit transition: until it's gone, a
        // "record_edit_button" click can be dropped, and a wait for "edit_phone_field" below would
        // match the OLD, departing screen instead of the new one (confirmed via a live run).
        driver.waitUntil { !it.containsTag("edit_phone_field") }

        // Edit: the only PATCH-able fields are severity and observations (see Allergy's doc
        // comment) -- this is the one step that exercises the real PatchAllergyRequest.
        driver.clickTag("record_edit_button")
        driver.waitForTag("edit_phone_field")
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("allergy_edit_0")
        driver.waitUntil { it.containsTag("allergy_form") }
        driver.scrollDown("screen_scroll_container")
        // Already graded, so "Sin graduar" isn't offered here: Leve, Moderada, Severa.
        driver.selectOption("allergy_severity_field", 0) // Leve
        driver.type("allergy_observations_field", observations)
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("allergy_save_button")
        driver.waitUntil { it.containsText(observations) && !it.containsTag("allergy_form") }

        driver.scrollDown("screen_scroll_container")
        driver.clickTag("edit_cancel_button")
        driver.waitForTag("record_edit_button")
        driver.waitUntil { it.containsText(observations) }
        val afterEdit = driver.getHierarchy()
        assertTrue(afterEdit.containsText(agent), "editing must not change the agent itself")
        assertTrue(afterEdit.containsText("Leve"), "the revised severity must round-trip through the real backend")
        assertFalse(afterEdit.containsText("Severa"), "the old severity must be gone after the PATCH")
        driver.waitUntil { !it.containsTag("edit_phone_field") }

        driver.clickTag("record_edit_button")
        driver.waitForTag("edit_phone_field")
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("allergy_delete_0")
        driver.clickTag("allergy_confirm_delete_0")
        driver.waitUntil { !it.containsText(agent) }

        driver.scrollDown("screen_scroll_container")
        driver.clickTag("edit_cancel_button")
        driver.waitForTag("record_edit_button")
        driver.waitUntil { !it.containsText(agent) }
        assertFalse(driver.getHierarchy().containsText(agent), "a removed allergy must no longer show on the record")
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

    // --- Clinical notes as backend documents (HCEM-79) ----------------------------------------

    @Test
    fun test13b_historiaClinicaDraft_blocksDischargeUntilSigned() {
        driver.loginAsAdmin()
        driver.registerE2eTestPatient()
        driver.admitPatient()

        driver.clickTag("hosp_open_hc_button")
        driver.waitForTag("hc_field_edad")
        driver.type("hc_field_edad", "34")
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("hc_save_section_button")
        driver.waitUntil { it.containsText("Pendiente de firma") }
        driver.clickTag("encounter_close_button")
        driver.waitForTag("hosp_discharge_button")
        assertTrue(driver.getHierarchy().containsText("Pendiente de firma"), "the stay must show the Historia Clinica as a draft")

        // Same settle-delay stopgap as test14 (CodeHavenX/hirsh-client#31).
        Thread.sleep(4_000)
        driver.clickTag("hosp_discharge_button")
        driver.waitForTag("discharge_unsigned_notes")
        assertTrue(driver.getHierarchy().containsText("Historia clinica"), "the unsigned Historia Clinica must be listed")
    }

    /** The bootstrap admin holds DOCUMENT_SIGN; the backend also needs a medical license on that account. */
    @Test
    fun test13c_nuevaEvolucion_signerSavesASignedNote() {
        driver.loginAsAdmin()
        driver.registerE2eTestPatient()
        driver.admitPatient()

        driver.clickTag("hosp_new_evolucion_button")
        driver.waitForTag("evo_new_subjective_field")
        driver.type("evo_new_subjective_field", "E2E nota firmada.")
        driver.type("evo_new_objective_field", "E2E signos vitales estables.")
        driver.type("evo_new_dx_descripcion_0", "E2E diagnostico de prueba")
        driver.selectOption("evo_new_pronostico_field", 0)
        driver.selectOption("evo_new_resultado_field", 0)
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("evo_new_save_button")
        driver.waitForTag("evo_view_print_button")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Registrada · solo lectura"), "a signer's evolucion must be signed on save")
        assertFalse(hierarchy.containsTag("evo_view_sign_button"), "a signed evolucion offers no signature")

        // HCEM-81: the plain-text copy, read back wherever the target's clipboard is readable.
        driver.clickTag("evo_view_copy_sis_button")
        driver.waitForTag("evo_view_copied")
        systemClipboardText()?.let { copied ->
            assertTrue(copied.startsWith("=== EVOLUCION CLINICA - PSIQUIATRIA ==="), copied)
            assertTrue(copied.contains("[SUBJETIVO] E2E nota firmada."), copied)
            assertTrue(copied.contains("[DIAGNOSTICOS] E2E diagnostico de prueba"), copied)
        }
    }

    @Test
    fun test13d_applyScale_suicideRiskIsConfirmedAndShownOnTheStay() {
        driver.loginAsAdmin()
        driver.registerE2eTestPatient()
        driver.admitPatient()

        driver.clickTag("hosp_new_evolucion_button")
        driver.waitForTag("evo_new_subjective_field")
        driver.type("evo_new_subjective_field", "E2E refiere ideas de muerte pasivas.")
        driver.type("evo_new_objective_field", "E2E afecto aplanado.")
        driver.type("evo_new_dx_descripcion_0", "E2E episodio depresivo")
        driver.selectOption("evo_new_pronostico_field", 0)
        driver.selectOption("evo_new_resultado_field", 0)
        driver.scrollDown("screen_scroll_container")
        driver.clickTag("evo_new_save_button")
        driver.waitForTag("evo_view_print_button")

        // HCEM-80: PHQ-9 with item 9 above "Ningun dia" (total 17) raises the suicide-risk alert.
        driver.clickTag("evo_view_apply_scale_button")
        driver.waitForTag("scale_item_q1", timeoutMs = 15_000)
        (1..9).forEach { item ->
            val tag = "scale_option_q${item}_${if (item == 9) 1 else 2}"
            driver.scrollUntilVisible(tag, "screen_scroll_container")
            driver.clickTag(tag)
        }
        driver.waitForTag("scale_suicide_alert")
        assertTrue(driver.getHierarchy().containsText("17"), "the running score must reach 17")

        driver.scrollUntilVisible("scale_save_button", "screen_scroll_container")
        driver.clickTag("scale_save_button")
        driver.clickTag("scale_alert_confirm_button")
        driver.waitForTag("evo_view_print_button")

        driver.clickTag("encounter_close_button")
        driver.waitForTag("hosp_new_evolucion_button")
        driver.scrollUntilVisible("hosp_scales_section", "screen_scroll_container")
        driver.scrollDown("screen_scroll_container")
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText("Alerta riesgo suicida"), "the stay must flag the result that raised the alert")
        assertTrue(hierarchy.containsText("Moderadamente severa"), hierarchy.allTexts().toString())
    }

    // --- Full clinical documentation lifecycle: admision -> HC -> evolucion -> discharge -----

    @Ignore("see the settle-delay stopgap noted inline below -- CodeHavenX/hirsh-client#31")
    @Test
    fun test14_admisionToDischarge_fullHospitalizationLifecycle() {
        driver.loginAsAdmin()
        val patient = driver.registerE2eTestPatient()
        ensureAdmissionCatalog()
        driver.clickTag("record_new_hospitalization_button")
        driver.waitForTag("admision_submit_button")

        // Blank submit is rejected first, mirroring the register/nueva-evolucion validation pattern.
        driver.clickTag("admision_submit_button")
        assertTrue(driver.getHierarchy().containsText("Completa los campos requeridos"))

        driver.fillAdmissionPickers()
        driver.type("admision_motivo_field", "E2E: sintomas respiratorios")
        driver.clickTag("admision_submit_button")
        driver.waitForTag("hosp_discharge_button")
        var hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(patient.fullName))
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

        // Discharge: Activa -> Alta, through the discharge form. The diagnosis typeahead searches
        // the backend's ICD-10 catalog (seeded with placeholder Chapter V codes for now).
        driver.clickTag("hosp_discharge_button")
        driver.waitForTag("discharge_submit_button")
        driver.selectOption("discharge_type_field", 0)
        driver.selectOption("discharge_condition_field", 0)
        driver.type("discharge_diagnosis_field", "F32")
        driver.waitForTag("discharge_diagnosis_option_0")
        driver.clickTag("discharge_diagnosis_option_0")
        driver.waitForTag("discharge_diagnosis_selected")
        driver.clickTag("discharge_submit_button")
        driver.waitForTag("hosp_open_hc_button")
        driver.waitUntil { !it.containsTag("hosp_discharge_button") }
        assertFalse(driver.getHierarchy().containsTag("hosp_discharge_button"), "a discharged hospitalization must be Alta and hide the discharge action")
    }

    @Test
    fun test14b_patientList_filtersBySearch() {
        driver.loginAsAdmin()
        val matchPatient = driver.registerE2eTestPatient(firstName = "Zzzmatch")
        driver.clickTag("nav_patients")
        val otherPatient = driver.registerE2eTestPatient(firstName = "Zzzother")
        driver.clickTag("nav_patients")
        driver.waitForTag("patient_search_field")
        driver.type("patient_search_field", "Zzzmatch")
        // The filtered recompute can take a moment longer than type()'s own settle delay once the
        // (shared, real-backend) patient list has grown large -- wait for the non-match to actually
        // drop out rather than asserting against a snapshot that might still be mid-recompute.
        driver.waitUntil { !it.containsText(otherPatient.fullName) }
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(matchPatient.fullName), "search must still show the matching patient")
        assertFalse(hierarchy.containsText(otherPatient.fullName), "search must filter out non-matching patients")
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
    fun test16_accounts_addAccount_createsAccountRow() {
        driver.loginAsAdmin()
        val username = driver.createE2eTestAccount()
        val hierarchy = driver.getHierarchy()
        assertTrue(hierarchy.containsText(username))
        assertTrue(hierarchy.containsTag("account_edit_$username"), "a new account is active, so it offers Editar")
    }

    @Test
    fun test17_accounts_editAccount_savesProfile() {
        driver.loginAsAdmin()
        val username = driver.createE2eTestAccount()
        val phone = "555-${System.nanoTime().toString().takeLast(4)}"
        driver.clickTag("account_edit_$username")
        driver.waitForTag("account_edit_phone_field")
        assertFalse(driver.getHierarchy().containsTag("account_edit_name_field"), "the API can't rename an account (HISS-653)")
        driver.type("account_edit_phone_field", phone)
        driver.clickTag("account_edit_confirm_button")
        driver.waitUntil { !it.containsTag("account_edit_confirm_button") }

        // The phone isn't a table column, so reopen the dialog to read it back.
        driver.clickTag("account_edit_$username")
        driver.waitForTag("account_edit_phone_field")
        assertTrue(driver.getHierarchy().containsText(phone), "the saved phone must come back from the backend")
    }

    @Test
    fun test18_accounts_resetPassword_showsGeneratedPassword() {
        driver.loginAsAdmin()
        val username = driver.createE2eTestAccount()
        driver.clickTag("account_reset_$username")
        driver.waitForTag("account_reset_confirm_button")
        assertTrue(driver.getHierarchy().containsText("Reset contraseña"))
        driver.clickTag("account_reset_confirm_button")
        driver.waitForTag("account_reset_password_field")

        driver.clickTag("account_reset_confirm_button")
        driver.waitUntil { !it.containsTag("account_reset_confirm_button") }
        assertFalse(driver.getHierarchy().containsTag("account_reset_password_field"), "Listo must close the dialog")
    }

    @Test
    fun test19_accounts_deactivateThenReactivate_togglesStatus() {
        driver.loginAsAdmin()
        val username = driver.createE2eTestAccount()
        driver.clickTag("account_deactivate_$username")
        driver.waitForTag("account_deactivate_confirm_button")
        driver.clickTag("account_deactivate_confirm_button")
        driver.waitForTag("account_reactivate_$username")
        assertTrue(
            driver.getHierarchy().containsTag("account_reactivate_$username"),
            "a deactivated account's row must offer Reactivar instead of Editar/Desactivar",
        )

        driver.clickTag("account_reactivate_$username")
        // Reactivating swaps the single "Reactivar" link back to the full action set, which can
        // render taller and push the row's links below the fold again.
        driver.scrollDown("screen_scroll_container")
        driver.waitForTag("account_edit_$username")
        assertTrue(driver.getHierarchy().containsTag("account_edit_$username"), "reactivating must restore the normal action set")
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
