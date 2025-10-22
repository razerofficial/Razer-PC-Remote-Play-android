package com.limelight

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.util.Size
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.VideoStats
import com.limelight.nvstream.ConnectionContext
import com.limelight.preferences.PreferenceConfiguration
import com.razer.neuron.model.DisplayModeOption
import java.util.concurrent.CompletableFuture


/**
 * DO NOT IMPORT com.razer.neuron.xxx classes here
 * DO NOT IMPORT com.razer.neuron.xxx classes here
 * Put the code in [RnNeuronBridgeImpl] instead
 */
interface NeuronBridgeInterface {


    /**
     * true if we should stick with current resolution during stream
     */
    val isMatchStreamViewResolutionToCurrentWindow : Boolean

    /**
     * See NEUR-59
     */
    fun getPairQueryParameters(): String


    fun getUserFacingMessage(t : Throwable) : String

    /**
     * For cases when virtual display has rendering issue
     */
    fun fallbackVideoSettings(fallbackResolution: Size? = null, fallbackDisplayMode : DisplayModeOption? = null)

    /**
     * true if virtual display is used
     */
    val isLaunchWithVirtualDisplay : Boolean

    fun doVibrate(lowFreqMotor: Int, highFreqMotor: Int)


    fun isRumbleWithNexus(): CompletableFuture<Boolean>

    /**
     * Called right after [com.limelight.preferences.PreferenceConfiguration] is created
     * in [Game] (Stage 1)
     */
    fun updatePreferenceConfiguration(activity: Activity, prefConfig: PreferenceConfiguration, intent : Intent)


    /**
     * Called right after [ConnectionContext.computerDetails] is assigned in
     * in [Game] (Stage 2)
     */
    fun updateStreamConfiguration(connectionContext: ConnectionContext, decoderRenderer: MediaCodecDecoderRenderer)


    /**
     * See NEUR-4
     * - Add "virtualDisplay" param (1 or 0)
     * - Add "clientNativeResolution" param (wxh)
     *
     * Called by [com.limelight.nvstream.http.NvHTTP.launchApp] to get the final URL to
     * launch game
     * Called in [Game] (Stage 3)
     */
    fun getLaunchUrlQueryParameters(connectionContext: ConnectionContext): String


    /**
     * BAA-2249
     */
    fun updateVideoStats(
        randomId : String,
        decoder: String,
        initialSize: Size,
        avgNetLatency: Int,
        avgNetLatencyVarianceMs: Int,
        globalVideoStats: VideoStats?,
        lastWindowVideoStats: VideoStats?,
        activeWindowVideoStats: VideoStats?
    )

    /**
     * Return ports information as a readable string
     *
     * These are ports that the router need to forward
     * See https://razersw.atlassian.net/browse/NEUR-31?focusedCommentId=605600
     *
     * @param portFlags ??? (see LiStringifyPortFlags)
     */
    fun stringifyPortFlags(portFlags : Int) : String?


    /**
     * For debugging and recording error
     */
    fun logAndRecordException(t : Throwable)

    /**
     * Invert freq in array (if needed)
     */
    fun invertMotors(freqArray : IntArray)

    /**
     * BAA-2375
     */
    fun onStartNeuronGame()

    /**
     * BAA-2375
     */
    fun onStopNeuronGame()


    /**
     * NEUR-37
     * - From [com.limelight.nvstream.jni.MoonBridge.getStageName]
     * - or some other means
     *
     * Unknown stage will be returned as it is.
     */
    fun getLocalizedStageName(stage : String) : String


    /**
     * NEUR-37
     * Transform custom error code to string
     */
    fun getLocalizedStringFromErrorCode(errorCode : Int) : String

    /**
     * Return a non-null build to customize the [AudioAttributes]
     * used by [com.limelight.binding.audio.AndroidAudioRenderer]
     */
    fun createAudioAttributesBuilder() : AudioAttributes.Builder?

    /**
     * Return a non-null build to customize the [AudioFormat]
     * used by [com.limelight.binding.audio.AndroidAudioRenderer]
     */
    fun createAudioFormatBuilder() : AudioFormat.Builder?
}


/**
 * DO NOT IMPORT com.razer.neuron.xxx classes here
 * DO NOT IMPORT com.razer.neuron.xxx classes here
 * Put the code in [RnNeuronBridgeImpl] instead
 */
object NeuronBridge {
    private var implementation: NeuronBridgeInterface = NoNeuronBridge

    @JvmStatic
    val isLaunchWithVirtualDisplay get() = implementation.isLaunchWithVirtualDisplay

    @JvmStatic
    val isMatchStreamViewResolutionToCurrentWindow get() = implementation.isMatchStreamViewResolutionToCurrentWindow


    fun setImplementation(implementation: NeuronBridgeInterface) {
        this.implementation = implementation
    }

    @JvmStatic
    fun getUserFacingMessage(t : Throwable) = implementation.getUserFacingMessage(t)

    /**
     * Used to update the [prefConfig] object before [Game] has a chance to use the values
     */
    @JvmStatic
    fun updatePreferenceConfiguration(activity: Activity, prefConfig: PreferenceConfiguration, intent : Intent) = implementation.updatePreferenceConfiguration(activity, prefConfig, intent)

    /**
     * Used to update the [ConnectionContext.streamConfig] object before [Game] has a chance to use the values
     *
     * See [NeuronBridge.updateStreamConfiguration]
     * See BAA-2341
     */
    @JvmStatic
    fun updateStreamConfiguration(connectionContext: ConnectionContext, decoderRenderer: MediaCodecDecoderRenderer) = implementation.updateStreamConfiguration(connectionContext,decoderRenderer)

    /**
     * Used to get the parameters to be send to host launch/resume API
     *
     * See [NeuronBridge.getLaunchUrlQueryParameters]
     */
    @JvmStatic
    fun getLaunchUrlQueryParameters(connectionContext: ConnectionContext) = implementation.getLaunchUrlQueryParameters(connectionContext)

    /**
     * See NEUR-59
     */
    @JvmStatic
    fun getPairQueryParameters() = implementation.getPairQueryParameters()

    @JvmStatic
    fun fallbackVideoSettings(fallbackResolution: Size? = null, fallbackDisplayMode: DisplayModeOption? = null) = implementation.fallbackVideoSettings(fallbackResolution, fallbackDisplayMode)

    @JvmStatic
    fun doVibrate(lowFreqMotor: Int, highFreqMotor: Int) = implementation.doVibrate(lowFreqMotor, highFreqMotor)

    @JvmStatic
    fun isRumbleWithNexus() = implementation.isRumbleWithNexus()

    @JvmStatic
    fun updateVideoStats(
        randomId : String,
        decoder: String,
        initialSize: Size,
        avgNetLatency: Int,
        avgNetLatencyVarianceMs: Int,
        globalVideoStats: VideoStats?,
        lastWindowVideoStats: VideoStats?,
        activeWindowVideoStats: VideoStats?
    ) = implementation.updateVideoStats(
        randomId,
        decoder,
        initialSize,
        avgNetLatency,
        avgNetLatencyVarianceMs,
        globalVideoStats,
        lastWindowVideoStats,
        activeWindowVideoStats
    )

    /**
     * @param portFlags ???
     */
    @JvmStatic
    fun stringifyPortFlags(portFlags : Int) : String? = implementation.stringifyPortFlags(portFlags)

    @JvmStatic
    fun logAndRecordException(t : Throwable) = implementation.logAndRecordException(t)

    /**
     * BAA-2353
     */
    @JvmStatic
    fun invertMotors(freqArray : IntArray) = implementation.invertMotors(freqArray)

    /**
     * BAA-2375
     */
    fun onStartNeuronGame() = implementation.onStartNeuronGame()

    /**
     * BAA-2375
     */
    fun onStopNeuronGame() = implementation.onStopNeuronGame()


    fun getLocalizedStageName(stage : String) = implementation.getLocalizedStageName(stage)


    fun getLocalizedStringFromErrorCode(errorCode : Int) = implementation.getLocalizedStringFromErrorCode(errorCode)

    @JvmStatic
    fun createAudioAttributesBuilder() = implementation.createAudioAttributesBuilder()

    @JvmStatic
    fun createAudioFormatBuilder() = implementation.createAudioFormatBuilder()

}


/**
 * DO NOT IMPORT com.razer.neuron.xxx classes here
 * DO NOT IMPORT com.razer.neuron.xxx classes here
 * Put the code in [RnNeuronBridgeImpl] instead
 */
object NoNeuronBridge : NeuronBridgeInterface {
    override val isMatchStreamViewResolutionToCurrentWindow = false

    override fun getLaunchUrlQueryParameters(connectionContext: ConnectionContext) = ""

    override fun getPairQueryParameters() = ""

    override fun fallbackVideoSettings(fallbackResolution: Size?, fallbackDisplayMode : DisplayModeOption?) = Unit

    override val isLaunchWithVirtualDisplay = false

    override fun getUserFacingMessage(t: Throwable) = t.message ?: ""

    override fun doVibrate(lowFreqMotor: Int, highFreqMotor: Int) = Unit

    override fun isRumbleWithNexus() : CompletableFuture<Boolean> = CompletableFuture.completedFuture(false)

    override fun updateVideoStats(
        randomId: String,
        decoder: String,
        initialSize: Size,
        avgNetLatency: Int,
        avgNetLatencyVarianceMs: Int,
        globalVideoStats: VideoStats?,
        lastWindowVideoStats: VideoStats?,
        activeWindowVideoStats: VideoStats?
    ) = Unit

    /**
     * @param portFlags ??? (see LiStringifyPortFlags)
     */
    override fun stringifyPortFlags(portFlags : Int) : String? = null

    override fun logAndRecordException(t : Throwable) = Unit

    override fun updateStreamConfiguration(connectionContext: ConnectionContext, decoderRenderer: MediaCodecDecoderRenderer) = Unit

    override fun invertMotors(freqArray : IntArray) = Unit

    /**
     * BAA-2375
     */
    override fun onStartNeuronGame() = Unit

    /**
     * BAA-2375
     */
    override fun onStopNeuronGame() = Unit

    override fun updatePreferenceConfiguration(activity: Activity, prefConfig: PreferenceConfiguration, intent : Intent) = Unit

    override fun getLocalizedStageName(stage: String) = stage

    override fun getLocalizedStringFromErrorCode(errorCode : Int) = "(error $errorCode)"

    override fun createAudioAttributesBuilder() = null

    override fun createAudioFormatBuilder() = null
}

