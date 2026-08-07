package com.cramsan.hirsh.repository

import app.cash.turbine.test
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.preferences.AppPreferences
import com.cramsan.hirsh.util.Clock
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// No in-memory Settings ships with multiplatform-settings 1.2.0 (only the
// java.util.prefs-backed PreferencesSettings), so this is a minimal fake
// implementing the interface directly rather than pulling in a test artifact
// for one repository test.
private class FakeSettings : Settings {
    private val map = mutableMapOf<String, Any?>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() {
        map.clear()
    }
    override fun remove(key: String) {
        map.remove(key)
    }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putInt(key: String, value: Int) {
        map[key] = value
    }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) {
        map[key] = value
    }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putString(key: String, value: String) {
        map[key] = value
    }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putFloat(key: String, value: Float) {
        map[key] = value
    }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) {
        map[key] = value
    }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

private val FIXED_NOW: Instant = LocalDateTime(2027, 1, 15, 10, 30).toInstant(TimeZone.currentSystemDefault())

private class StubAuthClock(private val instant: Instant = FIXED_NOW) : Clock {
    override fun now(): Instant = instant
}

private fun newRepository(): FakeAuthRepository = FakeAuthRepository(
    preferences = AppPreferences(FakeSettings()),
    accountRepository = InMemoryAccountRepository(),
    clock = StubAuthClock(),
)

class AuthRepositoryTest {

    @Test
    fun `login with a matched active account resolves displayName and role`() = runTest {
        val repository = newRepository()

        val session = repository.login("apatel", "hunter2").getOrThrow()

        assertEquals("Dr. Anita Patel", session.displayName)
        assertEquals(Role.DOCTOR, session.role)
    }

    @Test
    fun `login with the admin account resolves the Admin role`() = runTest {
        val repository = newRepository()

        val session = repository.login("admin", "hunter2").getOrThrow()

        assertEquals("Administrador", session.displayName)
        assertEquals(Role.ADMIN, session.role)
    }

    @Test
    fun `login with a matched inactive account fails with the generic error`() = runTest {
        val repository = newRepository()

        val result = repository.login("tveer", "hunter2")

        assertTrue(result.isFailure)
        assertEquals("Usuario o contrasena incorrectos", result.exceptionOrNull()?.message)
    }

    @Test
    fun `login with an unmatched username still succeeds with the default role`() = runTest {
        val repository = newRepository()

        val session = repository.login("nobody", "hunter2").getOrThrow()

        assertEquals("nobody", session.displayName)
        assertEquals(Role.DOCTOR, session.role)
    }

    @Test
    fun `login with blank credentials fails regardless of account status`() = runTest {
        val repository = newRepository()

        assertTrue(repository.login("", "hunter2").isFailure)
        assertTrue(repository.login("apatel", "").isFailure)
    }

    @Test
    fun `successful login writes lastLogin for the matched account`() = runTest {
        val accountRepository = InMemoryAccountRepository()
        val repository = FakeAuthRepository(
            preferences = AppPreferences(FakeSettings()),
            accountRepository = accountRepository,
            clock = StubAuthClock(),
        )

        repository.login("apatel", "hunter2")

        accountRepository.getAccount("apatel").test {
            assertEquals("15 Jan 2027 10:30", awaitItem()?.lastLogin)
        }
    }

    @Test
    fun `successful login for an unmatched username does not create an account`() = runTest {
        val accountRepository = InMemoryAccountRepository()
        val beforeCount = accountRepository.accounts.value.size
        val repository = FakeAuthRepository(
            preferences = AppPreferences(FakeSettings()),
            accountRepository = accountRepository,
            clock = StubAuthClock(),
        )

        repository.login("nobody", "hunter2")

        assertEquals(beforeCount, accountRepository.accounts.value.size)
    }

    @Test
    fun `restoreSession resolves the same displayName and role as a fresh login would`() = runTest {
        val settings = FakeSettings()
        val accountRepository = InMemoryAccountRepository()
        val repository = FakeAuthRepository(
            preferences = AppPreferences(settings),
            accountRepository = accountRepository,
            clock = StubAuthClock(),
        )
        repository.login("mreyes", "hunter2")

        val restored = repository.restoreSession()

        assertEquals("Dr. Marco Reyes", restored?.displayName)
        assertEquals(Role.DOCTOR, restored?.role)
    }

    @Test
    fun `restoreSession is null when nothing was persisted`() = runTest {
        val repository = newRepository()

        assertNull(repository.restoreSession())
    }
}
