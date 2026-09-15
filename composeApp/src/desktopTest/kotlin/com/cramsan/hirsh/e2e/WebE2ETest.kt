package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import com.cramsan.cmpbridge.driver.WasmDevServerProcess
import com.cramsan.cmpbridge.driver.WebBridgeDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import java.io.File

/**
 * Runs [HissE2EScenarios] against the real Compose Web (wasmJs) app: [WasmDevServerProcess]
 * shells out to `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` (needs the
 * `e2e.repoRoot` system property this module's build.gradle.kts wires onto the `desktopTest`
 * task) and waits for the dev server to come up, then [WebBridgeDriver] drives it through a
 * headless Chromium (Playwright) walking Compose Web's own accessibility DOM -- no in-app
 * bridge code involved on this side at all, unlike the desktop target.
 *
 * cmp-bridge-driver 0.2.0.0's web coverage has known gaps versus desktop (documented in its
 * own cmp-bridge-sample): some fields/scroll can report zero bounds in the accessibility DOM,
 * and password masking isn't reflected there. A failure here that doesn't reproduce on
 * [DesktopE2ETest] is worth checking against those gaps before assuming it's an app bug.
 *
 * The dev server itself stays [BeforeClass]/[AfterClass] -- it's just serving the static
 * webpack bundle, not holding any app state, and restarting the whole Gradle task per test
 * would be far too slow. What DOES need to reset per test (matching [DesktopE2ETest]'s full
 * relaunch, per [HissE2EScenarios]'s doc comment) is the app instance itself; for web that's
 * the browser page, not the server. [WebBridgeDriver.connect] launches a brand-new Playwright
 * browser + page and navigates it fresh, which reinstantiates the wasmJs app (and all its
 * in-memory Koin singletons) from scratch -- so moving connect/close to [Before]/[After] gives
 * the same per-test isolation as the desktop target's process relaunch, without paying for a
 * dev-server restart each time.
 */
class WebE2ETest : HissE2EScenarios() {

    companion object {
        private lateinit var devServer: WasmDevServerProcess

        @JvmStatic
        @BeforeClass
        fun launchDevServer() = runBlocking {
            // 0.2.0.0's WasmDevServerProcess.launch no longer builds the Gradle command
            // internally (0.1.0.3 read this same e2e.repoRoot property and hardcoded
            // "$module:wasmJsBrowserDevelopmentRun --console=plain") -- the caller now owns it.
            val repoRoot = File(
                System.getProperty("e2e.repoRoot") ?: error("e2e.repoRoot not set -- run via the `test` Gradle task"),
            )
            devServer = WasmDevServerProcess.launch(
                command = listOf(File(repoRoot, "gradlew").absolutePath, ":composeApp:wasmJsBrowserDevelopmentRun", "--console=plain"),
                workingDir = repoRoot,
            )
        }

        @JvmStatic
        @AfterClass
        fun tearDownDevServer() {
            if (::devServer.isInitialized) {
                devServer.close()
            }
        }
    }

    private lateinit var managedDriver: WebBridgeDriver

    @Before
    fun launchApp() = runBlocking {
        managedDriver = WebBridgeDriver.connect(devServer.url)
    }

    @After
    fun tearDownApp() {
        if (::managedDriver.isInitialized) {
            managedDriver.close()
        }
    }

    override val driver: BridgeDriver get() = managedDriver
}
