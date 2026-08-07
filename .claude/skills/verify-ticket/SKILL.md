---
name: verify-ticket
description: "Verify an implemented HISS ticket is actually correct before code-quality review: confirm green build/tests, check every Scope/Added-on-review bullet from the Plane ticket against real evidence, run the mock-fidelity report, and move the ticket to QA / Code Review only if everything checks out. Use after implement-ticket and before review/create-pr, or whenever asked to verify a change is ready for review."
allowed-tools: Read, Write, Glob, Grep, Bash, mcp__plane__retrieve_work_item, mcp__plane__update_work_item
---

# verify-ticket — Correctness Gate Before Code Review

## Purpose

`review` audits code **quality** (style, architecture, convention adherence). It
does not confirm the change actually **does what the ticket says**. This skill is
that gate: green build/tests, every Scope / "Added on review" bullet from the
ticket's Plane description backed by named evidence, and the mock-fidelity report
run — nothing taken on faith, including `implement-ticket`'s own handoff notes;
re-check independently.

---

## Required Information

Ask for the `HISS-NNN` ticket id if not provided. Look up its work item id in
`.claude/skills/_shared/plane-project.md`.

---

## Step 1 — Load the ticket and the real diff

```
mcp__plane__retrieve_work_item
  project_id: 69616f0e-7541-46e7-94dd-3a261ab0bd05
  work_item_id: <this ticket>
```

Read the full description — every Scope bullet and every "Added on review"
bullet is a claim this skill must resolve to PASS/gap, the same way an
Acceptance Criteria checklist would.

Get the actual changed files, not the plan's intent:

```bash
git diff main...HEAD --name-only --diff-filter=d
```

(If working directly on `main` per `implement-ticket`'s small-ticket exception,
diff the specific commit(s) instead: `git show --name-only --diff-filter=d
<sha>`.)

---

## Step 2 — Full verification gate

Run the root project's CI-tier aggregate task rather than the individual pieces
by hand — it's the single source of truth for "does this pass," covers every
platform's `commonMain` compile (not just desktop), and regenerates +
diff-checks Roborazzi screenshots as part of the same gate (see root
`build.gradle.kts`'s `verifyCi`/`verifyLocal` task definitions):

```bash
./gradlew verifyCi --quiet
```

This subsumes `compileKotlinDesktop`, `compileKotlinMetadata`,
`:composeApp:desktopTest`, `:composeApp:detekt`, `:detekt-rules:test`, and a
clear+record+diff pass over `composeApp/screenshots/` (`checkScreenshotsClean`)
— run it in full even for a diff with no `*Screen.kt`/`*Previews.kt` changes,
so a screenshot drifted by something else entirely doesn't slip through.

A **red build, detekt failure, test failure, or screenshot drift stops the gate
here.** Report the failure (paste the actual Gradle output for whichever task
failed) and do not proceed to Step 3 claiming partial success.

---

## Step 3 — Walk the ticket's Scope / Added-on-review bullets with evidence

For every bullet, resolve to exactly one of:

- **PASS** — name the file/line or test that satisfies it.
  ```bash
  ./gradlew :composeApp:desktopTest --quiet --tests "com.cramsan.hirsh.ui.screens.<feature>.<Feature>ViewModelTest"
  ```
- **NEEDS MANUAL VERIFICATION** — nothing in this skill's toolkit covers it (a
  multi-platform behavior only observable by actually running the app — see the
  `run` skill for that). Do not mark PASS on an assumption.
- **NOT APPLICABLE** — the bullet describes a decision/rule for *other* tickets
  to follow (several HISS tickets' "Added on review" bullets are cross-cutting
  rules, e.g. HISS-108's session-flows-as-explicit-parameter rule, that this
  specific ticket only needs to satisfy if it actually touches session — say so
  if it doesn't apply here).

A bullet with no matching evidence and no manual-verification note is a gap —
call it out, don't drop it silently.

---

## Step 4 — Mock fidelity (report-only)

For any diff touching a `*Screen.kt`, `*Previews.kt`, or a shared component
under `ui/components/`: read `.claude/skills/_shared/mock-fidelity-check.md` and
run it independently — do not accept `implement-ticket`'s own fidelity pass as
evidence if that skill was already run; re-render and re-compare here. Report
findings the same way that fragment specifies. If differences are found, this
skill does **not** fix them — mark the relevant bullet unmet, note that
`match-mock-fidelity` is the fix path, and let the user decide whether to run it
before re-verifying.

`verifyCi` (Step 2) already clears, regenerates, and diff-checks
`composeApp/screenshots/` as part of the gate, so a stray drift outside the
screen(s) this ticket touches would already have failed the build there —
if it passed, screenshots are clean. This step is about *visual correctness*
against the mock, which a clean diff-check can't tell you: a screen can render
without drifting from its own prior golden while still not matching
`prototype/*.html`.

---

## Step 5 — Handoff-notes cross-check

If `implement-ticket` reported specific claims in its own handoff (files
touched, tests added, scope addressed), spot-check at least the test list
against what's actually in the diff (`git diff --name-only` for new
`*Test.kt` files) — this skill exists specifically because that handoff
shouldn't be taken at face value.

---

## Step 6 — Produce the Verification Report

```markdown
# Verification Report — HISS-NNN <title>

## Build & Tests
- `./gradlew verifyCi`: PASS | FAIL (paste failure output for whichever
  sub-task failed — compile, detekt, desktopTest, or checkScreenshotsClean)

## Scope / Added-on-review bullets
- [x] <bullet> — verified by `<TestClass>.<testMethod>` or `<file>:<line>`
- [ ] <bullet> — NEEDS MANUAL VERIFICATION: <what to check and how>
- [~] <bullet> — NOT APPLICABLE (cross-cutting rule this ticket doesn't touch)

## Mock Fidelity
<pass-through from Step 4, or "No screen/preview changes in this diff.">

## Manual Verification Needed
- <item> — repro steps: <...>

## Verdict
READY FOR REVIEW | NOT READY — <reason>
```

**Verdict rule:** never mark READY FOR REVIEW if the build is red, or if any
non-NA bullet is unresolved without an explicit manual-verification note the
user has had a chance to act on.

---

## Step 7 — Move the ticket, handoff

If READY FOR REVIEW:

```
mcp__plane__update_work_item
  project_id: 69616f0e-7541-46e7-94dd-3a261ab0bd05
  work_item_id: <this ticket>
  state: 452c5584-3af6-40e1-a430-9f303ebd3094   # QA / Code Review
```

Tell the user to proceed to `review`, then `create-pr` (or straight to a direct
commit's already-pushed state if this was a small ticket on `main`).

If NOT READY, do not move the Plane state — give a short, ordered punch list of
exactly what has to change before re-running `verify-ticket`.
