package com.cramsan.hirsh.e2e

import com.cramsan.cmpbridge.driver.BridgeDriver
import com.cramsan.cmpbridge.driver.DesktopAppProcess
import com.cramsan.cmpbridge.driver.DesktopBridgeDriver
import com.cramsan.cmpbridge.driver.ManagedBridgeDriver
import org.junit.AfterClass
import org.junit.BeforeClass

/**
 * Runs [HissE2EScenarios] against the real Compose Desktop app: [DesktopAppProcess] launches
 * `com.cramsan.hirsh.MainKt` as a subprocess with the bridge armed and an isolated `user.home`
 * (so a persisted `sessionUsername` from an unrelated manual run -- see the run-desktop skill's
 * gotchas -- can't leak in), then [DesktopBridgeDriver] talks to its embedded
 * `DesktopBridgeServer` over a socket.
 */
class DesktopE2ETest : HissE2EScenarios() {

    companion object {
        private lateinit var appProcess: DesktopAppProcess
        private lateinit var managedDriver: ManagedBridgeDriver

        @JvmStatic
        @BeforeClass
        fun launchApp() {
            appProcess = DesktopAppProcess.launch("com.cramsan.hirsh.MainKt")
            managedDriver = ManagedBridgeDriver(appProcess, DesktopBridgeDriver.connect(appProcess.host, appProcess.port))
        }

        @JvmStatic
        @AfterClass
        fun tearDownApp() {
            managedDriver.close()
        }
    }

    override val driver: BridgeDriver get() = managedDriver
}
