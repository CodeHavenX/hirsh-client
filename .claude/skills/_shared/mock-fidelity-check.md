# Mock Fidelity Check (report-only)

Compare a rendered Compose preview against its source-of-truth HTML mock under
`prototype/`. This is a **design-intent comparison, not a pixel diff** —
Roborazzi's Compose Desktop rendering and a real browser's HTML/CSS rendering
will never match pixel-for-pixel even when the implementation is correct
(different fonts, layout engines, anti-aliasing). No headless-browser screenshot
tool exists in this repo for the `prototype/` side, so judge fidelity by reading
the mock's markup and resolved CSS tokens, and viewing the rendered PNG as an
image.

**This fragment is report-only.** If differences are found and the goal is to
actually fix them (not just describe them) — inside `implement-ticket`'s UI step,
or whenever asked to "match the mock" / "fix fidelity" — use the
`match-mock-fidelity` skill instead. It resolves tokens in more depth, prefers
reusing existing theme tokens over inventing new ones, and iterates fix →
re-render → re-compare until clean. Don't hand-patch styling from this fragment
alone.

## Step 1 — Locate the mock

The screen name usually maps directly to a prototype file:

| Screen | Mock |
|---|---|
| Login | `prototype/index.html` |
| Patient list | `prototype/patients.html` |
| Patient record | `prototype/record.html` |
| Register patient | `prototype/register.html` |
| Edit patient | `prototype/patient-edit.html` |
| Patient change history | `prototype/patient-history.html` |
| Admisión | `prototype/admision.html` |
| Hospitalización detail | `prototype/hospitalizacion.html` |
| Historia Clínica editor | `prototype/historia-clinica.html` |
| Nueva evolución | `prototype/evolucion.html` |
| Evolución (read-only) | `prototype/evolucion-view.html` |
| Print preview | `prototype/print.html` |
| Accounts (admin) | `prototype/accounts.html` |
| Profile | `prototype/profile.html` |

If no mock file exists for a screen being checked (shouldn't happen — every
planned screen traces to one of the 13 files above per the build plan), stop and
say so explicitly rather than inventing a comparison against nothing.

## Step 2 — Render the preview

```bash
./gradlew :composeApp:recordRoborazziDesktop --tests "*<ScreenName>*"
```

Goldens land under `screenshots/` (Git-LFS tracked, committed — not `build/`).
Every `@Preview` composable in the matching `<Screen>Previews.kt` gets its own
generated test (`generateComposePreviewDesktopTests` scans the `ui.screens`
package, including `private` previews — see `composeApp/build.gradle.kts`).

## Step 3 — Match each preview to a mock state

A single mock HTML file often only shows one canonical state (the prototype uses
static seed data, not a state machine — there's no "Loading" or "Empty" markup to
match against the way a live app would need). Match what the `<Screen>Previews.kt`
file actually renders (e.g. a specific seeded patient, an empty hospitalizations
list) to the equivalent data in `prototype/shared/data.js`, not to a generic mock
state. If a preview renders a case the mock's static HTML can't show at all (e.g.
a loading spinner), note that plainly — it isn't a fidelity gap, there's nothing
to compare it against.

## Step 4 — Compare

Read the rendered PNG with the Read tool (visual inspection), and read the mock's
HTML plus `prototype/shared/styles.css` for the resolved token values it uses
(e.g. resolve `var(--accent)` → `#2f7d8a`). Compare against the app's actual
theme:

- **Layout structure** — same grouping/order, same shell (`renderAppShell` sidebar
  layout vs. `renderEncounterBar` full-screen encounter layout — see `AppScaffold`
  vs. the encounter routes), same nav placement/active-item highlighting.
- **Spacing** — matches the mock's spacing; a large deviation (double/half the
  expected gap) is a finding, sub-pixel/dp rounding is not.
- **Color** — matches the *resolved* token value, cross-referenced against
  `ui/theme/Color.kt` / `Theme.kt`'s actual `lightColorScheme`/`darkColorScheme`
  mapping — not an arbitrary Material3 default standing in for it.
- **Typography** — matches the mock's scale/weight tier. Note going in:
  `Theme.kt`'s `MaterialTheme(...)` call does not currently set a custom
  `typography`, so it's on Material3's default type scale/font, not the
  prototype's Inter (`--font` in `styles.css`) — this is a known, real gap as of
  this writing, not a false positive; call it out per-screen rather than assuming
  someone already fixed it globally.
- **Component presence** — every element the mock's markup shows for that state
  (badges, empty-state text, audit lines, chips) is present in the render; nothing
  appears that isn't in the mock.
- **Content** — the specific labels/values the mock shows for that patient/record
  are the values rendered (matching `prototype/shared/data.js`'s seed data, not
  placeholder text).

Don't stop at eyeballing the PNG — check the Compose source directly for *why* it
renders the way it does: does a `Card`/`Surface` set an explicit `shape`/`border`/
`colors`, or fall back to Material3 defaults (which won't match the mock's
`--radius: 10px` cards)? Does a status label (e.g. the allergy badge, the
hospitalización estado badge) have a real background/shape, or is it plain
colored text standing in for a pill?

## Step 5 — Report

```
[<Screen>Previews] <preview function name>
Mock shows   : <what prototype/<file>.html renders for this case>
Render shows : <what the implementation actually renders>
Fix          : <specific change — Screen.kt structure, a missing theme token, missing content, etc.>
```

If there are no differences, say so explicitly rather than omitting the section.
