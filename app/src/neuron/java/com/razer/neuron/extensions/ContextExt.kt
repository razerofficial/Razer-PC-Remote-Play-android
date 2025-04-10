package com.razer.neuron.extensions

import android.app.Activity
import android.app.ActivityOptions
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import android.view.Display.HdrCapabilities
import android.view.InputDevice
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.razer.neuron.common.toast
import com.razer.neuron.nexus.SHARED_CONTENT_PROVIDER_DATA_ACCESS_PERMISSION
import com.razer.neuron.utils.API_LEVEL28
import com.razer.neuron.utils.API_LEVEL29
import com.razer.neuron.utils.API_LEVEL31
import com.razer.neuron.utils.checkSelfMultiplePermissions
import com.razer.neuron.utils.isAboveOrEqual
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * Check to see if an app([packageName]) is installed.
 *
 * If [fastCheck] then we will use this API feature here:
 * https://stackoverflow.com/a/46044612/6180539
 */
fun Context.isAppInstalled(packageName: String, fastCheck: Boolean = false): Boolean {
    return if (fastCheck) {
        try {
            packageManager.getPackageGids(packageName)
            true
        } catch (e: Throwable) {
            false
        }
    } else {
        packageManager.getPackageInfoInternal(packageName) != null
    }
}

data class AppVersion(val versionCode: Long, val versionName: String)

fun Context.getAppVersion(appPackageName: String) = try {
    with(
        checkNotNull(
            packageManager.getPackageInfo(
                appPackageName,
                PackageManager.GET_META_DATA
            )
        )
    ) {
        val versionCode =
            (if (isAboveOrEqual(API_LEVEL28)) longVersionCode else versionCode).toLong()
        AppVersion(versionCode, versionName)
    }
} catch (t: Throwable) {
    null
}


private val nexusPackages = setOf("com.razer.bianca", "com.razer.bianca.cn")
fun Context.getInstalledNexusPackage() = nexusPackages.firstOrNull { isAppInstalled(it) }

const val EXTRA_ANDROID_SPLASH_INT = "android.activity.splashScreenStyle"

fun Context.launchApp(appPackageName: String, bundle: Bundle? = null) = runCatching {
    val launchIntent = packageManager.getLaunchIntentForPackage(appPackageName)
        ?: throw UnsupportedOperationException("Cannot launch ${appPackageName}")
    val finalBundle = (bundle ?: ActivityOptions.makeBasic().toBundle())
        .apply {
            // https://issuetracker.google.com/issues/205021357#comment14
            putInt(EXTRA_ANDROID_SPLASH_INT, 1)
        }
    launchIntent.putExtras(finalBundle)
    ContextCompat.startActivity(
        if (this is Activity) this else applicationContext,
        launchIntent, finalBundle
    )
}


fun Uri?.openInBrowser(context: Context) {
    this ?: return // Do nothing if uri is null
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, this)
        ContextCompat.startActivity(context, browserIntent, null)
    } catch (e: ActivityNotFoundException) {
        e.message?.let { toast(it) }
    }
}

fun Context.hasNexusContentProviderPermission() = checkSelfMultiplePermissions(
    SHARED_CONTENT_PROVIDER_DATA_ACCESS_PERMISSION
)


fun Context.getDrawableExt(@DrawableRes id: Int) = AppCompatResources.getDrawable(this, id)


fun Context.windowManager() = getSystemService(Service.WINDOW_SERVICE) as WindowManager

fun Context.usbManager(): UsbManager? {
    return getSystemService(Context.USB_SERVICE) as? UsbManager
}

fun Context.getFullScreenSize(): Size {
    val wm = windowManager()
    return if (isAboveOrEqual(API_LEVEL31)) {
        wm.currentWindowMetrics.bounds.run { Size(width(), height()) }
    } else {
        val point = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(point)
        point.run { Size(x, y) }
    }
}

fun Context.getDisplayCutout(window: Window): List<Rect> {
    val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Use the much nicer Display.getCutout() API on Android 10+
        window.windowManager.defaultDisplay.cutout
    } else {
        // Android 9 only
        // Insets can be null when the activity is recreated on screen rotation
        // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
        val insets: WindowInsets? = window.decorView.rootWindowInsets
        insets?.displayCutout
    }
    return cutout?.boundingRects ?: emptyList()
}


fun Context.isHdrSupported() =
    runCatching { windowManager().defaultDisplay?.hdrCapabilities?.supportedHdrTypes?.any { hdrType -> hdrType == HdrCapabilities.HDR_TYPE_HDR10 } }.getOrNull()
        ?: false

fun Context.diagonalInches(): Double {
    val displayMetrics = resources.displayMetrics
    val screenResolution = getScreenResolution()
    val width = screenResolution.x
    val height = screenResolution.y
    val wi = width.toDouble() / displayMetrics.xdpi.toDouble()
    val hi = height.toDouble() / displayMetrics.ydpi.toDouble()
    val x = wi.pow(2.0)
    val y = hi.pow(2.0)
    return (Math.round((sqrt(x + y)) * 10.0) / 10.0)
}

fun Context.getPPI(): Double {
    val screenResolution = getScreenResolution()
    val width = screenResolution.x
    val height = screenResolution.y
    val diagonalPixels = sqrt((width * width + height * height).toDouble())
    return diagonalPixels / diagonalInches()
}

fun Context.getScreenResolution(): Point {
    val screenResolution = Point()
    val realMetrics = DisplayMetrics()
    windowManager().defaultDisplay.getRealMetrics(realMetrics)
    screenResolution.x = realMetrics.widthPixels
    screenResolution.y = realMetrics.heightPixels
    return screenResolution
}


fun Context.displayScale(): Float {
    val realMetrics = DisplayMetrics()
    windowManager().getDefaultDisplay().getMetrics(realMetrics)
    return realMetrics.scaledDensity
}


fun Context.getInputManger() = getSystemService(Context.INPUT_SERVICE) as InputManager

fun Context.hasGenericController() = getInputManger().hasGenericController()

fun InputManager.hasGenericController() =
    inputDeviceIds.any { id -> getInputDevice(id)?.isExternalGenericController() == true }

/**
 * For some devices, "input-fpc" (fingerprint sensor) has joystick source which breaks this function.
 * So we only checks for external devices for Android 29 and above, and non "input-fpc" devices for
 * Android 28 and below.
 */
fun InputDevice.isExternalGenericController() = if (
    (isAboveOrEqual(API_LEVEL29) && isExternal)
    || (!isAboveOrEqual(API_LEVEL29) && !name.equals("uinput-fpc"))
) {
    (sources and (InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || sources and (InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK)
} else {
    false
}


fun Context.connectivityManager() =
    getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager




