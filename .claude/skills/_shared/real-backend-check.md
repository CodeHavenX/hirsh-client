# Real-Backend Integration Tests (when applicable)

`ktor-client-mock`-based tests and the existing `desktopTest` E2E suite
(`composeApp/src/desktopTest/kotlin/com/cramsan/hirsh/e2e/`) only verify against
*assumptions* — the mock's fixtures, or the in-memory fakes wired in
`di/AppModule.kt`. Neither can catch a gap between what we assumed the real HISS
backend does and what it actually does. This fragment covers persisted tests
that talk to a real, separately-running instance of that backend (a separate
repo — see `network/ApiConfig.kt`'s doc comment).

**Why this exists**: HISS-604's `installApiErrorValidator` had full
`ktor-client-mock` coverage, including a 401 case — every fixture just happened
to send *some* JSON body (`{}` at minimum). The real backend's default Spring
Security 401 response sends `Content-Length: 0`, no body at all, which silently
mis-mapped to `ApiError.Unknown` instead of `ApiError.Unauthorized`. Every
mock-based test passed the whole time. Only pointing the actual production
`HttpClient` config at the actual running backend surfaced it.

These live under `composeApp/src/desktopIntegrationTest/kotlin/` (its own
compilation, alongside `desktopTest` — see the comment above
`desktopTarget.compilations.create("integrationTest")` in
`composeApp/build.gradle.kts`) and run via:

```bash
./gradlew :composeApp:desktopIntegrationTest
```

Deliberately **not** part of `verifyLocal`/`verifyCi` — depends on a separately
running backend that isn't guaranteed to exist on every machine or in CI (see
root `build.gradle.kts`'s still-open TODO on this).

---

## Step 0 — Which tier applies to this feature?

This isn't limited to diffs that literally touch `network/`. For whatever
feature/repository the ticket actually changes, check that repository's
binding in `di/AppModule.kt`:

```bash
grep -n "bind <Feature>Repository::class" composeApp/src/commonMain/kotlin/com/cramsan/hirsh/di/AppModule.kt
```

- **Bound to a `Fake*`/`InMemory*` implementation** (this is every repository
  today except the shared `HttpClient` itself — `FakeAuthRepository`,
  `InMemoryPatientRepository`, `InMemoryAccountRepository`,
  `InMemoryHospitalizationRepository`): **Tier 1 only, and only if the diff
  itself touches `network/`** (HttpClient config, plugins, error/response
  mapping). A UI E2E test against this feature would drive the UI against the
  fake and never touch the real backend at all — don't write one and call it a
  real-backend check. If the diff doesn't touch `network/` either, this
  fragment doesn't apply — note in the report that the feature's repository is
  still Fake/InMemory, so real-backend coverage isn't possible yet (name which
  ticket, if any, is tracked to migrate it — e.g. HISS-611 for
  `AuthRepository`).
- **Bound to a real, HTTP-backed implementation**: **Tier 2 applies** — add or
  extend a UI-driven scenario, not just a network-layer test, since the whole
  point of that repository existing is that the UI now genuinely depends on
  the backend.

---

## Tier 1 — Network-layer tests

### Step 1 — Check whether a real backend is reachable

```bash
curl -s -o /dev/null -w "%{http_code}\n" --max-time 2 <ApiConfig.BASE_URL>/actuator/health
```

**Any** HTTP response code counts as reachable, even 401/403/404 — only a
connection failure or timeout means "not running." If unreachable: the backend
needs to be started in its own repo (typically `./gradlew bootRun` — see that
repo's README). If you can't start it here, running `desktopIntegrationTest`
will just skip (Step 2's reachability guard) — mark this **NEEDS MANUAL
VERIFICATION** in the report rather than claiming it ran.

### Step 2 — Add or extend a persisted test

Follow `network/ApiErrorValidatorRealBackendTest.kt` as the reference shape:

- A JUnit4 `@Before` that checks reachability the same way as Step 1 and calls
  `org.junit.Assume.assumeTrue(...)` when it fails, so the class **skips**
  rather than fails with no backend up — safe to leave in the suite
  permanently.
- Construct the `HttpClient` using the **exact same** engine and plugin stack
  as `di/AppModule.kt`'s real one (copy the `install(...)` calls verbatim, not
  a subset).
- Issue a real request that exercises the specific behavior in scope, and
  assert the actual result.

### Step 3 — Compare against what the mock-based tests assume

- **Matches** — confirmed; the persisted test is the permanent record of that.
- **Doesn't match** — a genuine finding:
  1. Fix the production code to handle the real shape.
  2. Add/adjust a `ktor-client-mock`-based regression test (in `commonTest`)
     reproducing the mishandled shape, so CI catches this without a live
     backend. Confirm it fails without the fix and passes with it.
  3. Re-run `desktopIntegrationTest` to confirm the fix resolves it end-to-end.

---

## Tier 2 — Full UI E2E tests against the real backend

Applies once the feature's repository is a real, HTTP-backed implementation
(Step 0). Not buildable yet for anything in this repo — every repository is
still Fake/InMemory. The ticket that first migrates a repository to a real
implementation (starting with `AuthRepository`, tracked as HISS-611) should
also stand this tier up, not defer it further:

- New scenario files under
  `composeApp/src/desktopIntegrationTest/kotlin/com/cramsan/hirsh/e2e/`,
  driving the real app the same way
  `composeApp/src/desktopTest/kotlin/com/cramsan/hirsh/e2e/DesktopE2ETest.kt`
  and `HissE2EScenarios.kt` already do — same `cmp-bridge-driver`
  (`DesktopAppProcess`/`DesktopBridgeDriver`, `testTag`-only interaction, the
  `E2ESupport.kt` helpers) — reused, not reimplemented, so the
  `integrationTest` compilation needs `associateWith` the `test` compilation
  (in addition to `main`) and the `cmp.bridge.driver` dependency, both added
  in `composeApp/build.gradle.kts` alongside the rest of that suite's own
  wiring when this lands.
- The key difference from the existing `desktopTest` E2E suite: log in with
  real seeded backend accounts (whatever that separate repo's fixtures are),
  not the fake's `admin`/`whatever123`, and assert on data that actually came
  back over the wire.
- Same reachability-skip discipline as Tier 1 — these need the real backend up
  and running, and should skip rather than fail when it isn't.

---

## Reporting

```
## Real-Backend Verification
Tier: 1 (network-layer) | 2 (full UI E2E) | Not applicable
  <If Not applicable: name the Fake/InMemory repository and, if tracked, the
  ticket that will migrate it.>
Backend reachable at <BASE_URL>: yes | no
Ran: `./gradlew :composeApp:desktopIntegrationTest --tests "<ClassName>"`
Result: PASS | FAIL | SKIPPED (no backend reachable)
<If a mismatch was found and fixed: the finding, the fix, and the new/extended
test(s) that now cover it.>
<If skipped: "NEEDS MANUAL VERIFICATION -- no backend instance reachable in
this environment; run desktopIntegrationTest against a real endpoint before
merging.">
```
