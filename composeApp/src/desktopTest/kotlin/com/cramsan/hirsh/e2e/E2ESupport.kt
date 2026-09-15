package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.HierarchyNode
import com.cramsan.cmpbridge.driver.BridgeDriver
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
 * Logs in as the seeded ADMIN account (`admin`/`whatever123`) and waits for `nav_accounts`,
 * confirming the ADMIN-only nav item rendered. Every scenario below calls this (or
 * [loginAsDoctor]) itself as its first step rather than relying on an earlier test's login --
 * see [ensureSignedOut]'s doc comment for why that's a no-op on desktop but not on web.
 */
fun BridgeDriver.loginAsAdmin() {
    ensureSignedOut()
    type("login_username_field", "admin")
    type("login_password_field", "whatever123")
    clickTag("login_submit_button")
    waitForTag("nav_accounts")
}

/**
 * Logs in as the seeded DOCTOR account (`apatel`/`whatever123`) and waits for `nav_patients`.
 * Used only by the login scenario that specifically asserts on the DOCTOR role's nav --
 * everything else uses [loginAsAdmin] so accounts-management tags stay reachable too.
 */
fun BridgeDriver.loginAsDoctor() {
    ensureSignedOut()
    type("login_username_field", "apatel")
    type("login_password_field", "whatever123")
    clickTag("login_submit_button")
    waitForTag("nav_patients")
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
