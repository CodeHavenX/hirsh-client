package com.cramsan.hirsh.ui.screens.accounts

import app.cash.turbine.test
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.Role
import com.cramsan.hirsh.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun sampleAccount(
    name: String = "Dr. Sara Lin",
    username: String = "slin",
    status: AccountStatus = AccountStatus.ACTIVE,
) = Account(name = name, username = username, role = Role.DOCTOR, status = status, lastLogin = "28 May 2026")

private class FakeAccountRepository(accounts: List<Account> = listOf(sampleAccount())) : AccountRepository {
    private val _accounts = MutableStateFlow(accounts)
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    override fun getAccount(username: String): Flow<Account?> =
        accounts.map { list -> list.find { it.username == username } }

    override suspend fun updateLastLogin(username: String, lastLogin: String) = Unit

    override suspend fun addAccount(name: String, username: String): Account {
        val created = Account(name = name, username = username, role = Role.DOCTOR, status = AccountStatus.ACTIVE, lastLogin = "—")
        _accounts.update { it + created }
        return created
    }

    override suspend fun updateAccount(originalUsername: String, name: String, username: String) {
        _accounts.update { list -> list.map { if (it.username == originalUsername) it.copy(name = name, username = username) else it } }
    }

    override suspend fun deactivate(username: String) {
        _accounts.update { list -> list.map { if (it.username == username) it.copy(status = AccountStatus.INACTIVE) else it } }
    }

    override suspend fun reactivate(username: String) {
        _accounts.update { list -> list.map { if (it.username == username) it.copy(status = AccountStatus.ACTIVE) else it } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState reflects the repository's accounts`() = runTest(dispatcher) {
        val account = sampleAccount()
        val viewModel = AccountsViewModel(FakeAccountRepository(listOf(account)))

        viewModel.uiState.test {
            // stateIn's own initial value (AccountsUiState(), empty accounts) arrives first;
            // the combine's real first computation (the repository's seeded accounts) follows
            // once the upstream flow is actually collected.
            var latest = awaitItem()
            while (latest.accounts.isEmpty()) {
                latest = awaitItem()
            }
            assertEquals(listOf(account), latest.accounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openAddDialog opens a blank Add dialog with a generated temp password`() = runTest(dispatcher) {
        val viewModel = AccountsViewModel(FakeAccountRepository())

        viewModel.uiState.test {
            awaitItem()
            viewModel.openAddDialog()
            val dialog = assertIs<AccountDialog.Add>(awaitItem().dialog)
            assertEquals("", dialog.name)
            assertEquals("", dialog.username)
            assertEquals(true, dialog.tempPassword.contains('-'))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmAdd blocks when name or username is blank`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()
        val viewModel = AccountsViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.openAddDialog()
            awaitItem()
            viewModel.onAddNameChange("Dr. New Doc")

            val loaded = awaitItem()
            viewModel.confirmAdd()
            dispatcher.scheduler.advanceUntilIdle()

            val dialog = assertIs<AccountDialog.Add>(loaded.dialog)
            assertEquals("Dr. New Doc", dialog.name)
            assertEquals(1, repository.accounts.value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmAdd succeeds and the new account appears live`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()
        val viewModel = AccountsViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.openAddDialog()
            awaitItem()
            viewModel.onAddNameChange("Dr. New Doc")
            awaitItem()
            viewModel.onAddUsernameChange("ndoc")
            awaitItem()

            viewModel.confirmAdd()

            var latest = awaitItem()
            while (latest.accounts.size < 2 || latest.dialog != AccountDialog.None) {
                latest = awaitItem()
            }
            assertEquals(AccountDialog.None, latest.dialog)
            assertEquals(true, latest.accounts.any { it.username == "ndoc" && it.name == "Dr. New Doc" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openEditDialog pre-fills from the selected account`() = runTest(dispatcher) {
        val account = sampleAccount(name = "Dr. Sara Lin", username = "slin")
        val viewModel = AccountsViewModel(FakeAccountRepository(listOf(account)))

        viewModel.uiState.test {
            awaitItem()
            viewModel.openEditDialog(account)
            val dialog = assertIs<AccountDialog.Edit>(awaitItem().dialog)
            assertEquals("Dr. Sara Lin", dialog.name)
            assertEquals("slin", dialog.username)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmEdit blocks when a field is blank`() = runTest(dispatcher) {
        val account = sampleAccount()
        val repository = FakeAccountRepository(listOf(account))
        val viewModel = AccountsViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.openEditDialog(account)
            awaitItem()
            viewModel.onEditNameChange("")

            val loaded = awaitItem()
            viewModel.confirmEdit()
            dispatcher.scheduler.advanceUntilIdle()

            val dialog = assertIs<AccountDialog.Edit>(loaded.dialog)
            assertEquals("", dialog.name)
            assertEquals("Dr. Sara Lin", repository.accounts.value.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmEdit succeeds and updates the account live`() = runTest(dispatcher) {
        val account = sampleAccount()
        val repository = FakeAccountRepository(listOf(account))
        val viewModel = AccountsViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.openEditDialog(account)
            awaitItem()
            viewModel.onEditNameChange("Dr. Sara Lin-Chen")
            awaitItem()

            viewModel.confirmEdit()

            var latest = awaitItem()
            while (latest.dialog != AccountDialog.None) {
                latest = awaitItem()
            }
            assertEquals("Dr. Sara Lin-Chen", latest.accounts.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmReset closes the dialog without calling the repository`() = runTest(dispatcher) {
        val account = sampleAccount()
        val repository = FakeAccountRepository(listOf(account))
        val viewModel = AccountsViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.openResetDialog(account)
            awaitItem()

            viewModel.confirmReset()

            assertEquals(AccountDialog.None, awaitItem().dialog)
            assertEquals(account, repository.accounts.value.single())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmDeactivate flips the account's status live`() = runTest(dispatcher) {
        val account = sampleAccount()
        val repository = FakeAccountRepository(listOf(account))
        val viewModel = AccountsViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.openDeactivateDialog(account)
            awaitItem()

            viewModel.confirmDeactivate()

            var latest = awaitItem()
            while (latest.dialog != AccountDialog.None) {
                latest = awaitItem()
            }
            assertEquals(AccountStatus.INACTIVE, latest.accounts.single().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reactivate flips status without opening a dialog`() = runTest(dispatcher) {
        val account = sampleAccount(status = AccountStatus.INACTIVE)
        val repository = FakeAccountRepository(listOf(account))
        val viewModel = AccountsViewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            viewModel.reactivate(account)

            val latest = awaitItem()
            assertEquals(AccountStatus.ACTIVE, latest.accounts.single().status)
            assertEquals(AccountDialog.None, latest.dialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `closeDialog discards an in-progress draft`() = runTest(dispatcher) {
        val viewModel = AccountsViewModel(FakeAccountRepository())

        viewModel.uiState.test {
            awaitItem()
            viewModel.openAddDialog()
            awaitItem()
            viewModel.onAddNameChange("Draft that should be discarded")
            awaitItem()

            viewModel.closeDialog()
            assertEquals(AccountDialog.None, awaitItem().dialog)

            viewModel.openAddDialog()
            val reopened = assertIs<AccountDialog.Add>(awaitItem().dialog)
            assertEquals("", reopened.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmAdd ignores a second call while already saving`() = runTest(dispatcher) {
        val repository = FakeAccountRepository(emptyList())
        val viewModel = AccountsViewModel(repository)
        viewModel.openAddDialog()
        viewModel.onAddNameChange("Dr. New Doc")
        viewModel.onAddUsernameChange("ndoc")

        viewModel.confirmAdd()
        viewModel.confirmAdd()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.accounts.value.size)
    }
}
