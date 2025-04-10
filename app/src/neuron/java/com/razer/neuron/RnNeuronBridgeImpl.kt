package com.razer.neuron

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.preference.PreferenceManager
import android.util.Size
import android.view.Window
import com.limelight.Game
import com.limelight.NeuronBridgeInterface
import com.limelight.R
import com.limelight.RemotePlayConfig
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.VideoStats
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.DisplayMode
import com.limelight.nvstream.http.toResolution
import com.limelight.nvstream.http.toResolutionString
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.razer.neuron.extensions.SCM_AV1_MAIN10
import com.razer.neuron.extensions.SCM_AV1_MAIN8
import com.razer.neuron.extensions.SCM_AV1_MASK_10BIT
import com.razer.neuron.extensions.SCM_H264
import com.razer.neuron.extensions.SCM_HEVC
import com.razer.neuron.extensions.SCM_HEVC_MAIN10
import com.razer.neuron.extensions.edit
import com.razer.neuron.extensions.getAllSupportedNativeFps
import com.razer.neuron.extensions.getPPI
import com.razer.neuron.extensions.getScreenResolution
import com.razer.neuron.extensions.getUserFacingMessage
import com.razer.neuron.extensions.parseServerCodecModeSupportFlags
import com.razer.neuron.extensions.parseSupportedVideoFormat
import com.razer.neuron.extensions.willStreamHdr
import com.razer.neuron.game.helpers.RnStreamError
import com.razer.neuron.managers.AIDLManager
import com.razer.neuron.managers.RumbleManager.isRumbleWithNexus
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.model.ResolutionScale
import com.razer.neuron.model.SessionStats
import com.razer.neuron.model.isDesktop
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.shared.RazerRemotePlaySettingsKey
import com.razer.neuron.shared.SharedConstants
import com.razer.neuron.shared.SharedConstants.DEFAULT_LIST_FPS
import com.razer.neuron.shared.SharedConstants.DEFAULT_LIST_RESOLUTION
import com.razer.neuron.shared.SharedConstants.REDUCE_REFRESH_RATE_PREF_STRING
import com.razer.neuron.utils.calculateVirtualDisplayMode
import com.razer.neuron.utils.getDefaultDisplayRefreshRateHz
import com.razer.neuron.utils.getDeviceNickName
import kotlinx.coroutines.future.future
import timber.log.Timber
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * You can use com.razer.neuron.xxx classes here
 */
class RnNeuronBridgeImpl(val context: Context) : NeuronBridgeInterface {



    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private val displayMode get() = prefs.getString(RazerRemotePlaySettingsKey.PREF_DISPLAY_MODE, null)?.let { DisplayModeOption.findByDisplayModeName(it) }

    override val isLaunchWithVirtualDisplay
        get() = displayMode?.isUsesVirtualDisplay == true

    val isLimitRefreshRate get() = prefs.getBoolean(REDUCE_REFRESH_RATE_PREF_STRING, false)

    override fun getUserFacingMessage(t: Throwable) = t.getUserFacingMessage()

    private var listResolution: String
        get() {
            return RemotePlaySettingsPref.sharedPreferences.getString(
                PreferenceConfiguration.RESOLUTION_PREF_STRING,
                SharedConstants.DEFAULT_LIST_RESOLUTION
            ) ?: SharedConstants.DEFAULT_LIST_RESOLUTION
        }
        set(value) {
            RemotePlaySettingsPref.sharedPreferences.edit {
                putString(PreferenceConfiguration.RESOLUTION_PREF_STRING, value)
            }
        }

    private var listFps: String
        get() {
            return RemotePlaySettingsPref.sharedPreferences.getString(
                PreferenceConfiguration.FPS_PREF_STRING,
                SharedConstants.DEFAULT_LIST_FPS
            ) ?: SharedConstants.DEFAULT_LIST_FPS
        }
        set(value) {
            RemotePlaySettingsPref.sharedPreferences.edit {
                putString(PreferenceConfiguration.FPS_PREF_STRING, value)
            }
        }


    override fun fallbackVideoSettings(fallbackResolution: Size?, fallbackDisplayMode : DisplayModeOption?) {
        val _fallbackDisplayMode = fallbackDisplayMode ?: DisplayModeOption.safest
        val tag = "fallbackVideoSettings"
        RemotePlaySettingsPref.isUseFallbackResolution = true
        RemotePlaySettingsPref.fallbackResolution = fallbackResolution?.toResolutionString() ?: DEFAULT_LIST_RESOLUTION
        Timber.v("$tag: final fallbackResolution=${RemotePlaySettingsPref.fallbackResolution}, fallbackDisplayMode=${_fallbackDisplayMode}")
        prefs.edit {
            putString(RazerRemotePlaySettingsKey.PREF_DISPLAY_MODE, _fallbackDisplayMode.displayModeName)
        }
    }

    override fun doVibrate(lowFreqMotor: Int, highFreqMotor: Int) = AIDLManager.doVibrate(lowFreqMotor, highFreqMotor)

    override fun isRumbleWithNexus(): CompletableFuture<Boolean> {
        return RnApp.globalScope.future {
            context.isRumbleWithNexus()
        }
    }

    /**
     * NEUR-59
     *
     *  Url encoding will be handled by the caller (this is because
     *  [okhttp3.HttpUrl.Builder.query] (used by [com.limelight.nvstream.http.NvHTTP])
     *  will canonicalize the query internally)
     */
    override fun getPairQueryParameters(): String {
        val deviceNickname = context.getDeviceNickName()
        val params = mutableListOf<Pair<String, Any?>>(
            "devicenickname" to deviceNickname
        )
        params.removeAll { it.second == null }

        // need to start and separate with '&'
        return params.joinToString("") { (k, v) ->
            "&$k=${v}"
        }
    }

    private fun DisplayModeOption.toQueryParamInt() = when(this) {
        DisplayModeOption.DuplicateDisplay -> 0
        DisplayModeOption.SeparateDisplay -> 1
        DisplayModeOption.PhoneOnlyDisplay -> 2
    }




    /**
     * See [NeuronBridgeInterface.updatePreferenceConfiguration]
     */
    override fun updatePreferenceConfiguration(window: Window, prefConfig: PreferenceConfiguration, intent : Intent) {
        val activeDisplayMode = intent.getStringExtra(Game.EXTRA_HOST_ACTIVE_DISPLAY_MODE)?.let { DisplayMode.createDisplayMode(it) }
        val displayMode = displayMode ?: return
        val tag = "updateResolutionAndRefreshRate"

        Timber.v("$tag: activeDisplayMode=$activeDisplayMode, displayMode=$displayMode")

        when {
            RemotePlaySettingsPref.isUseFallbackResolution -> {
                val defaultResolution =
                    requireNotNull(DEFAULT_LIST_RESOLUTION.toResolution()) { "default resolution not formatted" }
                val defaultFps =
                    requireNotNull(DEFAULT_LIST_FPS.toIntOrNull()) { "default FPS not formatted" }
                val fallbackFps = defaultFps
                val fallbackResolution = RemotePlaySettingsPref.fallbackResolution?.toResolution() ?: defaultResolution
                Timber.v("$tag: fallbackResolution=$defaultResolution fallbackFps=$defaultFps")
                prefConfig.width = fallbackResolution.width
                prefConfig.height = fallbackResolution.height
                prefConfig.fps = fallbackFps
            }
            displayMode == DisplayModeOption.PhoneOnlyDisplay || displayMode == DisplayModeOption.SeparateDisplay -> {
                val virtualDisplayMode = calculateVirtualDisplayMode(context).getOrNull()
                Timber.v("$tag: virtualDisplayMode=$virtualDisplayMode")
                if (virtualDisplayMode != null) {
                    prefConfig.width = virtualDisplayMode.widthInt ?: prefConfig.width
                    prefConfig.height = virtualDisplayMode.heightInt ?: prefConfig.height
                    prefConfig.fps = virtualDisplayMode.refreshRateInt ?: prefConfig.fps
                }
            }
            displayMode == DisplayModeOption.DuplicateDisplay -> {
                // for cases when we had to fallback to use default resolution
                if(activeDisplayMode != null) {
                    // NEUR-103
                    val nativeRefreshRates = getAllSupportedNativeFps(window)
                    val closestMatchingRefreshRate = nativeRefreshRates.minByOrNull { abs(it - (activeDisplayMode.refreshRateInt ?: 0)) } ?: context.getDefaultDisplayRefreshRateHz(true)
                    val (w, h) = activeDisplayMode.widthInt to activeDisplayMode.heightInt
                    Timber.v("$tag: w=$w, h=$h nativeRefreshRates=$nativeRefreshRates, closestMatchingRefreshRate=$closestMatchingRefreshRate")
                    if (w != null && h != null) {
                        prefConfig.width = w
                        prefConfig.height = h
                        prefConfig.fps = closestMatchingRefreshRate
                    }
                }
            }
        }
        Timber.v("$tag: prefConfig.widthxheight=${prefConfig.width}x${prefConfig.height} fps=${prefConfig.fps}")
    }

    /**
     * See [NeuronBridgeInterface.updateStreamConfiguration]
     * Already called serverInfo API and convert the data to [ConnectionContext.computerDetails]
     *
     * Apply logic to determine if we should use AV1 by tricking the [MediaCodecDecoderRenderer] into thinking
     * user wants to use AV1. But we still need to check that it supports AV1
     *  1. Check if AV1 is supported by [MediaCodecDecoderRenderer] but this is depended on [PreferenceConfiguration]
     *  2. Use [MediaCodecDecoderRenderer] using a fake [PreferenceConfiguration]
     *  3. Run [MediaCodecDecoderRenderer.findAv1Decoder]
     *  4. Set [com.limelight.nvstream.StreamConfiguration.supportedVideoFormats] (bit flag) (in [ConnectionContext.streamConfig])
     */
    override fun updateStreamConfiguration(
        connectionContext: ConnectionContext,
        decoderRenderer: MediaCodecDecoderRenderer
    ) {
        runCatching {
            if(connectionContext.prefConfig.videoFormat == PreferenceConfiguration.FormatOption.AUTO) {
                //applyAutoVideoFormat0(connectionContext, decoderRenderer)
            }
            decoderRenderer.debugServerCodecModeSupport = connectionContext.serverCodecModeSupport.parseServerCodecModeSupportFlags()
            decoderRenderer.debugLocalSupportedVideoFormats = connectionContext.streamConfig.supportedVideoFormats.parseSupportedVideoFormat()
            Timber.v("updateStreamConfiguration: debugServerCodecModeSupport=${decoderRenderer.debugServerCodecModeSupport}")
            Timber.v("updateStreamConfiguration: debugLocalSupportedVideoFormats=${decoderRenderer.debugLocalSupportedVideoFormats}")
        }.exceptionOrNull()?.let {
            logAndRecordException(it)
        }
    }

    /**
     * NEUR-154
     * Custom logic to deal with "AUTO" video format
     *
     * Will modify [ConnectionContext.streamConfig]
     */
    private fun applyAutoVideoFormat0(connectionContext: ConnectionContext,
                                     decoderRenderer: MediaCodecDecoderRenderer) {
        val originalPrefConfig = requireNotNull(connectionContext.prefConfig) { "prefConfig must be set in connectionContext" }
        Timber.v("applyAutoVideoFormat0: videoFormat=${originalPrefConfig.videoFormat} isAv1Main10Supported=${decoderRenderer.isAv1Main10Supported}, isAv1Supported=${decoderRenderer.isAv1Supported}")
        val localAv1Decoder : MediaCodecInfo? = decoderRenderer.findAv1Decoder(originalPrefConfig)

        val oldStreamConfig = connectionContext.streamConfig
        val newStreamConfig = StreamConfiguration.Builder(oldStreamConfig)
        var newSupportedVideoFormats = oldStreamConfig.supportedVideoFormats
        val willStreamHdr = context.willStreamHdr(originalPrefConfig)

        // if it is snapdragon
        val serverCodecModeSupport = connectionContext.serverCodecModeSupport
        Timber.v("applyAutoVideoFormat0: av1Decoder=${localAv1Decoder?.name}")
        Timber.v("applyAutoVideoFormat0: serverCodecModeSupport=${serverCodecModeSupport}")
        Timber.v("applyAutoVideoFormat0: SCM_AV1_MAIN8=${(serverCodecModeSupport and SCM_AV1_MAIN8) == SCM_AV1_MAIN8}")
        Timber.v("applyAutoVideoFormat0: SCM_AV1_MAIN10=${(serverCodecModeSupport and SCM_AV1_MAIN10) == SCM_AV1_MAIN10}")
        Timber.v("applyAutoVideoFormat0: SCM_H264=${(serverCodecModeSupport and SCM_H264) == SCM_H264}")
        Timber.v("applyAutoVideoFormat0: SCM_HEVC=${(serverCodecModeSupport and SCM_HEVC) == SCM_HEVC}")
        Timber.v("applyAutoVideoFormat0: SCM_HEVC_MAIN10=${(serverCodecModeSupport and SCM_HEVC_MAIN10) == SCM_HEVC_MAIN10}")

        val hasAv1ServerSupported = (serverCodecModeSupport and SCM_AV1_MASK_10BIT) != 0 // either SCM_AV1_MAIN10 or SCM_AV1_MAIN8
        if (hasAv1ServerSupported && localAv1Decoder != null) {
            newSupportedVideoFormats = newSupportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            if (willStreamHdr && decoderRenderer.isAv1Main10Supported(localAv1Decoder)) {
                newSupportedVideoFormats =
                    newSupportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN10
            }
            newStreamConfig.setSupportedVideoFormats(newSupportedVideoFormats)
        }
        connectionContext.streamConfig = newStreamConfig.build()
    }

    /**
     * See [NeuronBridgeInterface.getLaunchUrlQueryParameters]
     * See NEUR-4
     * - Add virtualDisplay param (1 or 0)
     * - Add "clientNativeResolution" param (wxh)
     *
     *  Url encoding will be handled by the caller (this is because
     *  [okhttp3.HttpUrl.Builder.query] (used by [com.limelight.nvstream.http.NvHTTP])
     *  will canonicalize the query internally)
     */
    override fun getLaunchUrlQueryParameters(connectionContext: ConnectionContext): String {
        val computerDetails = connectionContext.computerDetails
        val tag = "getLaunchUrlQueryParameters"
        val nvApp = connectionContext.streamConfig.app
        val deviceNickname = context.getDeviceNickName()
        val virtualDisplayModeFormated = calculateVirtualDisplayMode(context).getOrNull()?.format()
        val ppi = context.getPPI().roundToInt()
        val screenSize = context.getScreenResolution()
        val _displayMode = displayMode ?: DisplayModeOption.DuplicateDisplay
        val getUIScale = ResolutionScale.getUIScale(context)

        Timber.v("$tag: _displayMode=$_displayMode, computerDetails.activeDisplayMode=${computerDetails?.activeDisplayMode}, $deviceNickname, $virtualDisplayModeFormated")
        val params = mutableListOf<Pair<String, Any?>>(
            "virtualDisplay" to _displayMode.toQueryParamInt(),
            "virtualDisplayMode" to if(_displayMode.isUsesVirtualDisplay) virtualDisplayModeFormated else null,
            "devicenickname" to deviceNickname,
            "ppi" to ppi,
            "screen_resolution" to "${screenSize.x}x${screenSize.y}",
            "timeToTerminateApp" to if(nvApp?.isDesktop() == true) 0 else RemotePlaySettingsPref.autoCloseGameCountDown,
            "UIScale" to getUIScale.toString()
        )
        params.removeAll { it.second == null }

        // need to start and separate with '&'
        return params.joinToString("") { (k, v) ->
            "&$k=${v}"
        }
    }

    /**
     * BAA-2249
     */
    override fun updateVideoStats(
        randomId: String,
        decoder: String,
        initialSize: Size,
        avgNetLatency: Int,
        avgNetLatencyVarianceMs: Int,
        globalVideoStats: VideoStats?,
        lastWindowVideoStats: VideoStats?,
        activeWindowVideoStats: VideoStats?
    ) {
        val lastSession = SessionStats.lastSession?.takeIf { it.randomId == randomId }
            ?: (SessionStats(randomId = randomId).also { SessionStats.lastSession = it })
        lastSession.update(
            decoder,
            initialSize,
            avgNetLatency,
            avgNetLatencyVarianceMs,
            globalVideoStats,
            lastWindowVideoStats,
            activeWindowVideoStats
        )
    }


    override fun stringifyPortFlags(portFlags: Int) = stringifyPortFlags0(portFlags)

    /**
     * @param portFlags ignored in this implementation, it just lists all the ports that needs to be opened
     */
    private fun stringifyPortFlags0(portFlags: Int): String {
        fun Int.getPortFromPortFlagIndex() = RemotePlayConfig.default.portsMap[this] ?: 0
        val lines = mutableListOf<String>()
        lines += "TCP ${RemotePlayConfig.ML_PORT_INDEX_TCP_47984.getPortFromPortFlagIndex()}"
        lines += "TCP ${RemotePlayConfig.ML_PORT_INDEX_TCP_47989.getPortFromPortFlagIndex()}"
        lines += "TCP ${RemotePlayConfig.ML_PORT_INDEX_TCP_48010.getPortFromPortFlagIndex()}"
        lines += "UDP ${RemotePlayConfig.ML_PORT_INDEX_UDP_47998.getPortFromPortFlagIndex()} ~ ${RemotePlayConfig.ML_PORT_INDEX_UDP_48010.getPortFromPortFlagIndex()}"
        return (lines.joinToString("\n").trim())
    }

    /**
     *  @param portFlags (e.g. 1280 should return "UDP 51346\nUDP 51348")
     */
    private fun stringifyPortFlags1(portFlags: Int): String? {
        fun Int.getPortFromPortFlagIndex() = RemotePlayConfig.default.portsMap[this] ?: 0
        fun Int.getProtocolFromPortFlagIndex() = if(this >= 8) "UDP" else "TCP"
        val lines = mutableListOf<String>()
        Timber.v("stringifyPortFlags: portFlags${portFlags} (${portFlags.toString(2)})")
        for(i in 0 until 32) {
            val shifted = (portFlags and (1 shl i))
            if(shifted != 0) {
                lines += "${i.getProtocolFromPortFlagIndex()} ${i.getPortFromPortFlagIndex()}"
            }
        }
        return (lines.joinToString("\n").trim())
    }


    override fun logAndRecordException(t: Throwable) {
        com.razer.neuron.common.logAndRecordException(t)
    }



    override fun invertMotors(freqArray: IntArray) {
        require(freqArray.size == 2) { "Array size must be 2" }
        val temp = freqArray[0]
        freqArray[0] = freqArray[1]
        freqArray[1] = temp

    }


    override fun onStartNeuronGame() {
        AIDLManager.onStartNeuronGame()
    }

    override fun onStopNeuronGame() {
        AIDLManager.onStopNeuronGame()
    }

    override fun getLocalizedStageName(stage: String): String {
        return with(context) {
            when (stage) {
                "none" -> getString(R.string.conn_stage_none)
                "platform initialization" -> getString(R.string.conn_stage_platform_initialization)
                "name resolution" -> getString(R.string.conn_stage_name_resolution)
                "audio stream initialization" -> getString(R.string.conn_stage_audio_stream_initialization)
                "RTSP handshake" -> getString(R.string.conn_stage_RTSP_handshake)
                "control stream initialization" -> getString(R.string.conn_stage_control_stream_initialization)
                "video stream initialization" -> getString(R.string.conn_stage_video_stream_initialization)
                "input stream initialization" -> getString(R.string.conn_stage_input_stream_initialization)
                "control stream establishment" -> getString(R.string.conn_stage_control_stream_establishment)
                "video stream establishment" -> getString(R.string.conn_stage_video_stream_establishment)
                "audio stream establishment" -> getString(R.string.conn_stage_audio_stream_establishment)
                "input stream establishment" -> getString(R.string.conn_stage_input_stream_establishment)
                else -> stage
            }
        }
    }

    override fun getLocalizedStringFromErrorCode(errorCode: Int) = when (errorCode) {
        RnStreamError.ERROR_CODE_5031_CONCURRENT_STREAM_LIMIT_REACHED,
        RnStreamError.ERROR_CODE_5035_DEPRECATED_CONCURRENT_STREAM_LIMIT_REACHED,
        RnStreamError.ERROR_CODE_5036_MAYBE_REPLACE_EXISTING_SESSION -> context.getString(R.string.conn_error_code_5031)
        RnStreamError.ERROR_CODE_5032_FAILED_INIT_VID_ENCODING -> context.getString(R.string.conn_error_code_5032)
        RnStreamError.ERROR_CODE_5033_NO_RUNNING_APP_TO_RESUME -> context.getString(R.string.conn_error_code_5033)
        RnStreamError.ERROR_CODE_5034_ALL_SESSION_MUST_BE_DISCONNECTED -> context.getString(R.string.conn_error_code_5034)
        RnStreamError.ERROR_CODE_5037_HOST_ENCODER_NOT_FOUND -> context.getString(R.string.conn_error_code_5037)
        RnStreamError.ERROR_CODE_5038_CODEC_NOT_SUPPORTED_BY_HOST -> context.getString(R.string.conn_error_code_5038)
        RnStreamError.ERROR_CODE_5039_HOST_MONITOR_OFF -> context.getString(R.string.conn_error_code_5039)
        RnStreamError.ERROR_CODE_5040_HOST_MONITOR_OFF_IN_PHONE_ONLY_MODE -> context.getString(R.string.conn_error_code_5040)
        else -> "(error $errorCode)"
    }
}



