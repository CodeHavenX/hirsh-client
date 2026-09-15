package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import com.cramsan.cmpbridge.driver.DesktopAppProcess
import com.cramsan.cmpbridge.driver.DesktopBridgeDriver
import com.cramsan.cmpbridge.driver.ManagedBridgeDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/**
 * Runs [HissE2EScenarios] against the real Compose Desktop app: [DesktopAppProcess] launches
 * `com.cramsan.hirsh.MainKt` as a subprocess with the bridge armed and an isolated `user.home`
 * (so a persisted `sessionUsername` from an unrelated manual run -- see the run-desktop skill's
 * gotchas -- can't leak in), then [DesktopBridgeDriver] talks to its embedded
 * `DesktopBridgeServer` over a socket.
 *
 * One full process launch PER TEST METHOD ([Before]/[After], not [BeforeClass]/[AfterClass]):
 * every test starts from a genuinely fresh app with the seeded fixture data and nothing else --
 * see [HissE2EScenarios]'s own doc comment for why this replaced the earlier one-process-per-class
 * design. This is slow (each launch is several seconds) but gives real isolation: no test's
 * failure or leftover state can affect any other test.
 */
class DesktopE2ETest : HissE2EScenarios() {

    private lateinit var appProcess: DesktopAppProcess
    private lateinit var managedDriver: ManagedBridgeDriver

    @Before
    fun launchApp() = runBlocking {
        appProcess = DesktopAppProcess.launch("com.cramsan.hirsh.MainKt")
        managedDriver = ManagedBridgeDriver(appProcess, DesktopBridgeDriver.connect(appProcess.host, appProcess.port))
    }

    @After
    fun tearDownApp() {
        // launchApp() can fail after appProcess starts but before DesktopBridgeDriver.connect
        // succeeds, leaving managedDriver (which would normally own closing appProcess too)
        // never constructed.
        if (::managedDriver.isInitialized) {
            managedDriver.close()
        } else if (::appProcess.isInitialized) {
            appProcess.close()
        }
    }

    override val driver: BridgeDriver get() = managedDriver
}
