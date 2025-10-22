package com.razer.neuron.extensions

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import android.view.DisplayCutout
import android.view.Window
import android.view.WindowInsets
import com.razer.neuron.RnApp
import com.razer.neuron.pref.RemotePlaySettingsPref
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


data class NativeResolution(val width: Int, val height: Int, val hasInsets: Boolean) {
    val pixelsCount = width * height
    val isHigherThan4K get() = (width > 3840 || width > 2160)
}


val standardResolutions by lazy {
    listOf(
        Size(640, 360),
        Size(854, 480),
        Size(1280, 720),
        Size(1920, 1080),
        Size(2560, 1440),
        Size(3840, 2160),
    )
}

/**
 * See BAA-2656
 *
 * @return true if the current screen resolution is already the maximum that the device can support
 */
fun isCurrentNativeResolutionAlreadyMax(window: Window) : Boolean {
    val display =
        (window.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    val currentMode = display.mode ?: return false
    val supportedModes = display.supportedModes.takeIf { it.isNotEmpty() } ?: return true
    val maxSupportedMode = supportedModes.maxByOrNull { it.physicalWidth * it.physicalHeight } ?: return false
    return currentMode.physicalWidth == maxSupportedMode.physicalWidth
            && currentMode.physicalHeight == maxSupportedMode.physicalHeight
}


/**
 * See BAA-1887
 */
fun getNativeResolutions(window: Window, isOnlyUseCurrentNativeRes : Boolean = RemotePlaySettingsPref.isOnlyUseCurrentNativeRes): Set<NativeResolution> {
    val context = window.context
    val display =
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    val nativeResolutions = mutableSetOf<NativeResolution>()
    var hasInsets = false

    val cutout: DisplayCutout? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Use the much nicer Display.getCutout() API on Android 10+
        window.windowManager.defaultDisplay.cutout
    } else {
        // Android 9 only
        // Insets can be null when the activity is recreated on screen rotation
        // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
        val insets: WindowInsets = window.decorView.rootWindowInsets
        insets.displayCutout
    }

    if (cutout != null) {
        val widthInsets = cutout.safeInsetLeft + cutout.safeInsetRight
        val heightInsets = cutout.safeInsetBottom + cutout.safeInsetTop
        if (widthInsets != 0 || heightInsets != 0) {
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            val width = max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets)
            val height = min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets)
            nativeResolutions += NativeResolution(width, height, false)
            hasInsets = true
        }
    }


    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun Display.Mode.toNativeResolution() : NativeResolution {
        // Some devices report their dimensions in the portrait orientation
        // where height > width. Normalize these to the conventional width > height
        // arrangement before we process them.
        val width = max(physicalWidth, physicalHeight)
        val height = min(physicalWidth, physicalHeight)
        return NativeResolution(width, height, hasInsets)
    }

    val nativeResList = if(isOnlyUseCurrentNativeRes) listOf(display.mode.toNativeResolution()) else display.supportedModes.map { it.toNativeResolution() }
    for (nativeRes in nativeResList) {
        // Some TVs report strange values here, so let's avoid native resolutions on a TV
        // unless they report greater than 4K resolutions.
        if(!isTv || nativeRes.isHigherThan4K){
            nativeResolutions += nativeRes
        }
    }
    return nativeResolutions
}


fun Size.isStandardResolution() = standardResolutions.contains(this)


fun getAllSupportedNativeFps(window: Window): Set<Int> {
    val context = window.context
    val display =
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    val supportedFps = mutableSetOf<Int>()
    display.supportedModes.forEach {
        supportedFps += it.refreshRate.roundToInt()
    }
    return if(supportedFps.isEmpty()) setOf(display.refreshRate.roundToInt()) else supportedFps
}

fun getMaxNativeFps(window: Window): Int {
    val context = window.context
    val display =
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    var maxSupportedFps: Int = display.refreshRate.roundToInt()

    display.supportedModes.forEach {
        if (it.refreshRate > maxSupportedFps) {
            maxSupportedFps = it.refreshRate.roundToInt()
        }
    }

    if (maxSupportedFps == 0) {
        return display.refreshRate.roundToInt()
    }
    return maxSupportedFps
}