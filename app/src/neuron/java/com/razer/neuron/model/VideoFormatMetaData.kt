package com.razer.neuron.model

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.preferences.PreferenceConfiguration
import com.razer.neuron.extensions.connectivityManager
import com.razer.neuron.extensions.mediaCodecDecoderRenderer
import com.razer.neuron.extensions.prefConfig
import com.razer.neuron.extensions.willStreamHdr
import timber.log.Timber

/**
 * A meta data object to expose the supported decoder based on logic in [MediaCodecDecoderRenderer]
 */
data class VideoFormatMetaData(
    @SerializedName("is_avc_supported")
    val isAvcSupported: Boolean = false,
    @SerializedName("avc_decoder")
    val avcDecoderName: String? = null,
    @SerializedName("is_av1_supported")
    val isAV1Supported: Boolean = false,
    @SerializedName("av1_decoder")
    val av1DecoderName: String? = null,
    @SerializedName("is_av1_main10_supported")
    val isAv1Main10Supported: Boolean = false,
    @SerializedName("is_hevc_supported")
    val isHevcSupported: Boolean = false,
    @SerializedName("hevc_decoder")
    val hevcDecoderName: String? = null,
    @SerializedName("is_hevc_main10_hdr10_supported")
    val isHevcMain10Hdr10Supported: Boolean = false
) {

    companion object {
        fun create(
            context: Context,
            prefConfig: PreferenceConfiguration = context.prefConfig()
        ): VideoFormatMetaData {
            Timber.v("VideoFormatMetaData.create")
            // -----------------------AVC (H264)---------------------
            prefConfig.enableHdr = false
            prefConfig.videoFormat = PreferenceConfiguration.FormatOption.FORCE_H264
            var mcdr = context.mediaCodecDecoderRenderer(prefConfig = prefConfig)
            val isAvcSupported = mcdr.isAvcSupported
            val avcDecoderName = mcdr.findAvcDecoder()?.name
            // -----------------------HEVC (H265)-------------------------
            prefConfig.videoFormat = PreferenceConfiguration.FormatOption.FORCE_AV1
            prefConfig.enableHdr = false
            mcdr = context.mediaCodecDecoderRenderer(prefConfig = prefConfig)
            val isAV1Supported = mcdr.isAv1Supported
            val av1DecoderName = mcdr.findAv1Decoder(prefConfig)?.name
            prefConfig.enableHdr = true
            mcdr = context.mediaCodecDecoderRenderer(prefConfig = prefConfig)
            val isAv1Main10Supported = mcdr.isAv1Main10Supported
            // ------------------------ AV1 ------------------------
            prefConfig.videoFormat = PreferenceConfiguration.FormatOption.FORCE_HEVC
            prefConfig.enableHdr = false
            mcdr = context.mediaCodecDecoderRenderer(prefConfig = prefConfig)
            val isHevcSupported = mcdr.isHevcSupported
            val hevcDecoderName = mcdr.findHevcDecoder(prefConfig, context.connectivityManager().isActiveNetworkMetered, context.willStreamHdr(prefConfig))?.name
            prefConfig.enableHdr = true
            mcdr = context.mediaCodecDecoderRenderer(prefConfig = prefConfig)
            val isHevcMain10Hdr10Supported = mcdr.isHevcMain10Hdr10Supported
            val a = VideoFormatMetaData(
                isAvcSupported = isAvcSupported,
                avcDecoderName = avcDecoderName,
                isAV1Supported = isAV1Supported,
                av1DecoderName = av1DecoderName,
                isAv1Main10Supported = isAv1Main10Supported,
                isHevcSupported = isHevcSupported,
                hevcDecoderName = hevcDecoderName,
                isHevcMain10Hdr10Supported = isHevcMain10Hdr10Supported,
            )
            Timber.v("VideoFormatMetaData.create ${a}")
            return a
        }
    }
}