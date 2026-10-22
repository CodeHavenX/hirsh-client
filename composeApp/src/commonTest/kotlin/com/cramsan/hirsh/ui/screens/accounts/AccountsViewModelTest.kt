package com.cramsan.hirsh.ui.screens.accounts

import app.cash.turbine.test
import com.cramsan.hirsh.model.Account
import com.cramsan.hirsh.model.AccountProfile
import com.cramsan.hirsh.model.AccountRole
import com.cramsan.hirsh.model.AccountStatus
import com.cramsan.hirsh.model.NewAccount
import com.cramsan.hirsh.network.ApiError
import com.cramsan.hirsh.network.ApiException
import com.cramsan.hirsh.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun sampleAccount(
    id: String = "acc_3",
    name: String = "Dr. Sara Lin",
    username: String = "slin",
    status: AccountStatus = AccountStatus.ACTIVE,
) = Account(
    id = id,
    name = name,
    username = username,
    roles = listOf(AccountRole("ATTENDING_PHYSICIAN", "Attending Physician")),
    status = status,
    lastAccess = Instant.parse("2026-05-28T15:00:00Z"),
)

private val PHYSICIAN = AccountRole("ATTENDING_PHYSICIAN", "Attending Physician")

private class FakeAccountRepository(
    accounts: List<Account> = listOf(sampleAccount()),
    private val failWith: ApiError? = null,
) : AccountRepository {
    private val _accounts = MutableStateFlow(accounts)
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    var refreshCount = 0
    var lastNewAccount: NewAccount? = null
    var lastTemporaryPassword: String? = null

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun roles(): List<AccountRole> = listOf(PHYSICIAN)

    override suspend fun addAccount(account: NewAccount, temporaryPassword: String): Account {
        failWith?.let { throw ApiException(it) }
        lastNewAccount = account
        lastTemporaryPassword = temporaryPassword
        val created = sampleAccount(id = "acc_new", name = "${account.firstName} ${account.lastName}", username = account.username)
        _accounts.update { it + created }
        return created
    }

    override suspend fun updateAccount(id: String, profile: AccountProfile) {
        failWith?.let { throw ApiException(it) }
        _accounts.update { list -> list.map { if (it.id == id) it.copy(profile = profile) else it } }
    }

    override suspend fun resetPassword(id: String): String {
        failWith?.let { throw ApiException(it) }
        return "Gen3rated-Pass"
    }

    override suspend fun deactivate(id: String) = setStatus(id, AccountStatus.INACTIVE)

    override suspend fun reactivate(id: String) = setStatus(id, AccountStatus.ACTIVE)

    private fun setStatus(id: String, status: AccountStatus) {
        failWith?.let { throw ApiException(it) }
        _accounts.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }
}

private val filledForm = AddAccountForm(
    firstName = "Ana",
    lastName = "Torres",
    username = "atorres",
    documentNumber = "45821337",
    role = PHYSICIAN,
)

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
    fun `init loads the accounts from the repository`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()

        AccountsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `init loads the roles for the create dialog`() = runTest(dispatcher) {
        val viewModel = AccountsViewModel(FakeAccountRepository())

        viewModel.uiState.test {
            var latest = awaitItem()
            while (latest.roles.isEmpty()) {
                latest = awaitItem()
            }
            assertEquals(listOf(PHYSICIAN), latest.roles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A ViewModel with a live [AccountsViewModel.uiState] collector, so [dialogOf] can read its current value. */
    private fun TestScope.viewModelWith(repository: AccountRepository): AccountsViewModel {
        val viewModel = AccountsViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.dialogOf(viewModel: AccountsViewModel): AccountDialog {
        runCurrent()
        return viewModel.uiState.value.dialog
    }

    @Test
    fun `openAddDialog opens a blank form with a generated temp password the backend accepts`() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeAccountRepository())

        viewModel.openAddDialog()

        val dialog = assertIs<AccountDialog.Add>(dialogOf(viewModel))
        assertEquals(AddAccountForm(), dialog.form)
        assertTrue(dialog.tempPassword.length >= 8, "CreateUserRequest needs at least 8 characters")
    }

    @Test
    fun `confirmAdd blocks when a required field or the role is missing`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()
        val viewModel = viewModelWith(repository)
        val incomplete = listOf(
            filledForm.copy(firstName = " "),
            filledForm.copy(lastName = ""),
            filledForm.copy(username = ""),
            filledForm.copy(documentNumber = ""),
            filledForm.copy(role = null),
        )

        for (form in incomplete) {
            viewModel.openAddDialog()
            viewModel.onAddFormChange(form)
            viewModel.confirmAdd()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("Completa los campos requeridos", assertIs<AccountDialog.Add>(dialogOf(viewModel)).error)
        }
        assertNull(repository.lastNewAccount)
    }

    @Test
    fun `confirmAdd sends the form and the shown temp password, then closes`() = runTest(dispatcher) {
        val repository = FakeAccountRepository()
        val viewModel = viewModelWith(repository)
        viewModel.openAddDialog()
        val tempPassword = assertIs<AccountDialog.Add>(dialogOf(viewModel)).tempPassword
        viewModel.onAddFormChange(filledForm.copy(profile = AccountProfile(medicalLicense = "CMP-1")))

        viewModel.confirmAdd()
        dispatcher.scheduler.advanceUntilIdle()

        val sent = assertNotNull(repository.lastNewAccount)
        assertEquals("atorres", sent.username)
        assertEquals("ATTENDING_PHYSICIAN", sent.roleCode)
        assertEquals("CMP-1", sent.profile.medicalLicense)
        assertEquals(tempPassword, repository.lastTemporaryPassword)
        assertEquals(AccountDialog.None, dialogOf(viewModel))
    }

    @Test
    fun `confirmAdd keeps the dialog open with a message on a duplicate`() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeAccountRepository(failWith = ApiError.Conflict("u1")))
        viewModel.openAddDialog()
        viewModel.onAddFormChange(filledForm)

        viewModel.confirmAdd()
        dispatcher.scheduler.advanceUntilIdle()

        val dialog = assertIs<AccountDialog.Add>(dialogOf(viewModel))
        assertEquals("Ya existe una cuenta con ese usuario, documento, CMP o RNE", dialog.error)
        assertFalse(dialog.isSaving)
    }

    @Test
    fun `confirmAdd ignores a second call while already saving`() = runTest(dispatcher) {
        val repository = FakeAccountRepository(emptyList())
        val viewModel = viewModelWith(repository)
        viewModel.openAddDialog()
        viewModel.onAddFormChange(filledForm)

        viewModel.confirmAdd()
        viewModel.confirmAdd()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.accounts.value.size)
    }

    @Test
    fun `openEditDialog pre-fills the account's profile`() = runTest(dispatcher) {
        val account = sampleAccount().copy(profile = AccountProfile(email = "slin@hospital.pe", medicalLicense = "CMP-9"))
        val viewModel = viewModelWith(FakeAccountRepository(listOf(account)))

        viewModel.openEditDialog(account)

        assertEquals(account.profile, assertIs<AccountDialog.Edit>(dialogOf(viewModel)).profile)
    }

    @Test
    fun `confirmEdit saves the profile by id and closes`() = runTest(dispatcher) {
        val account = sampleAccount()
        val repository = FakeAccountRepository(listOf(account))
        val viewModel = viewModelWith(repository)
        viewModel.openEditDialog(account)
        viewModel.onEditProfileChange(AccountProfile(phone = "987 654 321"))

        viewModel.confirmEdit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("987 654 321", repository.accounts.value.single().profile.phone)
        assertEquals(AccountDialog.None, dialogOf(viewModel))
    }

    @Test
    fun `confirmEdit shows a registry conflict inside the dialog`() = runTest(dispatcher) {
        val account = sampleAccount()
        val viewModel = viewModelWith(FakeAccountRepository(listOf(account), failWith = ApiError.Conflict("")))
        viewModel.openEditDialog(account)

        viewModel.confirmEdit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Ese CMP o RNE ya esta registrado en otra cuenta", assertIs<AccountDialog.Edit>(dialogOf(viewModel)).error)
    }

    @Test
    fun `confirmReset shows the backend's password, and a second confirm closes the dialog`() = runTest(dispatcher) {
        val account = sampleAccount()
        val viewModel = viewModelWith(FakeAccountRepository(listOf(account)))
        viewModel.openResetDialog(account)
        assertNull(assertIs<AccountDialog.Reset>(dialogOf(viewModel)).temporaryPassword)

        viewModel.confirmReset()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Gen3rated-Pass", assertIs<AccountDialog.Reset>(dialogOf(viewModel)).temporaryPassword)

        viewModel.confirmReset()
        assertEquals(AccountDialog.None, dialogOf(viewModel))
    }

    @Test
    fun `a failed reset stays open with an error and no password`() = runTest(dispatcher) {
        val account = sampleAccount()
        val viewModel = viewModelWith(FakeAccountRepository(listOf(account), failWith = ApiError.Unknown("boom")))
        viewModel.openResetDialog(account)

        viewModel.confirmReset()
        dispatcher.scheduler.advanceUntilIdle()

        val dialog = assertIs<AccountDialog.Reset>(dialogOf(viewModel))
        assertNull(dialog.temporaryPassword)
        assertEquals("No se pudo restablecer la contraseña", dialog.error)
    }

    @Test
    fun `confirmDeactivate flips the account's status`() = runTest(dispatcher) {
        val account = sampleAccount()
        val repository = FakeAccountRepository(listOf(account))
        val viewModel = viewModelWith(repository)
        viewModel.openDeactivateDialog(account)

        viewModel.confirmDeactivate()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccountStatus.INACTIVE, repository.accounts.value.single().status)
        assertEquals(AccountDialog.None, dialogOf(viewModel))
    }

    @Test
    fun `reactivate releases an inactive or locked account without a dialog`() = runTest(dispatcher) {
        for (status in listOf(AccountStatus.INACTIVE, AccountStatus.LOCKED)) {
            val account = sampleAccount(status = status)
            val repository = FakeAccountRepository(listOf(account))
            val viewModel = viewModelWith(repository)

            viewModel.reactivate(account)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(AccountStatus.ACTIVE, repository.accounts.value.single().status)
            assertEquals(AccountDialog.None, dialogOf(viewModel))
        }
    }

    @Test
    fun `a failed status change shows a screen error instead of crashing`() = runTest(dispatcher) {
        val account = sampleAccount(status = AccountStatus.INACTIVE)
        val viewModel = viewModelWith(FakeAccountRepository(listOf(account), failWith = ApiError.Unknown("boom")))

        viewModel.uiState.test {
            viewModel.reactivate(account)
            var latest = awaitItem()
            while (latest.error == null) {
                latest = awaitItem()
            }
            assertEquals("No se pudo reactivar la cuenta", latest.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `closeDialog discards an in-progress draft`() = runTest(dispatcher) {
        val viewModel = viewModelWith(FakeAccountRepository())
        viewModel.openAddDialog()
        viewModel.onAddFormChange(filledForm)

        viewModel.closeDialog()
        viewModel.openAddDialog()

        assertEquals(AddAccountForm(), assertIs<AccountDialog.Add>(dialogOf(viewModel)).form)
    }
}
