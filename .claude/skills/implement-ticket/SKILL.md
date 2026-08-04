---
name: implement-ticket
description: "Drive an already-planned HISS ticket to a complete, tested implementation on a feature branch, with incremental commits. Use when starting implementation work on a HISS ticket that plan-ticket has already produced a plan for (or immediately after planning it, if the user says to just proceed)."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, mcp__plane__retrieve_work_item, mcp__plane__update_work_item
---

# implement-ticket — Drive a HISS Plan to Implementation

## Purpose

Turn a `plan-ticket` plan into working, compiling, tested code on a feature
branch — small commits, following the exact conventions already established in
`composeApp/`. This is the link between `plan-ticket` and `verify-ticket` /
`review` / `create-pr`.

## Required Information

Ask for the `HISS-NNN` ticket id and its plan (from `plan-ticket`'s output, or
inline in the conversation) if not already established.

---

## Step 0 — Confirm scope before touching git

If the plan hasn't been read back and confirmed earlier in the conversation, do
that now — one line each on: files to add/change, reference implementation, test
plan. Get an explicit go-ahead. Don't assume approval silently, and don't start
implementing an unplanned ticket without at least the Step 1–4 research from
`plan-ticket` having happened somewhere in this conversation.

---

## Step 1 — Move the ticket, create the branch

```
mcp__plane__update_work_item
  project_id: 69616f0e-7541-46e7-94dd-3a261ab0bd05
  work_item_id: <this ticket, from _shared/plane-project.md>
  state: 725d0ade-69d7-4ef0-861a-060b76a83298   # In Progress
```

```bash
git status               # must be clean — if not, stop and ask how to proceed
git checkout main && git pull
git checkout -b hiss-<nnn>-<short-slug>
```

`<short-slug>` is a 2–4 word kebab-case summary of the ticket title (e.g.
`hiss-321-hc-editor-rail`). Never branch from a dirty tree, and never discard
uncommitted work to get there — ask first.

**Small tickets (XS/S) may skip the branch** if the user prefers committing
straight to `main`, matching how HISS-101/105 actually shipped in this repo's
early history — ask if unsure. `create-pr` only applies to the branch workflow;
skip it too if working directly on `main`.

---

## Step 2 — Implementation order

Skip any layer the plan doesn't touch. Commit at the end of each completed,
green layer (Step 4) — never accumulate a red layer under a later one.

1. **Domain model** (`model/*.kt`) — pure data classes/enums, no dependencies on
   anything else in the app. Mirror `prototype/shared/data.js` field names per
   `model/Patient.kt`'s established convention (see its doc comment).
2. **Repository** (`repository/*.kt`) — `interface` + implementation, seeded from
   `prototype/shared/data.js` fixtures where applicable. If this repository (or
   one it touches) is supposed to expose `Flow` per HISS-112, don't build a new
   one-shot `suspend fun` repository even if HISS-112 itself isn't done yet —
   check its current state first (Step 1 dependency check in `plan-ticket`)
   and raise it rather than silently building on the old shape.
3. **DI wiring** (`di/AppModule.kt`) — `singleOf(::X) bind Y::class` for a new
   repository, `viewModelOf(::XViewModel)` for a new ViewModel.
4. **Navigation** (`ui/navigation/Routes.kt` / `AppNavHost.kt`) — new route
   constant(s), `composable(...)` registration, and — per HISS-106's own
   ticket — the right choice between wrapping in `AppScaffold` (sidebar visible)
   vs. a bare full-screen "encounter" composable (no sidebar), matching whether
   the equivalent prototype page uses `renderAppShell()` or
   `renderEncounterBar()`.
5. **Screen / ViewModel / Previews** — the three-file convention enforced by the
   `ScreenMissingViewModel`/`ScreenMissingPreviews` detekt rules
   (`detekt-rules/`): `<Feature>Screen.kt`, `<Feature>ViewModel.kt`,
   `<Feature>Previews.kt` all in the same directory. Follow the reference
   implementation identified in `plan-ticket` Step 4 for the exact shape
   (ViewModel: plain `androidx.lifecycle.ViewModel` + `MutableStateFlow` UI
   state; Previews: a private in-file fake repository object + `@Preview
   @Composable` function(s) wrapped in `HirshTheme { ... }`).

   **Once the Screen and Previews compile, run the `match-mock-fidelity` skill**
   against this screen before committing this layer — don't commit a screen
   known to diverge from its `prototype/*.html` mock, and don't settle for a
   shallow "same text content" check (see that skill's own rationale).

After each layer:

```bash
./gradlew :composeApp:compileKotlinDesktop --quiet
./gradlew :composeApp:detekt --quiet
```

Fix failures before moving to the next layer.

---

## Step 3 — Tests

Write the matching test immediately after each Screen/ViewModel/repository
layer, not deferred to the end:

- **ViewModel**: `<Feature>ViewModelTest.kt` under `commonTest/`, following
  `PatientListViewModelTest.kt` / `LoginViewModelTest.kt`'s pattern — a private
  `Fake<Repo>` implementing the repo interface, `StandardTestDispatcher` +
  `Dispatchers.setMain`/`resetMain`, Turbine's `uiState.test { ... }` to assert
  the emission sequence.
- **Screen**: none — per `ScreenMissingViewModel`'s own convention, visual
  correctness is verified via `Previews.kt` + Roborazzi, not a unit test on the
  Screen composable itself.
- **Repository / domain model**: a test is only needed if there's real logic to
  exercise (a mapping function, a derived value). A pure data-class-only ticket
  (like HISS-101) has nothing to unit test — a green compile is the signal, note
  that explicitly rather than writing a placeholder test for its own sake.

Run the full suite before moving on:

```bash
./gradlew :composeApp:desktopTest --quiet
```

---

## Step 4 — Commit discipline

One commit per completed, green layer (small adjacent layers — e.g. domain model
+ DI registration — may be grouped if trivially small). Never squash everything
into one end-of-task commit. Match this repo's existing style (see `git log`):
imperative-mood subject line, no ticket-tag prefix, a body paragraph explaining
the *why* when it's non-obvious — plus, going forward, name the ticket in the
body for traceability:

```
<Imperative summary>

<Why, if non-obvious — reference prior art/conventions being followed.>

Implements HISS-NNN.
```

Never push from this skill — that's `create-pr`'s job (or the user's, for a
direct-to-`main` small ticket). Never amend a commit once a later layer has been
built on top of it.

---

## Step 5 — Scope pass against the ticket

Re-read the Plane ticket's Scope / Added-on-review bullets (from `plan-ticket`
Step 1) and confirm each is actually addressed by the diff — name the file/line
that satisfies it. Anything not addressed: say so explicitly now rather than
letting it surface later in `verify-ticket`.

---

## Step 6 — Handoff

Summarize for the user:

- Branch name (or "committed directly to `main`") and commit list:
  `git log main..HEAD --oneline`
- Files touched, grouped by layer
- Tests added, and their current pass/fail state
- Scope items: addressed / not addressed / not applicable to this ticket

Point them at **`verify-ticket`** next (confirm build/tests are actually green
and the mock-fidelity check is clean), then `review`, then `create-pr` if on a
branch. Do not run those yourself from this skill.
