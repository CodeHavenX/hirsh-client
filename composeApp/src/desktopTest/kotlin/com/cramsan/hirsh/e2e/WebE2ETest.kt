package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import com.cramsan.cmpbridge.driver.ManagedBridgeDriver
import com.cramsan.cmpbridge.driver.WasmDevServerProcess
import com.cramsan.cmpbridge.driver.WebBridgeDriver
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
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
 * Unlike [DesktopE2ETest], this launches ONE dev server + ONE browser/page for the WHOLE CLASS
 * ([BeforeClass]/[AfterClass]), not per test. A per-test [WebBridgeDriver.connect] was tried
 * first (matching desktop's per-test relaunch, per [HissE2EScenarios]'s doc comment) but proved
 * untenable: it launches a brand-new Playwright + Chromium process every time, and this app's
 * compiled wasmJs payload is large (`composeApp.wasm` + `skiko.wasm`, ~35 MiB combined) --
 * downloading, parsing and instantiating that from a cold browser process consistently exceeded
 * cmp-bridge-driver's fixed 30s connect timeout on every single test. Under the old
 * one-process-per-class design that cost is paid exactly once for the whole run instead of once
 * per test. Because this class shares one page across every test, [HissE2EScenarios]'s
 * [loginAsAdmin]/[createE2eTestDoctorAccount] helpers are written to tolerate that (signing out
 * first if already authenticated, skipping account creation if the row already exists) so the
 * same test bodies work correctly on both targets despite the different lifecycle.
 *
 * Ignored for now: on at least one dev machine, the very first [WebBridgeDriver.connect] in
 * [launchApp] hangs indefinitely -- Compose Web's accessibility root never populates, even though
 * the identical dev server URL loads and works correctly in a normal, non-automated browser tab
 * on the same machine. Extensive isolated testing (different browsers, headless vs. headed, GPU/
 * sandbox flags) narrowed it to something Playwright-automation-specific rather than an app or
 * lifecycle bug -- see https://github.com/CodeHavenX/hirsh-client/issues/32 for the full
 * investigation. Remove this once that's resolved (or confirmed CI-only-safe).
 */
@Ignore("WebBridgeDriver.connect() hangs under Playwright automation on at least one dev machine -- see hirsh-client#32")
class WebE2ETest : HissE2EScenarios() {

    companion object {
        private lateinit var devServer: WasmDevServerProcess
        private lateinit var managedDriver: ManagedBridgeDriver

        @JvmStatic
        @BeforeClass
        fun launchApp() = runBlocking {
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
            managedDriver = ManagedBridgeDriver(devServer, WebBridgeDriver.connect(devServer.url))
        }

        @JvmStatic
        @AfterClass
        fun tearDownApp() {
            // launchApp() can fail after devServer starts but before WebBridgeDriver.connect
            // succeeds (e.g. the connect timeout) -- managedDriver, which would normally own
            // closing devServer too, is never constructed in that case. Guard both independently
            // so a setup failure doesn't (a) throw a second, confusing
            // UninitializedPropertyAccessException on top of the real error, or (b) leak the
            // dev-server subprocess.
            if (::managedDriver.isInitialized) {
                managedDriver.close()
            } else if (::devServer.isInitialized) {
                devServer.close()
            }
        }
    }

    override val driver: BridgeDriver get() = managedDriver
}
