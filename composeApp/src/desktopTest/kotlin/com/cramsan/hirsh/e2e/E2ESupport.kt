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
