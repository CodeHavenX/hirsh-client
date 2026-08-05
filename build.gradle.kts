// Root project. Every actual module (currently just :composeApp) applies
// its own plugins; declaring them here too (with apply false) keeps a
// single resolved version per plugin across the build instead of each
// module's classloader picking its own.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
}

// Two verification tiers, run by hand today; a CI workflow (once one exists) should just
// invoke verifyCi. Neither replaces Gradle's own `check`/`build` -- those still pull in
// whatever every applied plugin wires up (Android lint, wasmJs browser tests via Karma, iOS
// simulator tests, ...), several of which need tooling (a headless Chrome, an iOS simulator)
// that isn't set up everywhere these tasks need to run. These two are a deliberately scoped,
// hand-picked subset instead.

tasks.register("verifyLocal") {
    group = "verification"
    description = "Fast local gate: desktop + cross-platform compile, unit tests, detekt. " +
        "No formatter is wired in yet -- see the TODO on this task."
    // TODO(formatting): this project has no auto-fix formatter wired in (detekt's own
    // `formatting` ruleset was dropped upstream in detekt 1.23+, and nothing like ktlint/
    // Spotless has replaced it yet -- see README's Lint section). Add a *Format task here
    // once one is chosen.
    dependsOn(
        ":composeApp:compileKotlinDesktop",
        ":composeApp:compileKotlinMetadata",
        ":composeApp:desktopTest",
        ":composeApp:detekt",
        ":detekt-rules:test",
    )
}

val checkScreenshotsClean by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails if regenerating Roborazzi goldens produced an uncommitted diff -- " +
        "the fix is committing the regenerated PNGs, not suppressing this check."
    dependsOn(":composeApp:recordRoborazziDesktop")
    workingDir = rootDir
    commandLine("git", "diff", "--exit-code", "HEAD", "--", "composeApp/screenshots/")
}

tasks.register("verifyCi") {
    group = "verification"
    description = "CI-tier gate: verifyLocal, plus screenshots cleared and regenerated from " +
        "current source so a real UI change shows up as a diff in the PR, then checked for " +
        "drift. Extend with an integration-test task dependency once that suite exists."
    dependsOn(
        "verifyLocal",
        ":composeApp:clearRoborazziDesktop",
        checkScreenshotsClean,
    )
    // TODO(integration tests): add this repo's integration test task here once it exists.
}
