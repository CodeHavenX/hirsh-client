---
name: match-mock-fidelity
description: "Iteratively fix a Compose screen until it matches its prototype/*.html mock: resolve the mock's actual CSS token values from styles.css, compare against the Compose source (not just the rendered pixels), reuse existing HirshTheme tokens/patterns before inventing new ones, apply fixes, then re-render and re-compare until clean. Use when a screen has already been built and its style doesn't match the mock, when asked to 'match the mock' or 'fix mock fidelity', or as the fix step within implement-ticket's Screen/ViewModel/Previews layer."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
---

# match-mock-fidelity — Iterative Screen-to-Mock Alignment

## Purpose

Drive an already-implemented Compose screen to zero visual-fidelity differences
against its `prototype/*.html` mock, by repeatedly: resolving the mock's real
`styles.css` token values, comparing them against what the Compose source
actually does (not just what the rendered PNG looks like), fixing by reusing
`ui/theme/`'s existing tokens/patterns wherever they already exist, and
re-rendering until a full pass finds nothing left to fix.

This is different from `_shared/mock-fidelity-check.md`, which is a
**report-only** comparison used inside `verify-ticket` (which must never
silently fix things). This skill is the **fix loop** — use it whenever the goal
is to actually close the gap.

### Why this exists

The prototype's design tokens are only partially ported into the Compose theme
today. `ui/theme/Color.kt` has `HissPaper`/`HissInk`/`HissFaint`/`HissAccent`(+
wash)/`HissWarn`(+wash)/`HissSuccess`(+wash) — but `prototype/shared/styles.css`
also defines `--ink2: #75726b` (muted/secondary text — used everywhere for
metadata, captions, counts) and `--radius: 10px` (every card/button/input's
corner radius), neither of which has a Kotlin token yet, and `Theme.kt`'s
`MaterialTheme(...)` call never sets a custom `typography`, so text renders in
Material3's default type scale/font instead of the mock's Inter. A shallow
"same content, same order" pass would miss all three — the render would show
sharp Material3-default corners and wrong body-text weight while looking
"close enough" at a glance. Catching this means reading `styles.css`'s resolved
values and cross-referencing `Color.kt`/`Theme.kt` directly, not eyeballing a
screenshot.

## When to use

- The user says a screen's style "doesn't match the mock," or asks to fix fidelity.
- As the fix step within `implement-ticket`'s Screen/ViewModel/Previews layer,
  once the Screen/Previews compile — invoke this instead of hand-patching ad hoc.
- Standalone, on any already-built screen, whenever it needs to be brought back
  into alignment (e.g. after a shared component's default styling changed).

## Required Information

Ask for the screen name or `Screen.kt` path if not given. If invoked with no
argument mid-conversation, infer it from the most recently touched
`*Screen.kt`/`*Previews.kt`.

---

## Step 1 — Locate the mock and the screen

Find the mock (see the screen → mock table in `_shared/mock-fidelity-check.md`).
If none exists for this screen, stop and say so — there's nothing to compare
against.

Locate `<Feature>Screen.kt`, `<Feature>ViewModel.kt`, and `<Feature>Previews.kt`
under `composeApp/src/commonMain/kotlin/com/cramsan/hirsh/ui/screens/<feature>/`.

## Step 2 — Resolve the mock's actual token values

Read the mock's HTML plus `prototype/shared/styles.css`. For every CSS class the
mock's relevant markup uses (`.card`, `.badge-*`, `.kv`, `.visit-card`,
`.profile-card`, `.nav-item`, etc.), resolve every `var(--...)` reference to its
concrete literal value — grep `styles.css` for `--token-name:` and note the hex
color / px size / weight. Build a short table:
`element → property → resolved value` (e.g. `.badge-warn → background →
#f6ece8`, `.visit-card → border-radius → 10px`, `.kv → font-size/weight of
value → 13px/600`).

This table is the ground truth for Step 4 — compare against these resolved
values, never against class names alone.

## Step 3 — Render the current implementation

```bash
./gradlew :composeApp:recordRoborazziDesktop --tests "*<PreviewFunctionName>*"
```

View the resulting PNG(s) under `screenshots/` with Read.

## Step 4 — Token-level comparison

For every visual element the mock shows, compare Step 2's resolved value
against what the render shows — cross-referencing the **Compose source**, not
just the pixels:

- **Cards / surfaces** (`profile-card`, `visit-card`, form sections) — does the
  Compose `Card`/`Surface` set an explicit `shape = RoundedCornerShape(...)`,
  `border`, and `colors`, or fall back to Material3 defaults? Material3
  defaults essentially never match the mock's flat `--radius: 10px`,
  `1.5px solid var(--faint)`-bordered cards. Watch for the classic Material3
  gotcha: a non-zero `elevation` triggers the tonal-elevation overlay, tinting
  the container color even with an explicit `containerColor` set — a
  faintly-tinted card instead of the mock's flat `--paper`/white is the
  symptom; fix by setting `containerColor` explicitly (or `elevation = 0.dp`
  for a genuinely flat mock card), not by removing the border.
- **Badges / status pills** (`badge-done`, `badge-warn`, `badge-prog`, `badge-off`,
  the allergy/hospitalización-estado/HC-completion badges) — is it a shaped
  container with background + content color from `HirshTheme`'s color scheme
  (or `HissWarn`/`HissWarnWash`/`HissSuccess`/`HissSuccessWash` directly), or
  plain `Text` with just a color and no background? Does the tone used actually
  match the mock's semantic color (warn=terracotta, success/done=green,
  accent/prog=teal), or is an unrelated token standing in?
- **Muted/secondary text** (`.ink2` in the mock — counts, captions, metadata,
  "last edited" lines) — as of this writing there is **no** `HissInk2` token in
  `Color.kt`. If the render uses `MaterialTheme.colorScheme.onSurfaceVariant` or
  a hardcoded gray as a substitute, that's expected *today*, but check whether
  it actually resolves close to `#75726b` — if not, this is a real finding, and
  the fix (Step 5) may mean adding the missing token rather than picking a
  closer substitute inline.
- **Typography** — does the `Text`'s `style`/`fontWeight` match the mock's
  resolved size/weight, or is it an unexamined Material3 default? Given
  `Theme.kt` doesn't currently set a custom `Typography`, expect this to need a
  local override more often than not — don't assume `headlineSmall`/
  `bodyMedium`/etc. already matches the mock's scale.
- **Structural elements** — every element the mock's markup shows for this
  screen/state (icons, dividers, empty-state copy, the sidebar's active-item
  highlight, chips) is actually present — a missing element is a finding, not a
  style nuance.

List every mismatch as: `element — mock value → current code (file:line) →
what's wrong`.

## Step 5 — Fix by reusing existing patterns first

For each mismatch, **before writing a new value**, search for how the same
pattern is already solved elsewhere:

1. Grep sibling screens under `ui/screens/` and `ui/components/` for the same
   kind of element (another bordered card, another status pill). A match may
   already exist — reuse it rather than re-deriving the same values inline.
2. Check `ui/theme/Color.kt` for a token whose resolved value already matches
   what the mock wants.
3. Only if genuinely nothing exists (e.g. `HissInk2`, or a corner-radius token),
   add it to `Color.kt` (and a shape/radius equivalent if one doesn't exist),
   following the existing `Hiss*` naming convention, with the exact hex/dp from
   `styles.css` — not an eyeballed approximation. Provide a sensible dark-theme
   value too if `DarkColors` in `Theme.kt` already has an equivalent slot for
   the token's semantic role (e.g. an `ink2`-equivalent in `darkColorScheme`);
   note explicitly that the mock is light-only so any dark value is a
   best-effort extrapolation, consistent with the existing palette's light/dark
   derivation.
4. For typography that needs to match the mock's declared scale but has no
   Material3 equivalent, use a local `Text` style override (`.copy(fontSize =
   ..., fontWeight = ...)`) scoped to that composable — don't change
   `HirshTheme`'s global type scale for one screen's fix unless asked to.
5. Never hardcode a one-off `Color(0x...)` or a raw `.dp` corner radius directly
   inside a `Screen.kt` when the same value is needed more than once — it
   belongs in `ui/theme/` so other screens can reuse it.

## Step 6 — Rebuild, re-render, re-diff

```bash
./gradlew :composeApp:compileKotlinDesktop --quiet
./gradlew :composeApp:recordRoborazziDesktop --tests "*<PreviewFunctionName>*"
git status --porcelain screenshots/
```

Confirm the build is green and the screenshot diff is scoped to the screen
being fixed — `git checkout --` anything unrelated that a broader Roborazzi run
might have swept up.

## Step 7 — Loop until clean

Re-run Step 4's comparison against the freshly rendered PNG. If any mismatch
remains, go back to Step 5. Re-check *every* element on each pass, including
ones already fixed — one fix (an explicit `containerColor`) can interact with
another (elevation tint). Stop only when a full pass finds nothing left to fix.

## Step 8 — Report

Summarize:

- Every fix made, as `element — mock value → code change (file:line)`.
- Any new theme tokens added to `Color.kt`/`Theme.kt`, and why no existing one
  covered it.
- Anything found to be systemically wrong elsewhere (a sibling screen using the
  same broken card/badge pattern) that was **not** fixed because it's outside
  this screen's scope — name it explicitly rather than leaving it to be
  silently rediscovered later.
- Confirmation the build is green and the screenshot diff under `screenshots/`
  is scoped to the intended screen.

This skill does not commit on its own — leave that to the calling workflow
(`implement-ticket`'s per-layer commit discipline, or a direct commit if invoked
standalone and the user asks for one).
