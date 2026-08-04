---
name: review
description: "Run a full code-quality review against a diff, module, or PR: convention adherence (Screen/ViewModel/Previews shape, DI registration, theme tokens), and the architectural rules this project has explicitly settled on (session-explicit-parameters, no-default-entity-fallback, Flow-based repositories, real required-field validation). Use when asked to review code quality, check for violations, or audit a change before create-pr."
allowed-tools: Read, Glob, Grep, Bash
---

# review — Code Quality Review

## Purpose

Audit a scope of Kotlin files against this project's actual conventions and
produce one prioritized findings report. This is a quality/architecture pass —
it does not confirm the change *does what the ticket says* (`verify-ticket`
does that; run it first).

## Step 1 — Resolve scope

| Scope requested | Command |
|---|---|
| `changes` (default) | `git diff main...HEAD --name-only --diff-filter=d` + `git diff --name-only` — union of both |
| `module <path>` | `find <path> -name "*.kt" -type f` |
| `ticket HISS-NNN` | same as `changes`, but on that ticket's branch (`hiss-<nnn>-*`) |

Filter to `.kt` files only. If no scope was given, default to `changes`. If the
resolved list is empty, report that and stop.

Read every file in scope fully before analysing.

## Step 2 — Apply rules

### Generic (every file)

**G1 — No detekt baseline suppression** (P0)
Never add a violation to `config/detekt/baseline.xml`. If a violation can't be
fixed, stop and ask — don't grandfather new code in.
Remediation: fix the violation directly; only regenerate the baseline
(`./gradlew :composeApp:detektBaseline`) for pre-existing code a rule newly
applies to, never for code written in this change.

**G2 — No explanatory comments** (P2)
A comment should explain a non-obvious *why* (a hidden constraint, a workaround,
a deliberate deviation — see `Route.kt`'s comment on why routes are plain
strings, or `Evolucion.kt`'s note on renaming `evolucion` → `resultado`), never
restate *what* the code already says through naming.
Remediation: delete comments that just narrate the next line; keep or add ones
that explain a decision a reader couldn't infer from the code alone.

### Screen / ViewModel / Previews convention

**C1 — ViewModel shape** (P1)
A `<Feature>ViewModel` exposes `val uiState: StateFlow<XUiState>` backed by a
`private val _uiState: MutableStateFlow<XUiState>` — never expose the mutable
flow itself, and never let the Screen hold state the ViewModel should own.
Remediation: add the private/public flow pair per `PatientListViewModel.kt`'s
pattern; move any state hoisted directly in the Screen composable into the
ViewModel.

**C2 — Previews shape** (P1)
`<Feature>Previews.kt` supplies a private fake object implementing the real
repository interface directly (no mocking library — see `LoginPreviews.kt`'s
`PreviewAuthRepository`) plus at least one `private @Preview @Composable`
function wrapped in `HirshTheme { ... }`. A preview constructed against the real
`InMemoryPatientRepository`/production repo (rather than a small in-file fake)
is a violation — it couples the preview to production seed data drifting.
Remediation: add a private fake repository object scoped to the preview file.

**C3 — DI registration completeness** (P0)
Every new repository interface+impl gets `singleOf(::Impl) bind Interface::class`
in `di/AppModule.kt`; every new ViewModel gets `viewModelOf(::XViewModel)`.
Remediation: add the missing Koin registration.

### This project's settled architectural rules

These came out of the HISS-108/HISS-106/HISS-112/HISS-104/HISS-303/HISS-109
tickets' own "Added on review" sections — treat them as binding, not
aspirational, for any code that touches session, repositories, or navigation.

**C4 — Session flows in as an explicit parameter** (P0)
A repository must never reach into session/auth state itself. Any
session-derived value it needs (an acting user's id for an audit field, an
author's display name) is a parameter supplied by the calling ViewModel.
Remediation: add the parameter to the repository method signature; have the
ViewModel read the session and pass it in.

**C5 — No default-entity fallback** (P0)
A lookup keyed by a child id under a parent (patient→hospitalización,
hospitalización→evolución) must validate **both** ids, not just find the child
by its own id and ignore which parent it's under — a hospitalización belonging
to a different real patient than the one requested must resolve to not-found,
never render. A missing/invalid id must produce a not-found state, never a
silent fallback to a default or first item.
Remediation: change the lookup to filter/match on both ids together; add or
route to a not-found state for anything that doesn't resolve.

**C6 — Repositories expose `Flow`, not one-shot fetches** (P1, once HISS-112
has landed — check its Plane state before flagging pre-existing code written
before it merged)
A repository's read methods return `Flow<T>`/`Flow<List<T>>`, not a one-shot
`suspend fun`, so screens retained on the back stack see mutations made
elsewhere without a manual refresh.
Remediation: convert the method to expose the backing `MutableStateFlow`;
update the ViewModel to `collectAsState()`/collect it directly instead of an
imperative `load()`.

**C7 — Required-field validation actually blocks save** (P1)
A form ViewModel's save/submit action must reject the call (surface an error,
not proceed) when a field the ticket marks required is blank — a UI-only
asterisk with no backing check is a violation, even if it matches the
prototype (the prototype itself doesn't enforce its own required markers; this
project deliberately does not inherit that gap — see HISS-203's ticket).
Remediation: add the validation check in the ViewModel before the repository
call, surfaced via the UI state's error field.

**C8 — Theme tokens for visual constants** (P1)
Colors, corner radii, and spacing come from `ui/theme/` (`Color.kt`, and
whatever shape/spacing tokens exist there) — a hardcoded `Color(0x...)` or a
one-off `.dp` corner radius in a `Screen.kt` is a violation unless it's a
genuine one-off structural layout detail with no design-token equivalent.
Remediation: replace with the existing token; if none exists yet for a value
the mock clearly specifies, add one to `Color.kt` following the `Hiss*` naming
convention (see `match-mock-fidelity`'s Step 5 for the process) rather than
leaving a bare literal in the composable.

**C9 — Plain string routes only** (P0)
Never reintroduce `@Serializable` type-safe navigation routes — `Route.kt`
documents an active wasmJs/K2 compiler crash this avoids.
Remediation: use a plain string constant on `Routes` instead.

### Build Tools

Run, and include the raw output verbatim in the report:

```bash
./gradlew :composeApp:compileKotlinDesktop --quiet
./gradlew :composeApp:detekt --quiet
```

## Step 3 — Produce output

List findings sorted by priority (P0 first), grouped by file within a priority:

```
[P<N>] <RuleID> — <File>:<line-or-section>
Problem     : <one sentence describing the violation>
Remediation : <one or two sentences on how to fix it>
```

End with:

```
Summary
-------
P0 (Critical): N findings
P1 (High)    : N findings
P2 (Medium)  : N findings
Total        : N findings

Build Tools Report
<compileKotlinDesktop / detekt output, or "clean" if no output>
```

If no violations are found, say so explicitly rather than omitting the section.

## Next step

- **Any P0 finding** — tell the user to fix it and re-run `review`. Do not
  suggest `create-pr` yet.
- **Only P1/P2, or none at all** — tell the user they can proceed to
  `create-pr`.
