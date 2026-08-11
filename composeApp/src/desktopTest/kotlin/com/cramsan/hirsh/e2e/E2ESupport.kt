package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.HierarchyNode
import com.cramsan.cmpbridge.driver.BridgeDriver

/**
 * Small helpers on top of [BridgeDriver]/[HierarchyNode] shared by every scenario in
 * [HissE2EScenarios]. cmp-bridge's `click`/`setText` are tag-only (no coordinate or
 * text-based fallback -- see .claude/skills/run-desktop/SKILL.md), so every interaction
 * below goes through a `testTag`; `getHierarchy()`'s tree is used to read rendered text
 * back for assertions regardless of whether a node has a tag.
 */

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
