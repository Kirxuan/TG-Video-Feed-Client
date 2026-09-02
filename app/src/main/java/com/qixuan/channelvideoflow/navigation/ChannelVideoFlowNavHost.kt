package com.qixuan.channelvideoflow.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.qixuan.channelvideoflow.feature.auth.AuthViewModel
import com.qixuan.channelvideoflow.feature.auth.LoginScreen
import com.qixuan.channelvideoflow.feature.auth.LoginStep
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionRoute
import com.qixuan.channelvideoflow.feature.settings.CacheSettingsRoute
import com.qixuan.channelvideoflow.feature.tags.TagFilterRoute
import com.qixuan.channelvideoflow.feature.video.VideoPlaybackRoute
import com.qixuan.channelvideoflow.model.video.VideoFilter

@Composable
@UnstableApi
fun ChannelVideoFlowNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(AuthorizedDestination.CHANNELS) }
    var playbackFilter by remember { mutableStateOf<VideoFilter?>(null) }
    LaunchedEffect(authUiState.step) {
        if (authUiState.step != LoginStep.AUTHORIZED) {
            destination = AuthorizedDestination.CHANNELS
            playbackFilter = null
        }
    }
    BackHandler(
        enabled = authUiState.step == LoginStep.AUTHORIZED &&
            destination != AuthorizedDestination.CHANNELS,
    ) {
        destination = when (destination) {
            AuthorizedDestination.FEED -> AuthorizedDestination.TAGS
            AuthorizedDestination.TAGS,
            AuthorizedDestination.SETTINGS,
            -> AuthorizedDestination.CHANNELS
            AuthorizedDestination.CHANNELS -> AuthorizedDestination.CHANNELS
        }
    }
    if (authUiState.step == LoginStep.AUTHORIZED) {
        when (destination) {
            AuthorizedDestination.SETTINGS -> CacheSettingsRoute(
                onBack = { destination = AuthorizedDestination.CHANNELS },
                onLogout = {
                    destination = AuthorizedDestination.CHANNELS
                    authViewModel.logout()
                },
            )
            AuthorizedDestination.TAGS -> TagFilterRoute(
                onBack = { destination = AuthorizedDestination.CHANNELS },
                onContinue = { filter ->
                    playbackFilter = filter
                    destination = AuthorizedDestination.FEED
                },
            )
            AuthorizedDestination.FEED -> VideoPlaybackRoute(
                initialFilter = playbackFilter,
                onBack = { destination = AuthorizedDestination.TAGS },
                onLogout = {
                    destination = AuthorizedDestination.CHANNELS
                    playbackFilter = null
                    authViewModel.logout()
                },
            )
            AuthorizedDestination.CHANNELS -> ChannelSelectionRoute(
                onLogout = authViewModel::logout,
                onOpenPlayback = { destination = AuthorizedDestination.TAGS },
                onOpenCacheSettings = { destination = AuthorizedDestination.SETTINGS },
                logoutEnabled = authUiState.canLogout,
            )
        }
    } else {
        LoginScreen(
            uiState = authUiState,
            onInputChanged = authViewModel::onInputChanged,
            onCredentialApiIdChanged = authViewModel::onCredentialApiIdChanged,
            onCredentialApiHashChanged = authViewModel::onCredentialApiHashChanged,
            onConfigureCredentials = authViewModel::configureCredentials,
            onSubmit = authViewModel::submit,
            onResendCode = authViewModel::resendCode,
            onRetry = authViewModel::retryStart,
            onLogout = authViewModel::logout,
        )
    }
}

private enum class AuthorizedDestination {
    CHANNELS,
    TAGS,
    FEED,
    SETTINGS,
}
