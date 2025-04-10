package com.razer.neuron.extensions

import android.app.Activity
import android.content.Context
import android.view.Display
import android.view.Display.HdrCapabilities
import com.limelight.binding.video.CrashListener
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.preferences.GlPreferences
import com.limelight.preferences.PreferenceConfiguration
import com.razer.neuron.common.logAndRecordException

fun Context.glPreferences() : GlPreferences = GlPreferences.readPreferences(this)


fun Context.willStreamHdr(prefConfig: PreferenceConfiguration): Boolean {
    // Check if the user has enabled HDR
    var willStreamHdr = false
    if (prefConfig.enableHdr) {
        // Start our HDR checklist
        val display: Display = windowManager().getDefaultDisplay()
        val hdrCaps = display.hdrCapabilities

        // We must now ensure our display is compatible with HDR10
        if (hdrCaps != null) {
            // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
            for (hdrType in hdrCaps.supportedHdrTypes) {
                if (hdrType == HdrCapabilities.HDR_TYPE_HDR10) {
                    willStreamHdr = true
                    break
                }
            }
        }
    }
    return willStreamHdr
}

/**
 * @return a [PreferenceConfiguration] using [PreferenceConfiguration.readPreferences]
 */
fun Context.prefConfig() : PreferenceConfiguration = PreferenceConfiguration.readPreferences(this)


/**
 * Create a default instance of [MediaCodecDecoderRenderer]
 *
 * @param prefConfig must remain immutable, since [MediaCodecDecoderRenderer] will be constructed based on the
 * values in [PreferenceConfiguration] at the time of creation
 */
fun Context.mediaCodecDecoderRenderer(
    prefConfig: PreferenceConfiguration = prefConfig(),
    glPrefs: GlPreferences = glPreferences(),
    crashListener: CrashListener = CrashListener { logAndRecordException(it) },
    onPerfUpdate: PerfOverlayListener = PerfOverlayListener {}
): MediaCodecDecoderRenderer {
    MediaCodecHelper.initialize(this, glPrefs.glRenderer)
    return MediaCodecDecoderRenderer(
        this,
        prefConfig,
        crashListener,
        0,
        connectivityManager().isActiveNetworkMetered,
        willStreamHdr(prefConfig),
        glPrefs.glRenderer,
        onPerfUpdate
    )
}


