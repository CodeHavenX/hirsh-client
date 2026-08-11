import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
}

// ComposablePreviewScanner (used by Roborazzi's desktop preview screenshot tests) publishes
// JVM 17 metadata, so the desktop target's compile output and test classpath must request 17.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Used by the expect/actual Preview alias in ui/preview/Preview.kt (still Beta in Kotlin 2.3.20),
// across every platform's compile task (JVM, JS/Wasm, Native, metadata).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

afterEvaluate {
    // 21, not 17: ComposablePreviewScanner (Roborazzi) only publishes JVM 17 metadata and
    // cmp-bridge-driver (ui/e2e/) only publishes JVM 21+ -- Gradle's TargetJvmVersion
    // compatibility rule accepts a producer's target <= the consumer's request, so requesting
    // 21 keeps both resolvable (17 <= 21) rather than requiring an exact match.
    listOf("desktopTestCompileClasspath", "desktopTestRuntimeClasspath").forEach { name ->
        configurations.named(name) {
            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activityCompose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinxJson)

            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing)
            // Debug-only in-app driver server, armed only via CMP_BRIDGE_ENABLED=true --
            // see .claude/skills/run-desktop/SKILL.md.
            implementation(libs.cmp.bridge)
        }
        val desktopTest by getting {
            dependencies {
                // Powers Roborazzi's generateComposePreviewDesktopTests: scans commonMain for
                // @Preview composables and screenshots them by rendering on Compose Desktop.
                implementation(libs.roborazzi.compose.desktop.preview.scanner.support)
                implementation(libs.composable.preview.scanner.android)
                implementation(libs.junit)
                // JVM-only driver for cmp-bridge e2e tests (ui/e2e/) -- drives the desktop app
                // via a socket to its embedded DesktopBridgeServer, and the wasmJs app via a
                // headless Chromium (Playwright) walking Compose Web's own accessibility DOM.
                // No wasmJs compilation involved on either side; see ui/e2e/README for the
                // architecture note.
                implementation(libs.cmp.bridge.driver)
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

// cmp-bridge-driver's WasmDevServerProcess shells out to `./gradlew <module>:wasmJsBrowserDevelopmentRun`
// from a plain JVM test (ui/e2e/WebE2ETest.kt) -- it needs the repo root to find `gradlew` from,
// which isn't derivable from the test JVM's own working directory once Gradle forks it.
tasks.named<Test>("desktopTest") {
    systemProperty("e2e.repoRoot", rootProject.projectDir.absolutePath)
}

android {
    namespace = "com.cramsan.hirsh"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.cramsan.hirsh"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.cramsan.hirsh.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.cramsan.hirsh"
            packageVersion = "1.0.0"
        }
    }
}

roborazzi {
    // Goldens live under a committed directory (not build/) so they're versioned and diffable
    // in review, the same way the rest of the screenshot-testing ecosystem expects.
    outputDir.set(layout.projectDirectory.dir("screenshots"))

    // Generates one screenshot test per @Preview composable under ui.screens, rendered on
    // Compose Desktop (no Robolectric/Android needed). Run `./gradlew recordRoborazziDesktop`
    // to (re)record goldens under screenshots/, `verifyRoborazziDesktop` to check them.
    @OptIn(ExperimentalRoborazziApi::class)
    generateComposePreviewDesktopTests {
        enable = true
        packages = listOf("com.cramsan.hirsh.ui.screens", "com.cramsan.hirsh.ui.components")
        // Our *Previews.kt composables are private by convention (see ScreenMissingPreviews).
        includePrivatePreviews = true
    }
}

// Only takes effect when both tasks are actually in the same invocation's graph (e.g. via the
// root project's `verifyCi`) -- a standalone `recordRoborazziDesktop` (optionally `--tests`
// filtered, as match-mock-fidelity does per-screen) is unaffected, since this doesn't add
// clearRoborazziDesktop as a dependency, only orders it first if it's already going to run.
tasks.named("recordRoborazziDesktop") {
    mustRunAfter("clearRoborazziDesktop")
}

detekt {
    // KMP source sets (commonMain, androidMain, ...) live outside the
    // src/main/src/test layout detekt's Gradle plugin looks for by default.
    source.setFrom(files("src"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

dependencies {
    detektPlugins(project(":detekt-rules"))
}
