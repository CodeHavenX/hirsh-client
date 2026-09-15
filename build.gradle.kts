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

// CI's Xvfb + Ubuntu-runner Compose Desktop rendering does not byte-match a real desktop's --
// confirmed by recording goldens on a real desktop (zero diff against what's already committed)
// while CI's own recording of the exact same source consistently produces different PNG bytes
// (also confirmed deterministic: identical output across independent CI runs, so this is a fixed
// environment gap, not run-to-run noise). That makes "regenerate locally, commit the diff" an
// unreliable fix for a contributor without CI's exact rendering stack -- so in CI, this commits
// and pushes any diff back to the PR branch itself instead of just failing. Locally, it keeps the
// original fail-on-diff behavior (see README's Screenshot testing section): a human should still
// review and commit a real local change by hand rather than have this push on their behalf.
val syncScreenshots by tasks.registering(Exec::class) {
    group = "verification"
    description = "Regenerates Roborazzi goldens from current source. In CI, commits and pushes " +
        "any diff back to the PR branch (CI's own render is deterministic, so this is safe); " +
        "locally, fails on any diff instead so a real UI change is reviewed and committed by hand."
    dependsOn(":composeApp:recordRoborazziDesktop")
    workingDir = rootDir
    val inCi = System.getenv("GITHUB_ACTIONS") == "true"
    commandLine(
        "bash", "-c",
        if (inCi) {
            """
            set -e
            if git diff --quiet HEAD -- composeApp/screenshots/; then
              echo "Screenshots already match current source -- nothing to sync."
            else
              git config user.name "github-actions[bot]"
              git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
              git add composeApp/screenshots/
              git commit -m "Sync Roborazzi screenshots to current source [skip ci]"
              git push origin "HEAD:${'$'}GITHUB_HEAD_REF"
            fi
            """.trimIndent()
        } else {
            "git diff --exit-code HEAD -- composeApp/screenshots/"
        },
    )
}

tasks.register("verifyCi") {
    group = "verification"
    description = "CI-tier gate: verifyLocal, plus screenshots cleared and regenerated from " +
        "current source so a real UI change shows up as a diff in the PR, then synced (CI) or " +
        "checked for drift (local). Extend with an integration-test task dependency once that " +
        "suite exists."
    dependsOn(
        "verifyLocal",
        ":composeApp:clearRoborazziDesktop",
        syncScreenshots,
    )
    // TODO(integration tests): add this repo's integration test task here once it exists.
}
