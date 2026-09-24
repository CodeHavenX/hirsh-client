package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.HierarchyNode
import com.cramsan.cmpbridge.driver.BridgeDriver
import com.cramsan.cmpbridge.driver.BridgeTimeoutException
import com.cramsan.cmpbridge.driver.TagVisibility
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

/**
 * Small helpers on top of [BridgeDriver]/[HierarchyNode] shared by every scenario in
 * [HissE2EScenarios]. cmp-bridge's `click`/`setText` are tag-only (no coordinate or
 * text-based fallback -- see .claude/skills/run-desktop/SKILL.md), so every interaction
 * below goes through a `testTag`; `getHierarchy()`'s tree is used to read rendered text
 * back for assertions regardless of whether a node has a tag.
 */

/**
 * Restores cmp-bridge-driver 0.1.0.3's synchronous `waitForTag` -- removed in 0.2.0.0 in favor of
 * the suspend `waitForTagVisibility`/`waitForText`. JUnit4 test methods (and this whole call chain
 * below) are plain synchronous functions, so this bridges via [runBlocking] rather than converting
 * every scenario to suspend. Safe to non-null-assert: every caller here only ever waits for
 * [TagVisibility.VISIBLE], which [BridgeDriver.waitForTagVisibility] always returns non-null for.
 */
fun BridgeDriver.waitForTag(tag: String, timeoutMs: Long = 15_000): HierarchyNode =
    runBlocking { checkNotNull(waitForTagVisibility(tag, TagVisibility.VISIBLE, timeoutMs.milliseconds)) }

/** Depth-first collection of every non-blank [HierarchyNode.text] in the tree. */
fun HierarchyNode.allTexts(): List<String> = buildList {
    text?.takeIf { it.isNotBlank() }?.let(::add)
    children.forEach { addAll(it.allTexts()) }
}

/** True if this node or any descendant carries [tag] as its [HierarchyNode.testTag]. */
fun HierarchyNode.containsTag(tag: String): Boolean =
    testTag == tag || children.any { it.containsTag(tag) }

/** True if any rendered text in the tree contains [substring]. */
fun HierarchyNode.containsText(substring: String): Boolean = allTexts().any { it.contains(substring) }

/** First rendered text satisfying [predicate], depth-first -- e.g. reading back a generated id. */
fun HierarchyNode.firstTextMatching(predicate: (String) -> Boolean): String? = allTexts().firstOrNull(predicate)

/**
 * The testTag of the nearest node prefixed with [tagPrefix] whose own subtree renders [text] --
 * lets a caller click a dynamically-generated row (e.g. a patient a test just registered through
 * the real backend, whose server-generated id cmp-bridge has no way to read out of a URL) by
 * matching on rendered content instead of a hardcoded id.
 */
fun HierarchyNode.tagOfNodeContaining(text: String, tagPrefix: String): String? {
    if (testTag?.startsWith(tagPrefix) == true && containsText(text)) return testTag
    return children.firstNotNullOfOrNull { it.tagOfNodeContaining(text, tagPrefix) }
}

/**
 * Waits for [tag] to exist, then clicks it -- covers a freshly-composed screen's first frame.
 * The trailing settle delay matters even for a click: every ViewModel action here goes
 * through `viewModelScope.launch { ... }`, so reading [BridgeDriver.getHierarchy] immediately
 * after `click()` can race the coroutine dispatch and observe pre-click state (seen concretely
 * on the blank-credentials login case, which updates inline without navigating -- a `waitForTag`
 * on the next screen isn't there to absorb the race the way it is for a navigating click).
 */
fun BridgeDriver.clickTag(tag: String, timeoutMs: Long = 10_000) {
    waitForTag(tag, timeoutMs)
    click(tag)
    Thread.sleep(300)
}

/**
 * Waits for [tag], then sets its text. Desktop's `setText` goes through the system
 * clipboard + paste (see the run-desktop skill's gotchas), which can race when fired
 * back-to-back into different fields -- the trailing settle delay keeps consecutive
 * calls from stepping on each other on both drivers, at the cost of test speed.
 */
fun BridgeDriver.type(tag: String, text: String, timeoutMs: Long = 10_000) {
    waitForTag(tag, timeoutMs)
    setText(tag, text)
    Thread.sleep(300)
}

/**
 * Scrolls [containerTag] down by [deltaY] -- needed before interacting with any tag below the
 * visible viewport of a `verticalScroll` container: the bridge reports zero bounds for a node
 * scrolled outside the window's visible clip rect (confirmed by dumping the actual hierarchy
 * JSON for `ProfileScreen`'s `profile_sign_out_button`/`profile_update_password_button` on
 * Compose Desktop's default 800x600 window -- both report `x=0, y=0, width=0, height=0` until
 * scrolled into view), so no amount of waiting brings such a tag into [BridgeDriver.getBounds]'s
 * view. [deltaY] defaults large enough to reach the bottom of any screen this suite drives;
 * scrolling past a container's actual content end is a no-op, not an error.
 */
fun BridgeDriver.scrollDown(containerTag: String, deltaY: Int = 1_000, times: Int = 3) {
    repeat(times) {
        scroll(containerTag, deltaY)
        Thread.sleep(300)
    }
}

/**
 * Signs out first if the current page is already authenticated, otherwise does nothing.
 * [DesktopE2ETest] relaunches the whole app process per test (see its doc comment), so this is
 * always a no-op there -- every test already starts at a fresh login screen. [WebE2ETest] shares
 * one browser page across its entire class instead (a per-test browser relaunch proved far too
 * slow for this app's wasmJs payload size -- see its own doc comment), so a later test can land
 * here still signed in from an earlier one and needs this to reach the login screen at all.
 */
private fun BridgeDriver.ensureSignedOut() {
    if (!getHierarchy().containsTag("nav_profile")) return
    clickTag("nav_profile")
    // profile_sign_out_button sits below the fold -- see scrollDown's own doc.
    scrollDown("profile_scroll_container")
    waitForTag("profile_sign_out_button")
    clickTag("profile_sign_out_button")
    waitForTag("login_submit_button")
}

/**
 * Logs in as the real backend's seeded admin account and waits for `nav_accounts`, confirming
 * the ADMIN-only nav item rendered. Every scenario below calls this itself as its first step
 * rather than relying on an earlier test's login -- see [ensureSignedOut]'s doc comment for why
 * that's a no-op on desktop but not on web.
 *
 * Credentials come from the `HIRSH_ADMIN_USERNAME`/`HIRSH_ADMIN_PASSWORD` env vars (the same
 * ones the backend itself reads to bootstrap that account -- see its README), not a literal
 * here: this suite runs against a real, separately-running backend as of HISS-611, not
 * `FakeAuthRepository`'s in-process fixtures, so there's no fixed password to hardcode.
 */
fun BridgeDriver.loginAsAdmin() {
    ensureSignedOut()
    type("login_username_field", System.getenv("HIRSH_ADMIN_USERNAME") ?: error(
        "HIRSH_ADMIN_USERNAME is not set -- required to log in against the real backend (HISS-611)",
    ))
    type("login_password_field", System.getenv("HIRSH_ADMIN_PASSWORD") ?: error(
        "HIRSH_ADMIN_PASSWORD is not set -- required to log in against the real backend (HISS-611)",
    ))
    clickTag("login_submit_button")
    waitForTag("nav_accounts")
}

/**
 * Creates the `e2etest` doctor account used by the accounts CRUD scenarios (edit/reset/
 * deactivate) if it doesn't already exist, then waits for its row to appear either way. On
 * [DesktopE2ETest] (fresh app per test) it never already exists, so every scenario creates it
 * itself rather than depending on a `test16`-equivalent having run first. On [WebE2ETest]
 * (one shared page for the whole class -- see its doc comment) an earlier scenario's row is
 * still there, so this skips straight to waiting for it instead of trying to create a duplicate.
 * Caller must already be logged in as ADMIN (see [loginAsAdmin]).
 */
fun BridgeDriver.createE2eTestDoctorAccount() {
    clickTag("nav_accounts")
    if (!getHierarchy().containsTag("account_row_e2etest")) {
        waitForTag("accounts_add_button")
        clickTag("accounts_add_button")
        waitForTag("account_add_name_field")
        type("account_add_name_field", "Dr. E2E Test")
        type("account_add_username_field", "e2etest")
        clickTag("account_add_confirm_button")
    }
    // The row (whether just-created or pre-existing) is appended last -- below the fold once
    // the seeded 5 + this one no longer fit the window; see scrollDown's own doc.
    scrollDown("screen_scroll_container")
    waitForTag("account_row_e2etest")
    // The e2etest row renders taller than the seeded rows (its action buttons wrap onto their
    // own line at this column width), so it only reaches its final height after this point --
    // the scrollDown above, issued before the row existed, undershoots that final height and
    // can leave account_deactivate_e2etest just past the visible bottom. One more scroll now
    // that the row exists reaches the container's true bottom.
    scrollDown("screen_scroll_container")
}

/** [registerE2eTestPatient]'s result: the two identifiers its callers actually need back. */
data class RegisteredE2ePatient(val fullName: String, val medicalRecordNumber: String)

/**
 * Registers a fresh patient through the real, Ktor-backed `PatientRepository` (HISS-622) and
 * lands on that patient's own record screen. Every call gets its own [System.nanoTime]-derived
 * suffix for `medicalRecordNumber`/`documentNumber` so concurrent/repeated runs against the same
 * backend instance never collide on a real uniqueness constraint the old `InMemoryPatientRepository`
 * never enforced. Caller must already be logged in (see [loginAsAdmin]); this navigates to the
 * patient list itself.
 */
fun BridgeDriver.registerE2eTestPatient(
    firstName: String = "Zzz",
    lastName: String = "E2E",
    secondLastName: String = "Test",
): RegisteredE2ePatient {
    val suffix = System.nanoTime().toString().takeLast(9)
    val medicalRecordNumber = "HC-E2E-$suffix"
    clickTag("nav_patients")
    clickTag("patient_register_button")
    waitForTag("register_mrn_field")
    type("register_mrn_field", medicalRecordNumber)
    type("register_first_name_field", firstName)
    type("register_last_name_field", lastName)
    type("register_second_last_name_field", secondLastName)
    type("register_dni_field", "E2E-$suffix")
    type("register_dob_field", "01/01/1990")
    type("register_phone_field", "555-0100")
    // register_sex_field/register_submit_button sit below (or right at) the fold once the mrn/
    // firstName/lastName/secondLastName fields HISS-622 added push the form past the window's
    // visible height -- see scrollDown's own doc (confirmed via a live run: register_submit_button
    // exists in the tree with x=0,y=0,w=0,h=0 until scrolled into view; register_sex_field's own
    // margin is close enough to the fold to be flaky the same way).
    scrollDown("screen_scroll_container")
    selectOption("register_sex_field", 0)
    clickTag("register_submit_button")
    waitForTag("record_edit_button")
    return RegisteredE2ePatient("$firstName $lastName $secondLastName", medicalRecordNumber)
}

/**
 * Returns to the patient list and opens [patient]'s own record by searching for its unique
 * [RegisteredE2ePatient.medicalRecordNumber] first, rather than clicking a row found by scanning
 * the unfiltered list -- confirmed via a live run that clicking a row out of the full (and, on a
 * long-lived shared backend, ever-growing) list is unreliable: `PatientListViewModel`'s `init`
 * re-triggers a real `refresh()` network call every time this screen is (re)entered, and the
 * resulting full-list replacement can invalidate a row found a moment earlier before a click
 * lands on it. Filtering down to the one row matching this patient's own MRN first avoids ever
 * needing to re-resolve a row out of a list that's still settling.
 */
fun BridgeDriver.reopenPatientRecord(patient: RegisteredE2ePatient) {
    clickTag("nav_patients")
    waitForTag("patient_search_field")
    type("patient_search_field", patient.medicalRecordNumber)
    // Don't resolve the row until the search has actually filtered the list down to just this
    // patient -- reading the hierarchy the instant after typing can still see the unfiltered list.
    waitUntil(timeoutMs = 15_000) { tree ->
        val rows = tree.tagsWithPrefix("patient_row_")
        rows.size == 1 && tree.tagOfNodeContaining(patient.medicalRecordNumber, "patient_row_") == rows.single()
    }
    val patientRowTag = checkNotNull(getHierarchy().tagOfNodeContaining(patient.medicalRecordNumber, "patient_row_"))
    // A click that lands while the list is still recomposing can be swallowed; retry it rather
    // than failing on the first miss, and dump what's on screen if it never navigates.
    repeat(3) { attempt ->
        if (getHierarchy().containsTag(patientRowTag)) clickTag(patientRowTag)
        try {
            waitForTag("record_edit_button", timeoutMs = 5_000)
            return
        } catch (e: BridgeTimeoutException) {
            if (attempt == 2) {
                throw AssertionError(
                    "record_edit_button never appeared after clicking $patientRowTag; on screen: " +
                        getHierarchy().allTexts(),
                    e,
                )
            }
        }
    }
}

/** Every testTag in this tree starting with [prefix], depth-first. */
fun HierarchyNode.tagsWithPrefix(prefix: String): List<String> = buildList {
    testTag?.takeIf { it.startsWith(prefix) }?.let(::add)
    children.forEach { addAll(it.tagsWithPrefix(prefix)) }
}

/**
 * Admits the patient whose record screen is currently open into a new hospitalization via the
 * real Admision form, landing on that hospitalization's own screen (`hosp_discharge_button`
 * visible). Mirrors the admission steps `HissE2EScenarios`'s full-lifecycle test already drove
 * inline; factored out here since other patient-record scenarios now also need a real
 * (non-seeded) Activa hospitalization to open, now that hospitalizations are only ever findable
 * under whichever patient id the real backend actually assigned.
 */
fun BridgeDriver.admitPatient(motivo: String = "E2E: sintomas respiratorios", cama: String = "12") {
    clickTag("record_new_hospitalization_button")
    waitForTag("admision_submit_button")
    selectOption("admision_servicio_field", 0)
    type("admision_cama_field", cama)
    selectOption("admision_medico_field", 0)
    type("admision_motivo_field", motivo)
    clickTag("admision_submit_button")
    waitForTag("hosp_discharge_button")
}

/**
 * Drives a [SelectField][com.cramsan.hirsh.ui.components.SelectField] tagged [fieldTag]:
 * opens the dropdown, then clicks the option at [optionIndex] (tagged
 * `"${fieldTag}_option_$optionIndex"` by that component -- see FormFields.kt).
 */
fun BridgeDriver.selectOption(fieldTag: String, optionIndex: Int) {
    clickTag(fieldTag)
    clickTag("${fieldTag}_option_$optionIndex")
}

/** Polls [getHierarchy] until [predicate] is satisfied or [timeoutMs] elapses, for state that isn't a single tag appearing (e.g. a route change with no new distinguishing tag yet). */
fun BridgeDriver.waitUntil(timeoutMs: Long = 10_000, pollMs: Long = 200, predicate: (HierarchyNode) -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate(getHierarchy())) return
        Thread.sleep(pollMs)
    }
    check(predicate(getHierarchy())) { "Timed out after ${timeoutMs}ms waiting for hierarchy predicate" }
}
