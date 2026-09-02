package com.qixuan.channelvideoflow.feature.auth

import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.model.auth.TelegramAuthState
import com.qixuan.channelvideoflow.model.auth.TelegramCodeDeliveryType
import com.qixuan.channelvideoflow.model.auth.TelegramCodeInfo
import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialization starts repository exactly once`() = runTest {
        val repository = FakeTelegramAuthRepository()
        AuthViewModel(repository)

        runCurrent()

        assertEquals(1, repository.startCalls)
    }

    @Test
    fun `initial start failure after initial replay remains visible`() = runTest {
        val repository = FakeTelegramAuthRepository().apply {
            startFailureBeforeGate = IllegalStateException("synthetic start failure")
        }
        val viewModel = AuthViewModel(repository)

        runCurrent()

        assertEquals(1, repository.startCalls)
        assertEquals(LoginStep.INITIALIZING, viewModel.uiState.value.step)
        assertEquals(TelegramAuthFailure.Unknown, viewModel.uiState.value.failure)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `domain states map to their exact UI states`() = runTest {
        val repository = FakeTelegramAuthRepository()
        val viewModel = AuthViewModel(repository)
        runCurrent()

        val cases = listOf(
            TelegramAuthState.UnconfiguredCredentials(setOf("TELEGRAM_API_HASH")) to
                LoginUiState(LoginStep.UNCONFIGURED, invalidKeys = setOf("TELEGRAM_API_HASH")),
            TelegramAuthState.Initializing to LoginUiState(LoginStep.INITIALIZING),
            TelegramAuthState.WaitingPhoneNumber(TelegramAuthFailure.InvalidPhoneNumber) to
                LoginUiState(LoginStep.PHONE_NUMBER, failure = TelegramAuthFailure.InvalidPhoneNumber),
            TelegramAuthState.WaitingCode(TelegramAuthFailure.InvalidCode) to
                LoginUiState(
                    LoginStep.CODE,
                    failure = TelegramAuthFailure.InvalidCode,
                    codeInfo = TelegramCodeInfo(),
                ),
            TelegramAuthState.WaitingPassword(TelegramAuthFailure.InvalidPassword) to
                LoginUiState(LoginStep.PASSWORD, failure = TelegramAuthFailure.InvalidPassword),
            TelegramAuthState.Authorized(TelegramAuthFailure.RequestRejected(500)) to
                LoginUiState(LoginStep.AUTHORIZED, failure = TelegramAuthFailure.RequestRejected(500)),
            TelegramAuthState.LoggingOut to LoginUiState(LoginStep.LOGGING_OUT),
            TelegramAuthState.Closing to LoginUiState(LoginStep.CLOSING),
            TelegramAuthState.Closed to LoginUiState(LoginStep.INITIALIZING),
            TelegramAuthState.Unsupported(TelegramUnsupportedAuthStep.EMAIL_CODE) to
                LoginUiState(LoginStep.UNSUPPORTED, unsupportedStep = TelegramUnsupportedAuthStep.EMAIL_CODE),
            TelegramAuthState.FatalError(TelegramAuthFailure.DatabaseFailed) to
                LoginUiState(LoginStep.FATAL_ERROR, failure = TelegramAuthFailure.DatabaseFailed),
        )

        cases.forEach { (domain, expected) ->
            repository.emit(domain)
            runCurrent()
            assertEquals(expected, viewModel.uiState.value)
        }
    }

    @Test
    fun `credential configuration clears hash before delegating once`() = runTest {
        val repository = FakeTelegramAuthRepository(
            TelegramAuthState.UnconfiguredCredentials(
                setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
            ),
        )
        val viewModel = AuthViewModel(repository)
        runCurrent()

        viewModel.onCredentialApiIdChanged("12a345")
        viewModel.onCredentialApiHashChanged("0123456789abcdef0123456789abcdef")
        viewModel.configureCredentials()
        runCurrent()
        viewModel.configureCredentials()
        runCurrent()

        assertEquals(
            listOf("12345" to "0123456789abcdef0123456789abcdef"),
            repository.configuredCredentials,
        )
        assertEquals("12345", viewModel.uiState.value.credentialApiId)
        assertEquals("", viewModel.uiState.value.credentialApiHash)
        assertTrue(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `storage failure keeps api id but never restores api hash`() = runTest {
        val repository = FakeTelegramAuthRepository(
            TelegramAuthState.UnconfiguredCredentials(
                setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
            ),
        )
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.onCredentialApiIdChanged("12345")
        viewModel.onCredentialApiHashChanged("0123456789abcdef0123456789abcdef")
        viewModel.configureCredentials()
        runCurrent()

        repository.emit(
            TelegramAuthState.UnconfiguredCredentials(
                invalidKeys = setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
                failure = TelegramAuthFailure.CredentialStorageFailed,
            ),
        )
        runCurrent()

        assertEquals("12345", viewModel.uiState.value.credentialApiId)
        assertEquals("", viewModel.uiState.value.credentialApiHash)
        assertEquals(
            TelegramAuthFailure.CredentialStorageFailed,
            viewModel.uiState.value.failure,
        )
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `code resend follows server timeout and delegates once`() = runTest {
        val info = TelegramCodeInfo(
            deliveryType = TelegramCodeDeliveryType.TELEGRAM_MESSAGE,
            nextDeliveryType = TelegramCodeDeliveryType.SMS,
            resendTimeoutSeconds = 2,
        )
        val repository = FakeTelegramAuthRepository(
            TelegramAuthState.WaitingCode(codeInfo = info),
        )
        val viewModel = AuthViewModel(repository)
        runCurrent()

        assertEquals(2, viewModel.uiState.value.resendSecondsRemaining)
        assertFalse(viewModel.uiState.value.canResendCode)
        viewModel.resendCode()
        runCurrent()
        assertEquals(0, repository.resendCodeCalls)

        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(viewModel.uiState.value.canResendCode)
        viewModel.resendCode()
        runCurrent()
        viewModel.resendCode()
        runCurrent()

        assertEquals(1, repository.resendCodeCalls)
        assertTrue(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `different login steps clear previous input while same phone failure retains it`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingPhoneNumber())
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.onInputChanged("synthetic-phone")

        repository.emit(TelegramAuthState.WaitingPhoneNumber(TelegramAuthFailure.InvalidPhoneNumber))
        runCurrent()
        assertEquals("synthetic-phone", viewModel.uiState.value.input)

        repository.emit(TelegramAuthState.WaitingCode())
        runCurrent()
        assertEquals("", viewModel.uiState.value.input)
    }

    @Test
    fun `phone submission delegates the synthetic snapshot once`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingPhoneNumber())
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.onInputChanged("synthetic-phone")

        viewModel.submit()
        runCurrent()

        assertEquals(listOf("synthetic-phone"), repository.submittedPhoneNumbers)
        assertEquals("synthetic-phone", viewModel.uiState.value.input)
        assertTrue(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `code input is cleared before repository suspension begins`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingCode())
        val viewModel = AuthViewModel(repository)
        var inputAtRepositoryEntry: String? = null
        repository.codeStarted = { inputAtRepositoryEntry = viewModel.uiState.value.input }
        repository.codeGate = CompletableDeferred()
        runCurrent()
        viewModel.onInputChanged("synthetic-code")

        viewModel.submit()
        runCurrent()

        assertEquals(listOf("synthetic-code"), repository.submittedCodes)
        assertEquals("", inputAtRepositoryEntry)
        assertEquals("", viewModel.uiState.value.input)
    }

    @Test
    fun `password input is cleared before repository suspension begins`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingPassword())
        val viewModel = AuthViewModel(repository)
        var inputAtRepositoryEntry: String? = null
        repository.passwordStarted = { inputAtRepositoryEntry = viewModel.uiState.value.input }
        repository.passwordGate = CompletableDeferred()
        runCurrent()
        viewModel.onInputChanged("synthetic-password")

        viewModel.submit()
        runCurrent()

        assertEquals(listOf("synthetic-password"), repository.submittedPasswords)
        assertEquals("", inputAtRepositoryEntry)
        assertEquals("", viewModel.uiState.value.input)
    }

    @Test
    fun `authorized state clears every input reference`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingPhoneNumber())
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.onInputChanged("synthetic-phone")

        repository.emit(TelegramAuthState.Authorized())
        runCurrent()

        assertEquals(LoginStep.AUTHORIZED, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.input)
    }

    @Test
    fun `wrong code and password leave their inputs empty after submission`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingCode())
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.onInputChanged("synthetic-code")
        viewModel.submit()
        runCurrent()
        repository.emit(TelegramAuthState.WaitingCode(TelegramAuthFailure.InvalidCode))
        runCurrent()
        assertEquals("", viewModel.uiState.value.input)

        repository.emit(TelegramAuthState.WaitingPassword())
        runCurrent()
        viewModel.onInputChanged("synthetic-password")
        viewModel.submit()
        runCurrent()
        repository.emit(TelegramAuthState.WaitingPassword(TelegramAuthFailure.InvalidPassword))
        runCurrent()
        assertEquals("", viewModel.uiState.value.input)
    }

    @Test
    fun `flood wait counts down two one zero and replacement cancels old timer`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingCode())
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.onInputChanged("synthetic-code")

        repository.emit(TelegramAuthState.WaitingCode(TelegramAuthFailure.FloodWait(2)))
        runCurrent()
        assertEquals(2, viewModel.uiState.value.retrySecondsRemaining)
        assertFalse(viewModel.uiState.value.canSubmit)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.retrySecondsRemaining)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.retrySecondsRemaining)
        assertTrue(viewModel.uiState.value.canSubmit)

        repository.emit(TelegramAuthState.WaitingCode(TelegramAuthFailure.InvalidCode))
        runCurrent()
        repository.emit(TelegramAuthState.WaitingCode(TelegramAuthFailure.FloodWait(2)))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.retrySecondsRemaining)
        repository.emit(TelegramAuthState.WaitingCode(TelegramAuthFailure.FloodWait(3)))
        runCurrent()
        assertEquals(3, viewModel.uiState.value.retrySecondsRemaining)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, viewModel.uiState.value.retrySecondsRemaining)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.retrySecondsRemaining)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.retrySecondsRemaining)
    }

    @Test
    fun `authorized flood wait disables logout counts down and clears after expiry`() = runTest {
        val repository = FakeTelegramAuthRepository()
        val viewModel = AuthViewModel(repository)
        runCurrent()

        repository.emit(TelegramAuthState.Authorized(TelegramAuthFailure.FloodWait(2)))
        runCurrent()
        assertEquals(2, viewModel.uiState.value.retrySecondsRemaining)
        assertFalse(viewModel.uiState.value.canLogout)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.retrySecondsRemaining)
        assertFalse(viewModel.uiState.value.canLogout)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.retrySecondsRemaining)
        assertNull(viewModel.uiState.value.failure)
        assertTrue(viewModel.uiState.value.canLogout)

        repository.emit(TelegramAuthState.FatalError(TelegramAuthFailure.FloodWait(2)))
        runCurrent()
        assertEquals(0, viewModel.uiState.value.retrySecondsRemaining)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.retrySecondsRemaining)
    }

    @Test
    fun `logout delegates once and remains disabled after quick return`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.Authorized())
        val viewModel = AuthViewModel(repository)
        runCurrent()

        viewModel.logout()
        runCurrent()
        viewModel.logout()
        runCurrent()

        assertEquals(1, repository.logoutCalls)
        assertFalse(viewModel.uiState.value.canLogout)
        assertTrue(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `retry start only delegates after an initial start failure`() = runTest {
        val repository = FakeTelegramAuthRepository().apply {
            startFailureBeforeGate = IllegalStateException("synthetic start failure")
        }
        val viewModel = AuthViewModel(repository)
        runCurrent()

        viewModel.retryStart()
        runCurrent()

        assertEquals(2, repository.startCalls)
    }

    @Test
    fun `retry start remains submitting and ignores a duplicate while start is pending`() = runTest {
        val repository = FakeTelegramAuthRepository().apply {
            startFailureBeforeGate = IllegalStateException("synthetic initial failure")
        }
        val viewModel = AuthViewModel(repository)
        runCurrent()
        repository.startFailureBeforeGate = null
        repository.startGate = CompletableDeferred()

        viewModel.retryStart()
        runCurrent()
        viewModel.retryStart()
        runCurrent()

        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(TelegramAuthFailure.Unknown, viewModel.uiState.value.failure)
        assertEquals(2, repository.startCalls)
    }

    @Test
    fun `retry start ignores initialization without a start failure`() = runTest {
        val repository = FakeTelegramAuthRepository()
        val viewModel = AuthViewModel(repository)
        runCurrent()

        viewModel.retryStart()
        runCurrent()

        assertEquals(1, repository.startCalls)
    }

    @Test
    fun `cancellation is not converted into a failure`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingPhoneNumber())
        val viewModel = AuthViewModel(repository)
        repository.phoneFailure = CancellationException("synthetic cancellation")
        runCurrent()
        viewModel.onInputChanged("synthetic-phone")

        viewModel.submit()
        runCurrent()

        assertNull(viewModel.uiState.value.failure)
        assertTrue(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `ordinary submission failure is sanitized and clears submitting`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingPhoneNumber())
        val viewModel = AuthViewModel(repository)
        repository.phoneFailure = IllegalStateException("must not reach UI")
        runCurrent()
        viewModel.onInputChanged("synthetic-phone")

        viewModel.submit()
        runCurrent()

        assertEquals(TelegramAuthFailure.Unknown, viewModel.uiState.value.failure)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `late phone failure cannot overwrite a newer suspended code submission`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.WaitingPhoneNumber())
        repository.phoneGate = CompletableDeferred()
        repository.codeGate = CompletableDeferred()
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.onInputChanged("synthetic-phone")
        viewModel.submit()
        runCurrent()

        repository.emit(TelegramAuthState.WaitingCode())
        runCurrent()
        viewModel.onInputChanged("synthetic-code")
        viewModel.submit()
        runCurrent()
        repository.phoneFailureAfterGate = IllegalStateException("late phone failure")
        repository.phoneGate?.complete(Unit)
        runCurrent()

        assertEquals(LoginStep.CODE, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.input)
        assertNull(viewModel.uiState.value.failure)
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.submit()
        runCurrent()
        assertEquals(listOf("synthetic-code"), repository.submittedCodes)
    }

    @Test
    fun `late logout failure cannot overwrite a newer closing state`() = runTest {
        val repository = FakeTelegramAuthRepository(TelegramAuthState.Authorized())
        repository.logoutGate = CompletableDeferred()
        val viewModel = AuthViewModel(repository)
        runCurrent()
        viewModel.logout()
        runCurrent()

        repository.emit(TelegramAuthState.Closing)
        runCurrent()
        repository.logoutFailureAfterGate = IllegalStateException("late logout failure")
        repository.logoutGate?.complete(Unit)
        runCurrent()

        assertEquals(LoginUiState(LoginStep.CLOSING), viewModel.uiState.value)
    }

    @Test
    fun `late start failure cannot overwrite a newer suspended phone submission`() = runTest {
        val repository = FakeTelegramAuthRepository()
        repository.startGate = CompletableDeferred()
        repository.phoneGate = CompletableDeferred()
        val viewModel = AuthViewModel(repository)
        runCurrent()

        repository.emit(TelegramAuthState.WaitingPhoneNumber())
        runCurrent()
        viewModel.onInputChanged("synthetic-phone")
        viewModel.submit()
        runCurrent()
        repository.startFailureAfterGate = IllegalStateException("late start failure")
        repository.startGate?.complete(Unit)
        runCurrent()

        assertEquals(LoginStep.PHONE_NUMBER, viewModel.uiState.value.step)
        assertEquals("synthetic-phone", viewModel.uiState.value.input)
        assertNull(viewModel.uiState.value.failure)
        assertTrue(viewModel.uiState.value.isSubmitting)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher(), TestRule {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }

    override fun apply(base: Statement, description: Description): Statement =
        super.apply(base, description)
}
