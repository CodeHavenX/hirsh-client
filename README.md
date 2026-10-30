# hirsh-client

Client for HISS (Hospital Information System). Kotlin Multiplatform + Compose
Multiplatform, targeting **Android, iOS, Desktop (JVM), and Web (Wasm)**.

This repo holds the client only. It talks to a backend HISS service that
lives in a separate repo; that integration isn't wired up yet (see
`network/ApiConfig.kt`).

The UI screens are scaffolding, not a port of the mocks. `prototype/` (plain
HTML/JS) is the source of truth for what HISS needs to do — patients,
admision, historia clinica, evolucion, accounts — and stays in the repo as a
reference while the real screens get built out one at a time.

## Prerequisites

- **JDK 17 or 21** to run Gradle. Gradle 8.13's embedded Kotlin script
  compiler cannot parse the version string from JDK 22+ (`java.lang.IllegalArgumentException: 25.0.3`
  when running any `./gradlew` command). If `java -version` on your machine
  reports 22+, point Gradle at an older JDK instead of your system default:
  `export JAVA_HOME=/path/to/jdk-21` (or set `org.gradle.java.home` in
  `~/.gradle/gradle.properties`, machine-local, not in this repo).
- **Android**: Android Studio (or just the SDK command-line tools) with
  `ANDROID_HOME`/`local.properties` configured.
- **iOS**: Xcode, on macOS. `iosApp/iosApp.xcodeproj` runs
  `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` as a build
  phase, so Gradle needs to be runnable from Xcode's build environment too.
- **Web**: nothing extra beyond the JDK; Kotlin/Wasm toolchain downloads are
  handled by Gradle.

## Module layout

Single-module KMP setup — `composeApp` holds all shared code and Android
directly, plus a per-platform `expect`/`actual` seam for the handful of
things that can't be shared (HTTP engine, local settings storage, the
platform entry point):

```
composeApp/src/
  commonMain/    domain model, repositories, DI (Koin), theme, navigation, screens
  androidMain/   MainActivity, Application, AndroidManifest, OkHttp engine
  iosMain/       MainViewController, Darwin engine
  desktopMain/   Window entry point, CIO engine
  wasmJsMain/    ComposeViewport entry point, index.html, Js engine
iosApp/          thin Xcode project wrapper (required for iOS distribution;
                 embeds the KMP framework built from composeApp)
```

Nothing is split into `core:*`/`feature:*` modules yet — that's a deliberate
choice to keep things simple while there's only a handful of screens. Split
by feature once a module boundary earns its keep (e.g. historia-clinica
becomes big enough, or two features need independent release cadence).

## Stack

- **Koin** for DI (`di/AppModule.kt`, `di/PlatformModule.kt` + per-platform actuals)
- **Ktor client** for networking, engine chosen per platform (OkHttp/Darwin/CIO/Js)
- **Navigation-Compose** (multiplatform, string routes via `Routes` -- see the
  note in `ui/navigation/Route.kt` on why not the newer type-safe
  `@Serializable` routes yet)
- **kotlinx.serialization** for DTOs once the backend contract exists (already
  wired into the Ktor client's content negotiation)

`gradle/libs.versions.toml` has a note at the top: the stack moves fast
enough that versions are worth double-checking before starting real feature
work. As of this skeleton, `compileKotlin{Android,Desktop,WasmJs,IosArm64,
IosSimulatorArm64,IosX64}` all build clean at these pinned versions.

## Running it

```
./gradlew :composeApp:assembleDebug              # Android APK
./gradlew :composeApp:run                         # Desktop
./gradlew :composeApp:wasmJsBrowserDevelopmentRun # Web (dev server)
```

The web dev server runs on `http://localhost:3000`. The browser only lets it call
the backend if the backend allows that origin, so start the backend with
`CORS_ALLOWED_ORIGIN=http://localhost:3000` (its default is `https://...`, which
a plain-http dev server never matches). Desktop, Android and iOS aren't subject to
CORS and need nothing extra.

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run. First build will be
slow (compiles the KMP framework for the simulator/device architecture).

## Deploying the web client

`deploy/web.Dockerfile` builds the wasmJs distribution (no Android SDK needed) and
serves it from nginx. nginx also proxies `/api/` to a `backend` service
(`deploy/nginx.conf`), so the page and the API share one origin. That matters: the
backend's session cookie is `SameSite=Lax`, so a client on a different site from the
backend can't log in.

The image adds `<meta name="hiss-api-base-url" content="">` to `index.html`. A blank
value tells the app to call its own origin; with no tag (the dev server), it calls
`ApiConfig.BASE_URL` as usual (`network/ApiBaseUrl.wasmJs.kt`).

The compose file that runs this image next to Postgres and the backend lives in the
backend repo (`docker-compose.deploy.yml`); see its README.

## Lint (detekt)

`detekt-rules/` is a small Gradle module with project-specific detekt rules,
wired into `composeApp` (`./gradlew :composeApp:detekt`). Standard detekt
rulesets (style, complexity, naming, ...) are off for now -- see the comment
in `config/detekt/detekt.yml` -- so the only rules that run are ours:

- **ScreenMissingViewModel** / **ScreenMissingPreviews** -- every
  `FooScreen.kt` must have a matching `FooViewModel.kt` and `FooPreviews.kt`
  next to it in the same directory.

`composeApp/build.gradle.kts` also points `detekt.baseline` at
`config/detekt/baseline.xml` for grandfathering in violations that predate a
rule -- there's currently nothing in it (all four screens comply), but if
you introduce a rule against existing code, regenerate it with
`./gradlew :composeApp:detektBaseline` rather than hand-editing it.

## Screenshot testing (Roborazzi)

Every `@Preview` composable under `ui.screens` and `ui.components` (including
`private` ones -- see `composeApp/build.gradle.kts`'s
`generateComposePreviewDesktopTests`) gets its own screenshot test, rendered
on Compose Desktop (no Robolectric/Android needed) and compared against a
golden PNG committed under `composeApp/screenshots/` (Git LFS).

```
./gradlew :composeApp:recordRoborazziDesktop   # (re)record goldens from current source
./gradlew :composeApp:verifyRoborazziDesktop   # compare against committed goldens, no writes
```

Both can be scoped to one preview with `--tests "*ScreenName*"`.

**`recordRoborazziDesktop` never deletes anything** -- it only adds/updates a
PNG for each preview that exists right now, so deleting or renaming a
`@Preview` function leaves its old golden behind as an orphaned file nobody
points at. To catch that, clear before recording:

```
./gradlew :composeApp:clearRoborazziDesktop
./gradlew :composeApp:recordRoborazziDesktop
```

`git status composeApp/screenshots/` afterwards shows exactly what's missing
now vs. before -- anything that shows as deleted and doesn't come back is an
orphan worth removing for real (`git rm`).

After either flow, actually look at every changed PNG before committing, not
just the diff stat -- and don't assume a diff is noise just because your own
change didn't touch that screen. A golden can be stale for a completely
different reason: `PatientListScreenPreview.png` sat on `main` showing only 3
patients for a long time after `InMemoryPatientRepository` was seeded with all
6, because nothing re-recorded it when the seed data changed elsewhere. Two
separate re-records surfaced that exact diff and got reverted as "probably
just rendering noise" without a pixel-level comparison -- both wrong. Diff the
two images for real (`ImageChops.difference` or equivalent) before deciding;
a changed bounding box that lines up with actual new/removed content is a real
fix to commit, not noise to discard.

## Auth / data

Every repository bound in `di/AppModule.kt` is a `Ktor*` implementation backed by
the separate backend service (see `network/ApiConfig.kt`). The Historia Clinica and
evoluciones are the backend's clinical documents (HCEM-79), created from its `HC_PSIQ`
and `EVOL_PSIQ` templates -- a backend without those templates seeded can't store
either. The `InMemory*` repositories remain only for tests. The app needs a running
backend to sign in at all. A session is
the backend's cookie, restored on launch through `GET /api/v1/auth/me` (HISS-611);
the app persists nothing of its own about it (HISS-664).

## Running the test suite against a real backend

`:composeApp:desktopTest` (part of `verifyLocal`/`verifyCi`) is fully
self-contained -- no backend or display needed. The full UI e2e suite
(`composeApp/src/desktopIntegrationTest/kotlin/com/cramsan/hirsh/e2e/`) lives
in the separate `desktopIntegrationTest` compilation instead, alongside the
network-layer real-backend tests (see
`.claude/skills/_shared/real-backend-check.md`) -- deliberately **not** part
of `verifyLocal`/`verifyCi`, since both depend on a real, separately-running
instance of the backend service (`http://localhost:8080` by default -- see
`network/ApiConfig.kt`) **and** two environment variables set to that
backend's seeded admin credentials, matching whatever it reads to bootstrap
that account:

```
HIRSH_ADMIN_USERNAME=<seeded admin username>
HIRSH_ADMIN_PASSWORD=<seeded admin password>
./gradlew :composeApp:desktopIntegrationTest
```

Without both, every login-dependent e2e scenario fails immediately with a
clear "not set" error rather than a confusing timeout.
