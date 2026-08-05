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
- **multiplatform-settings** for the session token / local prefs
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

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run. First build will be
slow (compiles the KMP framework for the simulator/device architecture).

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

After either flow, review every changed PNG before committing, not just the
diff stat -- re-rendering is not always pixel-stable on the same source (seen
in practice: a golden for a screen nobody touched changed on a re-record with
no visible content difference). If a changed golden's content is identical to
what's already committed, that's rendering noise, not a real change --
`git checkout --` it rather than committing a no-op diff.

## Auth / data

`repository/AuthRepository` and `repository/PatientRepository` currently
have only fake in-memory implementations (`FakeAuthRepository`,
`InMemoryPatientRepository`), seeded from `prototype/shared/data.js`, so the
login → patient list → patient record flow is demonstrable without a
backend. Swap these for `HttpClient`-backed implementations once the backend
service exposes real endpoints — the interfaces are the seam.
