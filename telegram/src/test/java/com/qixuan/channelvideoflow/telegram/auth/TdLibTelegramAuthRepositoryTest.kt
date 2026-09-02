package com.qixuan.channelvideoflow.telegram.auth

import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.model.auth.TelegramAuthState
import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep
import com.qixuan.channelvideoflow.telegram.client.FatalCategory
import com.qixuan.channelvideoflow.telegram.client.TelegramAuthRequest
import com.qixuan.channelvideoflow.telegram.client.TelegramClientAuthorizationState
import com.qixuan.channelvideoflow.telegram.client.TelegramClientEvent
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentials
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsResult
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsStore
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsUnavailableReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdLibTelegramAuthRepositoryTest {
    @Test
    fun initialStateIsInitializingAndExposedStateIsReadOnly() = runTest {
        val repository = repository(FakeTelegramAuthClient())

        assertEquals(TelegramAuthState.Initializing, repository.authState.value)
        assertFalse(repository.authState is kotlinx.coroutines.flow.MutableStateFlow<*>)
    }

    @Test
    fun startInstallsCollectorBeforeStartingClient() = runTest {
        val client = FakeTelegramAuthClient().apply {
            eventToEmitOnStart = TelegramClientEvent.AuthorizationStateChanged(
                TelegramClientAuthorizationState.WaitPhoneNumber,
            )
        }
        val repository = repository(client)

        repository.start()
        runCurrent()

        assertEquals(listOf(1), client.subscriberCountsAtStart)
        assertEquals(TelegramAuthState.WaitingPhoneNumber(), repository.authState.value)
    }

    @Test
    fun repeatedStartIsIdempotent() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = repository(client)

        repository.start()
        repository.start()
        repository.start()
        runCurrent()

        assertEquals(1, client.startCalls)
        assertEquals(listOf(1), client.subscriberCountsAtStart)
    }

    @Test
    fun credentialsUnavailableCopiesExactInvalidKeyNames() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        val mutableKeys = linkedSetOf("TELEGRAM_API_HASH", "TELEGRAM_API_ID")

        client.emit(TelegramClientEvent.CredentialsUnavailable(mutableKeys))
        runCurrent()
        mutableKeys.clear()

        assertEquals(
            TelegramAuthState.UnconfiguredCredentials(
                setOf("TELEGRAM_API_HASH", "TELEGRAM_API_ID"),
            ),
            repository.authState.value,
        )
    }

    @Test
    fun storageFailureIsRecoverableOnCredentialConfigurationScreen() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)

        client.emit(
            TelegramClientEvent.CredentialsUnavailable(
                invalidKeys = setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
                reason = TelegramCredentialsUnavailableReason.SECURE_STORAGE,
            ),
        )
        runCurrent()

        assertEquals(
            TelegramAuthState.UnconfiguredCredentials(
                invalidKeys = setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
                failure = TelegramAuthFailure.CredentialStorageFailed,
            ),
            repository.authState.value,
        )
    }

    @Test
    fun validConfiguredCredentialsAreSavedBeforeClientStartsAgain() = runTest {
        val client = FakeTelegramAuthClient()
        val store = RecordingCredentialsStore(
            TelegramCredentialsResult.Available(
                TelegramCredentials(12345, "0123456789abcdef0123456789abcdef"),
            ),
        )
        val repository = repository(client, store)
        repository.start()
        runCurrent()

        repository.configureCredentials("12345", "0123456789abcdef0123456789abcdef")
        runCurrent()

        assertEquals(1, store.saveCalls)
        assertEquals(1, client.startCalls)
        assertEquals(1, client.restartAfterCredentialsChangedCalls)
        assertEquals(TelegramAuthState.Initializing, repository.authState.value)
    }

    @Test
    fun invalidConfiguredCredentialsRemainOnConfigurationScreen() = runTest {
        val client = FakeTelegramAuthClient()
        val store = RecordingCredentialsStore(
            TelegramCredentialsResult.Unavailable(setOf("TELEGRAM_API_HASH")),
        )
        val repository = repository(client, store)

        repository.configureCredentials("12345", "invalid")

        assertEquals(1, store.saveCalls)
        assertEquals(0, client.startCalls)
        assertEquals(
            TelegramAuthState.UnconfiguredCredentials(setOf("TELEGRAM_API_HASH")),
            repository.authState.value,
        )
    }

    @Test
    fun rejectedTdLibParametersReturnToCredentialConfiguration() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)

        client.emit(
            TelegramClientEvent.RequestFailed(
                TelegramAuthRequest.PARAMETERS,
                401,
                "synthetic rejected credentials",
            ),
        )
        runCurrent()

        assertEquals(
            TelegramAuthState.UnconfiguredCredentials(
                invalidKeys = setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
                failure = TelegramAuthFailure.InvalidApiCredentials,
            ),
            repository.authState.value,
        )
    }

    @Test
    fun mapsPhoneCodePasswordAndReadyStatesExactly() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        val cases = listOf(
            TelegramClientAuthorizationState.WaitPhoneNumber to
                TelegramAuthState.WaitingPhoneNumber(),
            TelegramClientAuthorizationState.WaitCode() to TelegramAuthState.WaitingCode(),
            TelegramClientAuthorizationState.WaitPassword to TelegramAuthState.WaitingPassword(),
            TelegramClientAuthorizationState.Ready to TelegramAuthState.Authorized(),
        )

        cases.forEach { (clientState, expected) ->
            client.emitAuthorizationState(clientState)
            runCurrent()
            assertEquals(expected, repository.authState.value)
        }
    }

    @Test
    fun wrongCodeRemainsWaitingForCodeWithSanitizedFailure() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        client.emitAuthorizationState(TelegramClientAuthorizationState.WaitCode())
        runCurrent()

        client.emit(
            TelegramClientEvent.RequestFailed(
                TelegramAuthRequest.CODE,
                400,
                "PHONE_CODE_INVALID",
            ),
        )
        runCurrent()

        assertEquals(
            TelegramAuthState.WaitingCode(TelegramAuthFailure.InvalidCode),
            repository.authState.value,
        )
    }

    @Test
    fun wrongPasswordRemainsWaitingForPasswordWithSanitizedFailure() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        client.emitAuthorizationState(TelegramClientAuthorizationState.WaitPassword)
        runCurrent()

        client.emit(
            TelegramClientEvent.RequestFailed(
                TelegramAuthRequest.PASSWORD,
                400,
                "PASSWORD_HASH_INVALID",
            ),
        )
        runCurrent()

        assertEquals(
            TelegramAuthState.WaitingPassword(TelegramAuthFailure.InvalidPassword),
            repository.authState.value,
        )
    }

    @Test
    fun floodWaitStaysOnMatchingCurrentInputOnly() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        client.emitAuthorizationState(TelegramClientAuthorizationState.WaitCode())
        runCurrent()

        client.emit(
            TelegramClientEvent.RequestFailed(
                TelegramAuthRequest.CODE,
                429,
                "FLOOD_WAIT_45",
            ),
        )
        runCurrent()
        assertEquals(
            TelegramAuthState.WaitingCode(TelegramAuthFailure.FloodWait(45)),
            repository.authState.value,
        )

        client.emit(
            TelegramClientEvent.RequestFailed(
                TelegramAuthRequest.PASSWORD,
                429,
                "FLOOD_WAIT_99",
            ),
        )
        runCurrent()
        assertEquals(
            TelegramAuthState.WaitingCode(TelegramAuthFailure.FloodWait(45)),
            repository.authState.value,
        )
    }

    @Test
    fun unsupportedStateNeverBecomesAuthorized() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)

        client.emitAuthorizationState(
            TelegramClientAuthorizationState.Unsupported(TelegramUnsupportedAuthStep.EMAIL_CODE),
        )
        runCurrent()

        assertEquals(
            TelegramAuthState.Unsupported(TelegramUnsupportedAuthStep.EMAIL_CODE),
            repository.authState.value,
        )
        assertFalse(repository.authState.value is TelegramAuthState.Authorized)
    }

    @Test
    fun loggingOutClosingAndClosedAreObservableInOrder() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        val observed = mutableListOf<TelegramAuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.authState.collect(observed::add)
        }

        client.emitAuthorizationState(TelegramClientAuthorizationState.LoggingOut)
        runCurrent()
        client.emitAuthorizationState(TelegramClientAuthorizationState.Closing)
        runCurrent()
        client.emitAuthorizationState(TelegramClientAuthorizationState.Closed)
        runCurrent()

        assertEquals(
            listOf(
                TelegramAuthState.LoggingOut,
                TelegramAuthState.Closing,
                TelegramAuthState.Closed,
            ),
            observed.takeLast(3),
        )
    }

    @Test
    fun requestedLogoutRestartsOnceAfterClosedThenReturnsToLogin() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        client.emitAuthorizationState(TelegramClientAuthorizationState.Ready)
        runCurrent()

        repository.logout()
        client.emitAuthorizationState(TelegramClientAuthorizationState.LoggingOut)
        runCurrent()
        client.emitAuthorizationState(TelegramClientAuthorizationState.Closing)
        runCurrent()
        client.emitAuthorizationState(TelegramClientAuthorizationState.Closed)
        runCurrent()

        assertEquals(1, client.logoutCalls)
        assertEquals(2, client.startCalls)
        assertEquals(TelegramAuthState.Closed, repository.authState.value)

        client.emitAuthorizationState(TelegramClientAuthorizationState.WaitPhoneNumber)
        runCurrent()
        assertEquals(TelegramAuthState.WaitingPhoneNumber(), repository.authState.value)
        assertEquals(2, client.startCalls)
    }

    @Test
    fun closedWithoutRequestedLogoutDoesNotRestartClient() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)

        client.emitAuthorizationState(TelegramClientAuthorizationState.Closed)
        runCurrent()

        assertEquals(TelegramAuthState.Closed, repository.authState.value)
        assertEquals(1, client.startCalls)
    }

    @Test
    fun logoutFailureReturnsAuthorizedWithFailureAndDoesNotRestartOnClosed() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = startedRepository(client)
        client.emitAuthorizationState(TelegramClientAuthorizationState.Ready)
        runCurrent()

        repository.logout()
        client.emit(
            TelegramClientEvent.RequestFailed(
                TelegramAuthRequest.LOG_OUT,
                500,
                "synthetic server rejection",
            ),
        )
        runCurrent()

        assertEquals(
            TelegramAuthState.Authorized(TelegramAuthFailure.RequestRejected(500)),
            repository.authState.value,
        )

        client.emitAuthorizationState(TelegramClientAuthorizationState.Closed)
        runCurrent()
        assertEquals(1, client.startCalls)
    }

    @Test
    fun submitMethodsDelegateEachSyntheticInputExactlyOnce() = runTest {
        val client = FakeTelegramAuthClient()
        val repository = repository(client)

        repository.submitPhoneNumber("synthetic-phone")
        repository.submitCode("synthetic-code")
        repository.resendCode()
        repository.submitPassword("synthetic-password")

        assertEquals(listOf("synthetic-phone"), client.submittedPhoneNumbers)
        assertEquals(listOf("synthetic-code"), client.submittedCodes)
        assertEquals(1, client.resendCodeCalls)
        assertEquals(listOf("synthetic-password"), client.submittedPasswords)
    }

    @Test
    fun fatalClientCategoriesMapToExactDomainFailures() = runTest {
        val cases = listOf(
            FatalCategory.NATIVE_LIBRARY to TelegramAuthFailure.NativeLibraryLoadFailed,
            FatalCategory.INITIALIZATION to TelegramAuthFailure.TdLibInitializationFailed,
            FatalCategory.DATABASE to TelegramAuthFailure.DatabaseFailed,
        )

        cases.forEach { (category, expectedFailure) ->
            val client = FakeTelegramAuthClient()
            val repository = startedRepository(client)

            client.emit(TelegramClientEvent.FatalFailure(category))
            runCurrent()

            assertEquals(TelegramAuthState.FatalError(expectedFailure), repository.authState.value)
        }
    }

    private fun TestScope.repository(
        client: FakeTelegramAuthClient,
        credentialsStore: TelegramCredentialsStore = RecordingCredentialsStore(),
    ): TdLibTelegramAuthRepository = TdLibTelegramAuthRepository(
        client,
        credentialsStore,
        backgroundScope,
    )

    private suspend fun TestScope.startedRepository(
        client: FakeTelegramAuthClient,
    ): TdLibTelegramAuthRepository = repository(client).also {
        it.start()
        runCurrent()
    }
}

private class RecordingCredentialsStore(
    private val result: TelegramCredentialsResult = TelegramCredentialsResult.Unavailable(
        setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
    ),
) : TelegramCredentialsStore {
    var saveCalls = 0
        private set

    override suspend fun save(apiId: String, apiHash: String): TelegramCredentialsResult {
        saveCalls += 1
        return result
    }
}
