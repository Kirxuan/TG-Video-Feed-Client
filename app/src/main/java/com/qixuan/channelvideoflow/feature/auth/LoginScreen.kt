package com.qixuan.channelvideoflow.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qixuan.channelvideoflow.R
import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.model.auth.TelegramCodeDeliveryType
import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep
import com.qixuan.channelvideoflow.ui.components.GlossCard
import com.qixuan.channelvideoflow.ui.components.PremiumBackdrop
import com.qixuan.channelvideoflow.ui.components.PrimaryActionState
import com.qixuan.channelvideoflow.ui.components.StatefulPrimaryButton
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTokens

internal object LoginTestTags {
    const val BrandName = "login-brand-name"
    const val Creator = "login-creator"
    const val CredentialApiId = "login-credential-api-id"
    const val CredentialApiHash = "login-credential-api-hash"
    const val ConfigureCredentials = "login-configure-credentials"
    const val Input = "login-input"
    const val PasswordInput = "login-password-input"
    const val Submit = "login-submit"
    const val Logout = "login-logout"
    const val Retry = "login-retry"
    const val ResendCode = "login-resend-code"
    const val Progress = "login-progress"
    const val Stepper = "login-stepper"
}

@Composable
fun LoginRoute(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        uiState = uiState,
        onInputChanged = viewModel::onInputChanged,
        onCredentialApiIdChanged = viewModel::onCredentialApiIdChanged,
        onCredentialApiHashChanged = viewModel::onCredentialApiHashChanged,
        onConfigureCredentials = viewModel::configureCredentials,
        onSubmit = viewModel::submit,
        onResendCode = viewModel::resendCode,
        onRetry = viewModel::retryStart,
        onLogout = viewModel::logout,
    )
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onInputChanged: (String) -> Unit,
    onCredentialApiIdChanged: (String) -> Unit = {},
    onCredentialApiHashChanged: (String) -> Unit = {},
    onConfigureCredentials: () -> Unit = {},
    onSubmit: () -> Unit,
    onResendCode: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    PremiumBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.brand_name),
                        modifier = Modifier.testTag(LoginTestTags.BrandName),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.brand_slogan),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.creator_credit,
                            stringResource(R.string.creator_name),
                        ),
                        modifier = Modifier.testTag(LoginTestTags.Creator),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                AuthorizationStepper(uiState.step)

                GlossCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ChannelVideoFlowTokens.Shapes.hero,
                    isError = uiState.failure != null || uiState.step == LoginStep.FATAL_ERROR,
                    stateDescription = loginCardStateDescription(uiState),
                ) {
                    AnimatedContent(
                        targetState = uiState.step,
                        transitionSpec = {
                            (fadeIn(tween(ChannelVideoFlowTokens.Motion.contentEnterMillis)) +
                                slideInVertically(
                                    animationSpec = tween(
                                        ChannelVideoFlowTokens.Motion.contentEnterMillis,
                                    ),
                                    initialOffsetY = { height -> height / 12 },
                                )) togetherWith
                                (fadeOut(tween(ChannelVideoFlowTokens.Motion.stateChangeMillis)) +
                                    slideOutVertically(
                                        animationSpec = tween(
                                            ChannelVideoFlowTokens.Motion.stateChangeMillis,
                                        ),
                                        targetOffsetY = { height -> -height / 12 },
                                    ))
                        },
                        label = "authorization step",
                    ) { step ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(
                                ChannelVideoFlowTokens.Spacing.medium,
                            ),
                        ) {
                            when (step) {
                            LoginStep.UNCONFIGURED -> UnconfiguredContent(
                                uiState = uiState,
                                onApiIdChanged = onCredentialApiIdChanged,
                                onApiHashChanged = onCredentialApiHashChanged,
                                onConfigure = onConfigureCredentials,
                            )
                            LoginStep.INITIALIZING -> {
                                if (uiState.failure == null) {
                                    ProgressContent(R.string.login_initializing)
                                } else {
                                    InitialStartFailureContent(uiState, onRetry)
                                }
                            }
                            LoginStep.PHONE_NUMBER,
                            LoginStep.CODE,
                            LoginStep.PASSWORD,
                            -> InputContent(uiState, onInputChanged, onSubmit, onResendCode)
                            LoginStep.AUTHORIZED -> AuthorizedContent(uiState, onLogout)
                            LoginStep.LOGGING_OUT -> ProgressContent(R.string.login_logging_out)
                            LoginStep.CLOSING -> ProgressContent(R.string.login_closing)
                            LoginStep.UNSUPPORTED -> Text(
                                text = stringResource(uiState.unsupportedStep.toUnsupportedMessageRes()),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            LoginStep.FATAL_ERROR -> FailureText(
                                failure = uiState.failure ?: TelegramAuthFailure.Unknown,
                                retrySecondsRemaining = uiState.retrySecondsRemaining,
                            )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.login_private_storage_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AuthorizationStepper(step: LoginStep) {
    val currentIndex = when (step) {
        LoginStep.UNCONFIGURED -> 0
        LoginStep.PHONE_NUMBER -> 1
        LoginStep.CODE -> 2
        LoginStep.PASSWORD -> 3
        LoginStep.AUTHORIZED,
        LoginStep.LOGGING_OUT,
        LoginStep.CLOSING,
        -> 4
        else -> 0
    }
    val labels = listOf("API", "账号", "验证", "安全")
    val semanticLabels = listOf("API 参数", "手机号", "验证码", "两步验证密码")
    val stateLabel = if (currentIndex >= labels.size) {
        "授权已完成"
    } else {
        "当前步骤：${semanticLabels[currentIndex]}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.Stepper)
            .semantics { stateDescription = stateLabel },
        horizontalArrangement = Arrangement.spacedBy(ChannelVideoFlowTokens.Spacing.small),
    ) {
        labels.forEachIndexed { index, label ->
            val completed = currentIndex > index
            val active = currentIndex == index
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ChannelVideoFlowTokens.Spacing.xSmall),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = when {
                                completed -> MaterialTheme.colorScheme.tertiary
                                active -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (completed) "✓" else "${index + 1}",
                        color = if (completed || active) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = label,
                    color = if (active || completed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun loginCardStateDescription(uiState: LoginUiState): String = when {
    uiState.isSubmitting -> "授权请求处理中"
    uiState.failure != null -> "授权发生错误"
    uiState.step == LoginStep.AUTHORIZED -> "Telegram 授权成功"
    else -> "Telegram 授权步骤"
}

@Composable
private fun UnconfiguredContent(
    uiState: LoginUiState,
    onApiIdChanged: (String) -> Unit,
    onApiHashChanged: (String) -> Unit,
    onConfigure: () -> Unit,
) {
    Text(
        text = stringResource(R.string.login_unconfigured),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        text = stringResource(R.string.login_credentials_instructions),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = uiState.credentialApiId,
        onValueChange = onApiIdChanged,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.CredentialApiId),
        enabled = !uiState.isSubmitting,
        isError = "TELEGRAM_API_ID" in uiState.invalidKeys,
        label = { Text(stringResource(R.string.login_credential_api_id)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ),
        singleLine = true,
        shape = ChannelVideoFlowTokens.Shapes.control,
    )
    OutlinedTextField(
        value = uiState.credentialApiHash,
        onValueChange = onApiHashChanged,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.CredentialApiHash),
        enabled = !uiState.isSubmitting,
        isError = "TELEGRAM_API_HASH" in uiState.invalidKeys,
        label = { Text(stringResource(R.string.login_credential_api_hash)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
        ),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = ChannelVideoFlowTokens.Shapes.control,
    )
    if (uiState.invalidKeys.isNotEmpty()) {
        Text(
            text = stringResource(R.string.login_credentials_invalid),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    FailureText(uiState.failure, uiState.retrySecondsRemaining)
    StatefulPrimaryButton(
        text = stringResource(R.string.login_credentials_save),
        onClick = onConfigure,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.ConfigureCredentials),
        enabled = uiState.canConfigureCredentials || uiState.isSubmitting,
        state = when {
            uiState.isSubmitting -> PrimaryActionState.Loading
            uiState.failure != null || uiState.invalidKeys.isNotEmpty() -> PrimaryActionState.Error
            else -> PrimaryActionState.Idle
        },
        stateText = if (uiState.isSubmitting) {
            stringResource(R.string.login_credentials_saving)
        } else {
            null
        },
        progressIndicatorModifier = Modifier.testTag(LoginTestTags.Progress),
    )
}

@Composable
private fun InputContent(
    uiState: LoginUiState,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResendCode: () -> Unit,
) {
    val labelRes = when (uiState.step) {
        LoginStep.PHONE_NUMBER -> R.string.login_phone_number
        LoginStep.CODE -> R.string.login_code
        LoginStep.PASSWORD -> R.string.login_password
        else -> error("Input content requires an editable authorization step")
    }
    val keyboardType = when (uiState.step) {
        LoginStep.PHONE_NUMBER -> androidx.compose.ui.text.input.KeyboardType.Phone
        LoginStep.CODE -> androidx.compose.ui.text.input.KeyboardType.NumberPassword
        LoginStep.PASSWORD -> androidx.compose.ui.text.input.KeyboardType.Password
    }
    val visualTransformation = if (uiState.step == LoginStep.PASSWORD) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }
    val inputTag = if (uiState.step == LoginStep.PASSWORD) {
        LoginTestTags.PasswordInput
    } else {
        LoginTestTags.Input
    }

    if (uiState.step == LoginStep.CODE) {
        CodeDeliveryContent(uiState, onResendCode)
    }
    OutlinedTextField(
        value = uiState.input,
        onValueChange = onInputChanged,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(inputTag),
        enabled = !uiState.isSubmitting,
        isError = uiState.failure != null,
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = ChannelVideoFlowTokens.Shapes.control,
    )
    FailureText(uiState.failure, uiState.retrySecondsRemaining)
    StatefulPrimaryButton(
        text = stringResource(R.string.login_submit),
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.Submit),
        enabled = uiState.canSubmit || uiState.isSubmitting,
        state = when {
            uiState.isSubmitting -> PrimaryActionState.Loading
            uiState.failure != null -> PrimaryActionState.Error
            else -> PrimaryActionState.Idle
        },
        stateText = if (uiState.isSubmitting) "正在提交" else null,
        progressIndicatorModifier = Modifier.testTag(LoginTestTags.Progress),
    )
}

@Composable
private fun CodeDeliveryContent(
    uiState: LoginUiState,
    onResendCode: () -> Unit,
) {
    val codeInfo = uiState.codeInfo ?: return
    Text(
        text = stringResource(
            R.string.login_code_sent_to,
            stringResource(codeInfo.deliveryType.toDeliveryLabelRes()),
        ),
        style = MaterialTheme.typography.bodyLarge,
    )

    val nextDeliveryType = codeInfo.nextDeliveryType ?: return
    val nextLabel = stringResource(nextDeliveryType.toDeliveryLabelRes())
    val nextMessage = if (uiState.resendSecondsRemaining > 0) {
        stringResource(
            R.string.login_code_resend_countdown,
            uiState.resendSecondsRemaining,
            nextLabel,
        )
    } else {
        stringResource(R.string.login_code_resend_available, nextLabel)
    }
    Text(text = nextMessage, style = MaterialTheme.typography.bodyMedium)
    Button(
        onClick = onResendCode,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag(LoginTestTags.ResendCode),
        enabled = uiState.canResendCode,
    ) {
        Text(stringResource(R.string.login_code_resend))
    }
}

@Composable
private fun AuthorizedContent(
    uiState: LoginUiState,
    onLogout: () -> Unit,
) {
    Text(
        text = stringResource(R.string.login_authorized),
        style = MaterialTheme.typography.titleLarge,
    )
    FailureText(uiState.failure, uiState.retrySecondsRemaining)
    StatefulPrimaryButton(
        text = stringResource(R.string.login_logout),
        onClick = onLogout,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.Logout),
        enabled = uiState.canLogout || uiState.isSubmitting,
        state = if (uiState.isSubmitting) {
            PrimaryActionState.Loading
        } else {
            PrimaryActionState.Success
        },
        stateText = if (uiState.isSubmitting) {
            stringResource(R.string.login_logging_out)
        } else {
            null
        },
        progressIndicatorModifier = Modifier.testTag(LoginTestTags.Progress),
    )
}

@Composable
private fun InitialStartFailureContent(
    uiState: LoginUiState,
    onRetry: () -> Unit,
) {
    FailureText(
        failure = uiState.failure ?: TelegramAuthFailure.Unknown,
        retrySecondsRemaining = uiState.retrySecondsRemaining,
    )
    StatefulPrimaryButton(
        text = stringResource(R.string.login_retry),
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LoginTestTags.Retry),
        enabled = true,
        state = if (uiState.isSubmitting) {
            PrimaryActionState.Loading
        } else {
            PrimaryActionState.Error
        },
        stateText = if (uiState.isSubmitting) "正在重试" else null,
        progressIndicatorModifier = Modifier.testTag(LoginTestTags.Progress),
    )
}

@Composable
private fun ProgressContent(messageRes: Int) {
    Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)
    ProgressIndicator()
}

@Composable
private fun ProgressIndicator() {
    CircularProgressIndicator(modifier = Modifier.testTag(LoginTestTags.Progress))
}

@Composable
private fun FailureText(
    failure: TelegramAuthFailure?,
    retrySecondsRemaining: Int,
) {
    val failureRes = failure.toFailureMessageRes()
    if (failureRes != null) {
        val text = if (failure is TelegramAuthFailure.FloodWait) {
            stringResource(R.string.login_flood_wait, retrySecondsRemaining)
        } else if (failure is TelegramAuthFailure.RequestRejected) {
            stringResource(R.string.login_request_rejected, failure.code)
        } else {
            stringResource(failureRes)
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun TelegramAuthFailure?.toFailureMessageRes(): Int? = when (this) {
    TelegramAuthFailure.InvalidApiCredentials -> R.string.login_invalid_api_credentials
    TelegramAuthFailure.InvalidPhoneNumber -> R.string.login_invalid_phone
    TelegramAuthFailure.InvalidCode -> R.string.login_invalid_code
    TelegramAuthFailure.InvalidPassword -> R.string.login_invalid_password
    is TelegramAuthFailure.FloodWait -> R.string.login_flood_wait
    TelegramAuthFailure.NetworkUnavailable -> R.string.login_network_error
    TelegramAuthFailure.NativeLibraryLoadFailed -> R.string.login_native_library_error
    TelegramAuthFailure.TdLibInitializationFailed -> R.string.login_tdlib_initialization_error
    TelegramAuthFailure.DatabaseFailed -> R.string.login_database_error
    TelegramAuthFailure.CredentialStorageFailed -> R.string.login_credential_storage_error
    is TelegramAuthFailure.RequestRejected -> R.string.login_request_rejected
    TelegramAuthFailure.Unknown -> R.string.login_unknown_error
    null -> null
}

private fun TelegramUnsupportedAuthStep?.toUnsupportedMessageRes(): Int = when (this) {
    TelegramUnsupportedAuthStep.REGISTRATION -> R.string.login_registration_unsupported
    else -> R.string.login_unsupported
}

private fun TelegramCodeDeliveryType.toDeliveryLabelRes(): Int = when (this) {
    TelegramCodeDeliveryType.TELEGRAM_MESSAGE -> R.string.login_code_delivery_telegram
    TelegramCodeDeliveryType.SMS -> R.string.login_code_delivery_sms
    TelegramCodeDeliveryType.SMS_WORD -> R.string.login_code_delivery_sms_word
    TelegramCodeDeliveryType.SMS_PHRASE -> R.string.login_code_delivery_sms_phrase
    TelegramCodeDeliveryType.PHONE_CALL -> R.string.login_code_delivery_call
    TelegramCodeDeliveryType.FLASH_CALL -> R.string.login_code_delivery_flash_call
    TelegramCodeDeliveryType.MISSED_CALL -> R.string.login_code_delivery_missed_call
    TelegramCodeDeliveryType.FRAGMENT -> R.string.login_code_delivery_fragment
    TelegramCodeDeliveryType.FIREBASE_ANDROID -> R.string.login_code_delivery_firebase_android
    TelegramCodeDeliveryType.FIREBASE_IOS -> R.string.login_code_delivery_firebase_ios
    TelegramCodeDeliveryType.UNKNOWN -> R.string.login_code_delivery_unknown
}
