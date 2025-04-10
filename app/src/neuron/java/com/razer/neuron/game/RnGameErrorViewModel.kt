package com.razer.neuron.game

import android.app.Application
import android.content.Intent
import android.util.Size
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.cache.CacheBuilder
import com.limelight.Game
import com.limelight.NeuronBridge
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.toResolution
import com.limelight.nvstream.http.toResolutionString
import com.razer.neuron.common.debugToast
import timber.log.Timber


import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.di.UnexpectedExceptionHandler
import com.razer.neuron.extensions.connectivityManager
import com.razer.neuron.extensions.getFullScreenSize
import com.razer.neuron.extensions.standardResolutions
import com.razer.neuron.game.RnGameError.Companion.EXTRA_LAST_STREAM_ERROR_TIMESTAMP
import com.razer.neuron.game.RnGameError.Companion.EXTRA_RECOVERY_COUNT
import com.razer.neuron.game.helpers.RnGameIntentHelper
import com.razer.neuron.game.helpers.RnStreamError
import com.razer.neuron.main.findActiveAddress
import com.razer.neuron.main.isAutoRestartSupported
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.shared.SharedConstants
import com.razer.neuron.utils.getDeviceNickName
import com.razer.neuron.utils.getStringExt
import com.razer.neuron.utils.now
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.Throws

/**
 * See NEUR-159
 * See NEUR-158
 * See NEUR-157
 */
@HiltViewModel
class RnGameErrorViewModel
@Inject constructor(
    private val application: Application,
    @UnexpectedExceptionHandler val unexpectedExceptionHandler: CoroutineExceptionHandler,
    @IoDispatcher val ioDispatcher : CoroutineDispatcher,
    private val computerDatabaseManager: ComputerDatabaseManager
) : ViewModel(), RnGameIntentHelper {
    private val appContext by lazy { application }
    private val _navigation = MutableSharedFlow<RnGameErrorModel.Navigation>()
    val navigation = _navigation.asSharedFlow()
    private var recoveryJob : Job? = null
    private val computerDetailsCache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<String, ComputerDetails>()
    /**
     * Call when a [RnStreamError] is displayed
     *
     * Attempt to handle a [RnStreamError] by:
     * 1. Based on what [RnStreamError.getRecoveryStep] returns
     * - If [Intent.getRecoveryCount] is more than 1, we will should just [emit] [RnGameErrorModel.Navigation.Error] or [RnGameErrorModel.Navigation.ErrorWithRestart]
     *
     * 2. else we call [handleRecoveryStep]
     *
     * or just [emit] [RnGameErrorModel.Navigation.Error]
     */
    fun onStreamErrorDisplayed(streamError : RnStreamError, gameIntent: Intent?) {
        val recoveryStep = streamError.getRecoveryStep()
        val recoveryCount = (gameIntent?.getRecoveryCount() ?: 0)
        val isRecoveryAllowed = recoveryCount <= 3
        gameIntent?.setLastStreamErrorTimestamp()

        Timber.v("onStreamErrorDisplayed: ${recoveryStep} recoveryCount=$recoveryCount streamError=$streamError")
        if(recoveryStep != null) {
            if(isRecoveryAllowed) {
                recoveryJob?.cancel()
                recoveryJob = viewModelScope.launch { handleRecoveryStep(streamError, gameIntent) }
            } else {
                RnGameErrorModel.Navigation.ShowError(streamError = streamError, gameIntent = gameIntent).emit()
            }
        } else {
            RnGameErrorModel.Navigation.ShowError(streamError = streamError).emit()
        }

    }

    private fun RnGameErrorModel.Navigation.emit() = viewModelScope.launch {
        _navigation.emit(this@emit)
    }

    private fun emitRelaunchGame(gameIntent: Intent) {
        gameIntent.setReplaceSession(true)
        gameIntent.addRecoveryCount()
        RnGameErrorModel.Navigation.FinishAndRestartGame(gameIntent).emit()
    }

    /**
     * When use choose to kick out existing client
     */
    fun onUserReplaceSession(gameIntent: Intent) = emitRelaunchGame(gameIntent)

    fun onCancelClicked(streamError : RnStreamError) {
        val recoveryJob = recoveryJob
        if(recoveryJob?.isActive == true) {
            recoveryJob.cancel()
        } else {
            RnGameErrorModel.Navigation.FinishAndRestartGame(null)
        }
    }



    /**
     * Call when a [RnStreamError] with a [RnStreamError.RecoveryStep] is displayed, we should
     * start the process to recover
     */
    private suspend fun handleRecoveryStep(streamError : RnStreamError, gameIntent: Intent?) {
        val recoveryStep = requireNotNull(streamError.getRecoveryStep()) { "No recoveryStep" }
        withContext(ioDispatcher) {
            when (recoveryStep) {
                RnStreamError.RecoveryStep.PromptReplaceSessionOrQuit -> {
                    Timber.v("onRecoverableStreamErrorDisplayed: PromptReplaceSessionOrQuit")
                    try {
                        maybePromptReplaceSessionOrQuit(streamError, gameIntent).emit()
                    } catch (t : Throwable) {
                        RnGameErrorModel.Navigation.ShowError(streamError = streamError, recoveryError = t).emit()
                    }
                }
                is RnStreamError.RecoveryStep.FallbackToVideoSettings -> {
                    Timber.v("onRecoverableStreamErrorDisplayed: FallbackToVideoSettings")
                   handleFallbackToVideoSettings(streamError, recoveryStep, gameIntent)
                }
                RnStreamError.RecoveryStep.PollHostUntilOnline -> {
                    Timber.v("onRecoverableStreamErrorDisplayed: PollHostUntilOnline")
                    try {
                        pollHostUntilOnline(streamError, gameIntent)
                    } catch (t: Throwable) {
                        RnGameErrorModel.Navigation.ShowError(streamError = streamError, recoveryError = t).emit()
                    }
                }
            }
        }
    }

    /**
     * See https://razersw.atlassian.net/browse/NEUR-162
     *
     * This returns a size in [standardResolutions] where the [Size] is
     * the first resolution we should try to fallback to (probably the most unsafe)
     *
     * Safest will be [SharedConstants.DEFAULT_LIST_RESOLUTION] (but not the highest)
     */
    private fun firstFallbackResolution() : android.util.Size {
        // requireNotNull(SharedConstants.DEFAULT_LIST_RESOLUTION.toResolution()).run { Size(width, height) }
        val screenSize = appContext.getFullScreenSize()
        var maxSafeResolutionHeight = 1080
        val aspectRatioOfDevice = 1f*screenSize.width/screenSize.height
        val aspectRatio16x9 = 1f*16/9
        val value = if(aspectRatioOfDevice < aspectRatio16x9) screenSize.width else screenSize.height
        if(value < 1280) {
            maxSafeResolutionHeight = 720
        }
        return standardResolutions.first { it.height == maxSafeResolutionHeight }
    }

    /**
     * See https://razersw.atlassian.net/browse/NEUR-162
     */
    private suspend fun handleFallbackToVideoSettings(streamError: RnStreamError, fallbackStep : RnStreamError.RecoveryStep.FallbackToVideoSettings, gameIntent: Intent?) {
        val tag = "handleFallbackToVideoSettings"
        Timber.v("$tag: ${"-".repeat(20)}")
        val wasUseFallbackResolution = RemotePlaySettingsPref.isUseFallbackResolution
        val existingFallbackResolution = if(wasUseFallbackResolution) RemotePlaySettingsPref.fallbackResolution?.toResolution() else null
        val defaultResolution =
            requireNotNull(SharedConstants.DEFAULT_LIST_RESOLUTION.toResolution())
        val minFallbackResolution = defaultResolution
        var fallbackResolution = defaultResolution
        val computerName = gameIntent?.getStringExtra(Game.EXTRA_PC_UUID)?.let { computerUuid -> computerDatabaseManager.getComputerByUUID(computerUuid) }?.name

        Timber.v("$tag: isAllowRetry=${fallbackStep.isAllowRetry} recoveryCount=${gameIntent?.getRecoveryCount()} wasUseFallbackResolution=$wasUseFallbackResolution existingFallbackResolution=$existingFallbackResolution, wasUseFallbackResolution=$wasUseFallbackResolution, defaultResolution=$defaultResolution")
        if (fallbackStep.isAllowRetry) {
            if(existingFallbackResolution != null) {
                Timber.v("$tag: already tried $existingFallbackResolution")
                val nextFallbackResolution = standardResolutions.getOrNull(
                    standardResolutions.indexOf(existingFallbackResolution) - 1
                )?.takeIf { it.height >= minFallbackResolution.height }
                if (nextFallbackResolution != null) {
                    Timber.v("$tag: going to try $nextFallbackResolution next")
                    fallbackResolution = nextFallbackResolution
                }
            } else{
                fallbackResolution = firstFallbackResolution()
                Timber.v("$tag: going to try $fallbackResolution first")
            }
            // this is a critical step, according to Dejun, before we allow user to retry, we should delay
            // a bit so that we give enough time for the existing connection to stop, it takes the host 3s~4s to timeout
            for(i in 7 downTo 1) {
                val message = appContext.getString(R.string.rn_reconnecting_to_x, computerName ?: "?")
                RnGameErrorModel.Navigation.ShowLoading(message).emit()
                delay(1000)
            }
        }

        gameIntent?.addRecoveryCount()

        // fallback to duplicate and fallbackResolution
        val fallbackDisplayMode = when(streamError.errorCode) {
            RnStreamError.ERROR_CODE_UNSUPPORTED_NATIVE_RESOLUTION,
            RnStreamError.ERROR_CODE_UNSUPPORTED_720P_RESOLUTION -> DisplayModeOption.safest
            else -> RemotePlaySettingsPref.displayMode
        }

        Timber.v("$tag: fallbackResolution=$fallbackResolution, fallbackDisplayMode=$fallbackDisplayMode")
        debugToast("Falling back to ${fallbackResolution.toResolutionString()}, $fallbackDisplayMode")

        NeuronBridge.fallbackVideoSettings(fallbackResolution = fallbackResolution, fallbackDisplayMode = fallbackDisplayMode)
        // if it is already on default res (720p) OR if error doesn't allow retry
        // then we just show the error
        if((wasUseFallbackResolution && existingFallbackResolution == defaultResolution) || !fallbackStep.isAllowRetry) {
            Timber.v("$tag: ShowError")
            // make sure we tell user that we reverted the settings
            RnGameErrorModel.Navigation.ShowError(
                streamError = streamError.updateMessageToIncludeFallbackVideoSettings(RemotePlaySettingsPref.displayMode != fallbackDisplayMode),
                gameIntent = if (fallbackStep.isAllowRetry) gameIntent else null
            ).emit()
        } else {
            Timber.v("$tag: FinishAndRestartGame")
            // else if keep trying
            RnGameErrorModel.Navigation.FinishAndRestartGame(
                gameIntent = gameIntent
            ).emit()
        }
    }


    /**
     * Update the [RnStreamError.message] to include a new sentence about the app apply some video
     * settings changes to handle the error
     *
     * see
     * - [R.string.rn_fallback_display_mode_and_video_res_msg]
     * - [R.string.rn_fallback_video_res_msg]
     */
    private fun RnStreamError.updateMessageToIncludeFallbackVideoSettings(didFallbackDisplayOption : Boolean) : RnStreamError {
        // make sure we tell user that we reverted the settings
        return if(!hasFallbackVideoSettingsMessage()) {
            val newParagraph = if(didFallbackDisplayOption) {
                getStringExt(R.string.rn_fallback_display_mode_and_video_res_msg)
            } else {
                getStringExt(R.string.rn_fallback_video_res_msg)
            }
            val newMessage = (message + newParagraph)
            copy(message = newMessage)
        } else {
            this
        }
    }
    /**
     * True if [RnStreamError.message] already mentioned (in words) that the app
     * already revert the video settings (i.e. calling [NeuronBridge.fallbackVideoSettings])
     */
    private fun RnStreamError.hasFallbackVideoSettingsMessage(): Boolean {
        return errorCode == RnStreamError.ERROR_CODE_UNSUPPORTED_NATIVE_RESOLUTION
                || errorCode == RnStreamError.ERROR_CODE_UNSUPPORTED_720P_RESOLUTION
    }
    /**
     * Handle 5036 error which is recover via [RnStreamError.RecoveryStep.PromptReplaceSessionOrQuit]
     * See https://razersw.atlassian.net/browse/NEUR-158
     */
    @WorkerThread
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun maybePromptReplaceSessionOrQuit(streamError : RnStreamError, gameIntent: Intent?) : RnGameErrorModel.Navigation {
        val tag = "checkCurrentSession"
        requireNotNull(gameIntent) { "Missing game intent" }
        if(gameIntent.isReplaceSession()) {
            debugToast("Already tried replace session")
            return RnGameErrorModel.Navigation.ShowError(streamError = streamError)
        }
        val computerDetailsUuid = requireNotNull(gameIntent.getStringExtra(Game.EXTRA_PC_UUID))
        val appId = requireNotNull(gameIntent.getIntExtra(Game.EXTRA_APP_ID, 0).takeIf { it > 0 })
        var appName = gameIntent.getStringExtra(Game.EXTRA_APP_NAME)

        Timber.v("$tag: computerDetailsUuid $computerDetailsUuid")
        Timber.v("$tag: $appId ($appName)")
        val (computerDetails, http) = runCatching { findComputerDetails(computerDetailsUuid) }.getOrNull() ?: error("Computer not found")

        val runningGameId = computerDetails.runningGameId
        val runningGameDevice = computerDetails.runningGameDevice
        var runningGame : NvApp? = null
        var app : NvApp? = null
        runCatching {
            val list = (http.appList ?: LinkedList())
            runningGame = list.firstOrNull { it.appId == runningGameId }
            app = list.firstOrNull { it.appId == appId }
        }.exceptionOrNull()?.let {
            Timber.w(it)
            Timber.w("$tag: error ${it.message}")
        } // can ignore error here since runningGameName is optional


        val _runningGameDevice = runningGameDevice
        val _runningGameName = runningGame?.appName
        return if(_runningGameName == null || _runningGameDevice == null) {
            RnGameErrorModel.Navigation.ShowError(streamError = streamError, recoveryError = IllegalStateException("Running game not in applist"))
        } else {
            RnGameErrorModel.Navigation.PromptReplaceSessionOrQuit(
                currentGame = _runningGameName,
                currentDevice = _runningGameDevice,
                gameIntent = gameIntent
            )
        }
    }

    @Throws(RuntimeException::class)
    private suspend fun pollHostUntilOnline(streamError : RnStreamError, gameIntent: Intent?) {
        try {
            withContext(ioDispatcher) {
                if(streamError.isLocalUngracefulTermination) {
                    // so far we don't know how to handle any of these.
                    RnGameErrorModel.Navigation.ShowError(streamError = streamError).emit()
                } else {
                    requireNotNull(gameIntent) { "Missing game intent" }
                    val computerDetailsUuid =
                        requireNotNull(gameIntent.getStringExtra(Game.EXTRA_PC_UUID))
                    val appId =
                        requireNotNull(
                            gameIntent.getIntExtra(Game.EXTRA_APP_ID, 0).takeIf { it > 0 })
                    val appName = gameIntent.getStringExtra(Game.EXTRA_APP_NAME)

                    pollServerInfo(
                        streamError = streamError,
                        gameIntent = gameIntent,
                        computerUuid = computerDetailsUuid,
                        appId = appId,
                        appName = appName,
                        maxDurationMs = if (appContext.connectivityManager().isDefaultNetworkActive) 30000 else 8000
                    )
                }
            }
        } catch (t : CancellationException) {
            /**
             * either user press cancel or (more rarely) there is a duplicate call on
             * [handleUngracefulTermination].
             *
             * If use press cancel, we should close the error page
             */
            RnGameErrorModel.Navigation.FinishAndRestartGame(null).emit()
        } catch (t : Throwable) {
            /**
             * Some other unforeseen error during the [pollServerInfo] or other issue
             *
             * So we show the original error
             */
            RnGameErrorModel.Navigation.ShowError(streamError = streamError).emit()
        }
    }

    /**
     * Get the latest [ComputerDetails] and [NvHTTP]
     */
    private fun findComputerDetails(computerUuid : String) : Pair<ComputerDetails, NvHTTP> {
        var computerDetails = computerDatabaseManager.getComputerByUUID(computerUuid)
        var activeAddressResult = computerDetails.findActiveAddress(appContext, isUpdateThis = true)
        Timber.v("findComputerDetails: ${activeAddressResult.getOrNull()} ${activeAddressResult.exceptionOrNull()}")
        if(activeAddressResult.isFailure) {
            val cachedComputer = computerDetailsCache.getIfPresent(computerUuid)
            if(cachedComputer != null) {
                Timber.v("findComputerDetails: cachedComputer found for ${computerDetails?.name} $computerUuid")
                // will update computerDetails.activeAddress
                val newActiveAddress = cachedComputer.findActiveAddress(appContext, isUpdateThis = true).getOrNull()
                if(newActiveAddress != null) {
                    Timber.v("findComputerDetails: cachedComputer newActiveAddress ${newActiveAddress}")
                    computerDetails.activeAddress = newActiveAddress
                    computerDetails.httpsPort = cachedComputer.httpsPort
                }
            } else {
                Timber.w("findComputerDetails: No cachedComputer found for ${computerDetails?.name} $computerUuid")
            }
        }

        val httpsPort = computerDetails.httpsPort
        val activeAddress = computerDetails.activeAddress
        val serverCert = computerDetails.serverCert
        val http = NvHTTP(
            activeAddress,
            httpsPort,
            null,
            serverCert,
            PlatformBinding.getCryptoProvider(appContext))
        computerDetails = http.getComputerDetails(true)
        computerDetails.activeAddress = activeAddress
        computerDetails.serverCert = serverCert
        return computerDetails to http
    }


    /**
     * Poll [NvHTTP.getServerInfo] and see if we can [emit] some [RnGameErrorModel.Navigation]
     */
    private suspend fun pollServerInfo(
        streamError : RnStreamError,
        gameIntent: Intent,
        computerUuid: String,
        appId : Int,
        appName : String?,
        maxDurationMs : Long,
        intervalMs : Long = 2000) {
        // give it some times first
        val start = now()
        val devicename = appContext.getDeviceNickName()
        val originalComputerName = computerDatabaseManager.getComputerByUUID(computerUuid)?.name ?: computerUuid
        var aliveCount = 0
        while((now() - start) < maxDurationMs) {
            val pollResult = kotlin.runCatching { findComputerDetails(computerUuid) }.getOrNull()
            val computerDetails = pollResult?.first
            if(computerDetails != null) {
                require(computerDetails.isAutoRestartSupported()) { "Host does not support auto restart, so no polling needed" }
                aliveCount++
                val isNoClientStreaming = computerDetails.runningGameDevice?.isBlank() ?: true
                val isAnotherDeviceStreaming = computerDetails.runningGameDevice?.takeIf { it.isNotBlank() }?.let { it != devicename } ?: false
                if(isAnotherDeviceStreaming) {
                    debugToast("Session was taken over by another client")
                    break
                }
                if(aliveCount >= 4 && isNoClientStreaming) {
                    // start the game now, no need to keep polling
                    updateIntentHostAndPort(gameIntent, computerDetails)
                    emitRelaunchGame(gameIntent)
                    return
                }
            } else {
                // let ui know we are reconnecting
                RnGameErrorModel.Navigation.Reconnecting(streamError = streamError, computerName = originalComputerName).emit()
            }
            // wait interval (eg 1s)
            delay(intervalMs)
        }
        // timeout, so we show the original error
        RnGameErrorModel.Navigation.ShowError(streamError = streamError).emit()
    }

    fun onComputerDetailsUpdated(computerDetails: ComputerDetails) {
        Timber.v("onComputerDetailsUpdated: ${computerDetails.name} activeAddress=${computerDetails.activeAddress} localAddress=${computerDetails.localAddress}")
        computerDetailsCache.put(computerDetails.uuid, computerDetails)
    }

    fun onComputerDetailsRemoved(computerDetails: ComputerDetails) {
        Timber.v("onComputerDetailsRemoved: ${computerDetails.name} activeAddress=${computerDetails.activeAddress} localAddress=${computerDetails.localAddress}")
        computerDetailsCache.invalidate(computerDetails.uuid)
    }

    companion object {
        val TAG = "RnGameErrorViewModel"
    }
}

class RnGameErrorModel {
    sealed class Navigation(val id: String) {
        class PromptReplaceSessionOrQuit(val currentDevice : String, val currentGame : String, val gameIntent : Intent) : Navigation("replace_session")

        /**
         * If [gameIntent] is not null, then we will start the game too by starting the intent
         */
        class FinishAndRestartGame(val gameIntent : Intent?) : Navigation("finish")
        class Reconnecting(val streamError : RnStreamError, val computerName : String): Navigation("reconnecting")

        /**
         * If [gameIntent] is not null, then we will start the game too by starting the intent
         */
        class ShowError(val streamError: RnStreamError, val recoveryError : Throwable? = null, val gameIntent : Intent? = null) : Navigation("Error")
        class ShowLoading(val text : String) : Navigation("loading")
    }
}


fun Intent.addRecoveryCount(): Int {
    val count = getRecoveryCount() + 1
    putExtra(EXTRA_RECOVERY_COUNT, count)
    return count
}

fun Intent.getRecoveryCount() = getIntExtra(EXTRA_RECOVERY_COUNT, 0)


fun Intent.setLastStreamErrorTimestamp(timestamp : Long = now()) {
    putExtra(EXTRA_LAST_STREAM_ERROR_TIMESTAMP, timestamp)
}

fun Intent.getLastStreamErrorTimestamp() = getLongExtra(EXTRA_LAST_STREAM_ERROR_TIMESTAMP, 0L)




