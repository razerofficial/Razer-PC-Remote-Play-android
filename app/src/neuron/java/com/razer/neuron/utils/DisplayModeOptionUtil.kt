package com.razer.neuron.utils

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Window
import androidx.core.content.edit
import com.limelight.R
import com.limelight.nvstream.http.DisplayMode
import com.limelight.preferences.PreferenceConfiguration
import com.razer.neuron.RnApp
import com.razer.neuron.extensions.getAllSupportedNativeFps
import com.razer.neuron.extensions.getMaxNativeFps
import com.razer.neuron.extensions.getNativeResolutions
import com.razer.neuron.extensions.windowManager
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.shared.SharedConstants
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt


fun Context.getDefaultDisplayRefreshRateHz(isCheckMoonlightSupported : Boolean = false) : Int {
    val refreshRate = windowManager().defaultDisplay.refreshRate
    val refreshRateCoerceIn = if (isCheckMoonlightSupported) {
        resources.getStringArray(R.array.fps_values).mapNotNull { it.toIntOrNull() }.minByOrNull { abs(it - refreshRate) }
            ?: PreferenceConfiguration.DEFAULT_FPS.toInt()
    } else {
        refreshRate.roundToInt()
    }
    Timber.v("getDefaultDisplayRefreshRateHz: $refreshRate -> $refreshRateCoerceIn")
    return refreshRateCoerceIn
}

/**
 * Used to determine the [DisplayMode] used by virtual display (i.e. [DisplayModeOption.isUsesVirtualDisplay] is true)
 */
fun Activity.calculateVirtualDisplayMode(isLimitRefreshRate : Boolean = RemotePlaySettingsPref.isLimitRefreshRate) = runCatching {
    val tag = "calculateVirtualDisplayMode"

    val currentRefreshRate = getDefaultDisplayRefreshRateHz()

    val defaultFps = window?.let { getMaxNativeFps(it) } ?: PreferenceConfiguration.DEFAULT_FPS.toInt()

    val nativeFps = window?.let { getAllSupportedNativeFps(it) } ?: emptySet()
    val preferredNativeFps = nativeFps.minByOrNull { abs(currentRefreshRate - it) } ?: defaultFps
    val virtualDisplayFps = if(isLimitRefreshRate) defaultFps else preferredNativeFps


    val nativeResolutions = window?.let { getNativeResolutions(it) } ?: emptySet()
    val preferredNativeResolution =
        nativeResolutions.maxByOrNull { it.pixelsCount } ?: error("nativeResolutions is empty")

    DisplayMode.createDisplayMode(preferredNativeResolution.width, preferredNativeResolution.height, virtualDisplayFps)
}