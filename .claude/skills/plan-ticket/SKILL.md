---
name: plan-ticket
description: "Plan implementation for a HISS Plane ticket (HISS-NNN): fetch it from Plane, read the matching prototype HTML mock and README conventions, find the closest existing reference implementation in composeApp, and produce a concrete technical implementation plan grounded in real file paths — not a restatement of the ticket. Use when starting work on a new HISS ticket, or asked to plan one."
allowed-tools: Read, Write, Glob, Grep, Bash, mcp__plane__retrieve_work_item, mcp__plane__update_work_item, mcp__plane__search_work_items, mcp__plane__list_work_items
---

# plan-ticket — HISS Ticket Implementation Planning

## Purpose

The 28 HISS tickets in Plane already carry rich scope, rationale, and dependency
information (they went through three review passes before creation) — this skill
does **not** re-derive that. Its job is the gap between "what the ticket says" and
"how to actually build it in this codebase": which files to touch, which existing
screen/repository to pattern-match against, and what's genuinely still open. If a
ticket's Plane description already answers something, don't restate it in the
plan — reference it and move on.

## Required Information

Ask the user for the ticket id (`HISS-NNN`) if not provided. If they instead ask
"what's next," read `.claude/skills/_shared/plane-project.md`'s note on
`sort_order` and use `mcp__plane__list_work_items` (project id from that file)
sorted by `sort_order` to find the lowest-numbered ticket not yet Done.

---

## Step 1 — Fetch the ticket

Look up the work item id in `.claude/skills/_shared/plane-project.md`'s table,
then:

```
mcp__plane__retrieve_work_item
  project_id: 69616f0e-7541-46e7-94dd-3a261ab0bd05
  work_item_id: <from the table>
```

Record: current state, the full description (Scope / Added on review / Depends
on / Files sections), and which phase module it's in.

If `Depends on` lists any ticket, check its state too — if a dependency isn't
Done, stop and say so; don't plan on top of a foundation that doesn't exist yet
(the one exception: read-only exploration/planning is fine, just flag it clearly
in the plan's Dependencies section rather than silently assuming it'll be there).

---

## Step 2 — Read the repo's own conventions

Always read, in full:

- `README.md` — module layout, stack, detekt setup, Roborazzi wiring, current
  state of `AuthRepository`/`PatientRepository`.
- `composeApp/src/commonMain/kotlin/com/cramsan/hirsh/ui/navigation/Route.kt` —
  note the comment on why routes are plain strings, not `@Serializable` (a
  wasmJs/K2 compiler crash), so a plan never proposes type-safe routes.

---

## Step 3 — Read the matching prototype mock

Almost every ticket traces to one or more files under `prototype/` (see the
table in `_shared/mock-fidelity-check.md`, or the ticket's own description if it
names a source file). Read the mock's HTML directly — it is the actual spec for
what the screen/flow does, not just what it looks like. Also read
`prototype/shared/data.js` for any data shape the ticket touches (field names,
enum-like string sets, the seed fixtures) and `prototype/shared/components.js`
for any shared rendering logic (`renderDataTable`, `renderKV`, badges) the ticket
references.

For a non-screen ticket (a repository, the domain model, session state), there
may be no single mock — read whichever prototype JS functions the ticket's
description names instead (e.g. `logPatientChange`, `getSelectedHospitalization`).

---

## Step 4 — Find the reference implementation

Identify the closest existing pattern in `composeApp/` and read it in full,
matching the ticket's actual layer:

**A new Screen/ViewModel/Previews** (any `HISS-2xx`/`3xx`/`401` ticket):
Read the most structurally similar screen that already exists —
`ui/screens/patientrecord/` for a record/detail-style screen,
`ui/screens/patientlist/` for a list/table screen, `ui/screens/login/` for a form.
Read all three files (`*Screen.kt`, `*ViewModel.kt`, `*Previews.kt`) plus the
matching `*ViewModelTest.kt` under `commonTest/`. Note the exact convention:
`ViewModel` is a plain `androidx.lifecycle.ViewModel` holding a
`MutableStateFlow<XUiState>`/`StateFlow<XUiState>` pair, loaded via
`viewModelScope.launch` in `init` or an explicit `load(...)`; `Screen` takes the
ViewModel via `koinViewModel()` default parameter; `Previews` is a private
`object Preview<Repo>` implementing the repo interface directly (no mocking
library) plus one or more private `@Preview @Composable` functions wrapped in
`HirshTheme { ... }` (see `LoginPreviews.kt`).

**A repository or domain model** (any `HISS-1xx` ticket): read `model/Patient.kt`
and `repository/PatientRepository.kt` — the established convention for a
Kotlin-first domain model that mirrors `prototype/shared/data.js` field names
(see that file's own doc comment), plus an `interface` + in-memory implementation
seeded from the prototype's fixtures.

**Navigation** (HISS-106 and anything after it lands): read
`ui/navigation/AppNavHost.kt` and `Route.kt` for the existing `composable(...)` /
`AppScaffold` wiring pattern.

**DI registration**: read `di/AppModule.kt` — note the `singleOf(::X) bind
Y::class` / `viewModelOf(::XViewModel)` pattern; any new repository or ViewModel
needs an entry here.

---

## Step 5 — Identify open questions and risks

- Anything the ticket's `Depends on` section needs that isn't actually available
  yet on `main` (check with `git log`/`Read`, don't assume the dependency ticket's
  Plane state is accurate — verify the code exists).
- Anything the mock shows that the ticket's Plane description doesn't mention —
  flag it, don't silently expand scope without calling it out.
- Anything genuinely ambiguous that needs a human decision before coding starts.

---

## Step 6 — Write the plan

Produce a plan grounded in real paths — omit any section that doesn't apply:

```markdown
# HISS-NNN — <title>

**Plane:** <work item id> · **Phase module:** <Fase N>
**Depends on:** <HISS ids, with their current state>

## What Plane already specifies

<One or two sentences pointing back to the ticket's Scope/Added-on-review
content — do not restate it in full, the ticket is the source of truth for
"what" and "why". This section exists so the plan is legible standalone, not to
duplicate Plane.>

## Reference implementation

<Exact file(s) read in Step 4, and what to copy vs. what's different for this
ticket.>

## Files to add/change

- `path/to/File.kt` — <new | edit: what changes>
...

## Mock cross-check

<What in prototype/<file>.html and shared/data.js this must match. For a
Screen ticket: which seeded patient/record to use in the Preview so it's
directly comparable to the mock's own static content.>

## Domain model / DI notes

<Any new Koin registration needed. Any model field mapping decisions not
already pinned down by the ticket (e.g. an enum vs. String choice left open).>

## Test plan

<Which ViewModelTest cases to write, following the existing pattern in
commonTest/. For a pure model ticket: whether any test is needed at all, or a
successful build is the only signal (matches HISS-101's precedent).>

## Open questions

<Anything needing a human call before coding — or "None.">
```

---

## Step 7 — Move the ticket and hand off

```
mcp__plane__update_work_item
  project_id: 69616f0e-7541-46e7-94dd-3a261ab0bd05
  work_item_id: <this ticket>
  state: 52793115-adc8-4856-962b-c0478ffd9956   # Todo
```

Only move it if it's currently in Backlog — don't move a ticket backward if it's
already further along (e.g. re-planning something mid-implementation).

Present the plan to the user. Do not start writing code from this skill — hand
off to `implement-ticket` once the plan is confirmed (or immediately, if the user
says to just proceed).
