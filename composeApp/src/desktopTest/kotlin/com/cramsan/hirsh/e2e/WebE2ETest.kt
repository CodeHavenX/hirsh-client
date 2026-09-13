package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import com.cramsan.cmpbridge.driver.ManagedBridgeDriver
import com.cramsan.cmpbridge.driver.WasmDevServerProcess
import com.cramsan.cmpbridge.driver.WebBridgeDriver
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
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
 */
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
            managedDriver.close()
        }
    }

    override val driver: BridgeDriver get() = managedDriver
}
