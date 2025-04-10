package com.razer.neuron.game.helpers

import android.content.res.Resources
import com.limelight.Game
import com.limelight.NeuronBridge
import com.limelight.R
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.nvstream.jni.MoonBridge
import com.razer.neuron.game.RnGame

/**
 * A collection of functions for [RnGame]
 */

data class RnStreamError(
    val stage: String,
    val portFlags: Int,
    val errorCode: Int,
    val title: String? = null,
    val message: String? = null
) {
    companion object {
        const val ERROR_CODE_UNHANDLED_TERMINATION = -12
        const val ERROR_CODE_UNSUPPORTED_NATIVE_RESOLUTION = -13
        const val ERROR_CODE_UNSUPPORTED_720P_RESOLUTION = -14

        const val ERROR_CODE_5031_CONCURRENT_STREAM_LIMIT_REACHED = 5031
        const val ERROR_CODE_5032_FAILED_INIT_VID_ENCODING = 5032
        const val ERROR_CODE_5033_NO_RUNNING_APP_TO_RESUME = 5033
        const val ERROR_CODE_5034_ALL_SESSION_MUST_BE_DISCONNECTED = 5034
        const val ERROR_CODE_5035_DEPRECATED_CONCURRENT_STREAM_LIMIT_REACHED = 5035
        const val ERROR_CODE_5036_MAYBE_REPLACE_EXISTING_SESSION = 5036
        const val ERROR_CODE_5037_HOST_ENCODER_NOT_FOUND = 5037
        const val ERROR_CODE_5038_CODEC_NOT_SUPPORTED_BY_HOST = 5038
        const val ERROR_CODE_5039_HOST_MONITOR_OFF = 5039
        const val ERROR_CODE_5040_HOST_MONITOR_OFF_IN_PHONE_ONLY_MODE = 5040

    }
    sealed class RecoveryStep {
        data object PromptReplaceSessionOrQuit : RecoveryStep()
        data class FallbackToVideoSettings(val isAllowRetry : Boolean) : RecoveryStep()
        data object PollHostUntilOnline  : RecoveryStep()
    }

    /**
     * Seems like all ungraceful termination code are less than [MoonBridge.ML_ERROR_GRACEFUL_TERMINATION]
     * See [MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC] ...
     */
    val isUngracefulTermination = errorCode < MoonBridge.ML_ERROR_GRACEFUL_TERMINATION

    /**
     * Some ungraceful terminations can be caused by the device itself
     */
    val isLocalUngracefulTermination = when(errorCode) {
        ERROR_CODE_UNHANDLED_TERMINATION,
        ERROR_CODE_UNSUPPORTED_NATIVE_RESOLUTION,
        ERROR_CODE_UNSUPPORTED_720P_RESOLUTION -> true
        else -> false
    }

    fun getLocalizedStageName() = NeuronBridge.getLocalizedStageName(stage)
    fun getLocalizedStringFromErrorCode() = NeuronBridge.getLocalizedStringFromErrorCode(errorCode)
    fun getPortErrorMessage() = if(portFlags != 0) {
        NeuronBridge.stringifyPortFlags(portFlags) ?: MoonBridge.stringifyPortFlags(portFlags, "\n")
    } else {
        null
    }

    /**
     * https://razersw.atlassian.net/browse/NEUR-158
     */
    fun getRecoveryStep() = when {
        errorCode == ERROR_CODE_5036_MAYBE_REPLACE_EXISTING_SESSION -> RecoveryStep.PromptReplaceSessionOrQuit
        errorCode == MediaCodecDecoderRenderer.ERROR_INIT_DECODER_CONFIG_FAIL_FOR_LOW_LATENCY ->
            RecoveryStep.FallbackToVideoSettings(isAllowRetry = true) // This is returned by Galaxy A9+ when streaming on native res, for some reason it has no good codec.  Workaround is to use 720p.
        errorCode == ERROR_CODE_UNSUPPORTED_NATIVE_RESOLUTION // This is returned by Game activity (only on Pixel 9pro and 7pro at 25% zoom when streaming on native res). Workaround is to use 720p.
            || errorCode == ERROR_CODE_UNSUPPORTED_720P_RESOLUTION ->   // For some devices, it might even fail at 720p. If so... idk
            RecoveryStep.FallbackToVideoSettings(isAllowRetry = false)
        isUngracefulTermination && !isLocalUngracefulTermination -> // server died...
            RecoveryStep.PollHostUntilOnline
        else -> null
    }


    /**
     * https://razersw.atlassian.net/browse/NEUR-157
     */
    fun getHelpWebsite() = when(errorCode) {
        ERROR_CODE_5032_FAILED_INIT_VID_ENCODING -> "https://mysupport.razer.com/app/answers/detail/a_id/14919"
        else -> null
    }
}



/**
 * This was translated from [Game.stageFailed]
 *
 *
 * @param errorCode refers to the error code inside the XML, not the http status code
 *
 * @return a list of [RnStreamError], the most useful one will be listed first.
 */
fun Resources.transformToStreamError(
    stage: String,
    portFlags: Int,
    errorCode: Int
): RnStreamError {

    var streamingError = RnStreamError(stage = stage, portFlags = portFlags, errorCode = errorCode)
    // Perform a connection test if the failure could be due to a blocked port
    // This does network I/O, so don't do it on the main thread.
    val portTestResult = 0 // withContext(Dispatchers.IO) { MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags) }
    // If video initialization failed and the surface is still valid, display extra information for the user
    if (stage.contains("video")) {
        streamingError = streamingError.copy(message = getString(R.string.video_decoder_init_failed))
    } else {

        val part2 = streamingError.getLocalizedStageName()
        var dialogText = getString(R.string.failed_to_start_msg_with_param, part2)

        val part3 = streamingError.getLocalizedStringFromErrorCode()
        dialogText += " $part3"

        streamingError.getPortErrorMessage()?.let { portErrorMessage ->
            dialogText += "\n\n${getString(R.string.check_ports_msg)}\n$portErrorMessage"
        }
        if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
            dialogText += "\n\n${getString(R.string.nettest_text_blocked)}"
        }

        streamingError = streamingError.copy(title = getString(R.string.conn_error_title), message = dialogText)
    }
    return streamingError
}

