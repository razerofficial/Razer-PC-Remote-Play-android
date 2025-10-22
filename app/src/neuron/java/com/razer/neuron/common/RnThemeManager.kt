package com.razer.neuron.common

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.razer.neuron.common.BaseApplication.BaseActivityLifecycleCallbacks
import com.razer.neuron.extensions.dpToPx
import com.razer.neuron.extensions.fitSystemWindows
import com.razer.neuron.model.AppThemeType
import com.razer.neuron.model.DynamicThemeActivity
import com.razer.neuron.model.isUseWhiteSystemBarIcons
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.utils.ANDROID_10
import com.razer.neuron.utils.API_LEVEL28
import com.razer.neuron.utils.API_LEVEL29
import com.razer.neuron.utils.API_LEVEL30
import com.razer.neuron.utils.isAboveOrEqual
import com.razer.neuron.utils.isBelowOrEqual
import timber.log.Timber
import kotlin.math.max
import kotlin.math.roundToInt


object RnThemeManager {
    /**
     * Call when [application] was created
     */
    fun onApplicationCreated(application: Application) {
        application.initActivityLifeCycleCallback()
        /**
         * [BaseActivityLifecycleCallbacks.onActivityPreCreated] will not be called for API 28
         * but API 28 doesn't have dynamic color anyway, which means it will use razer color
         */
        if(isBelowOrEqual(API_LEVEL28)){
            AppCompatDelegate.setDefaultNightMode(AppThemeType.default().mode)
        }
    }

    fun appThemeType() = RemotePlaySettingsPref.appThemeType


    private val activityThemeModeCache = mutableMapOf<String, AppThemeType>()


    fun maybeRecreateOnThemeChange(activity: Activity) = activity._maybeRecreateOnThemeChange()


    private fun Activity._maybeRecreateOnThemeChange() : Boolean {
        val appThemeType = appThemeType()
        val createdActivityThemeMode = activityThemeModeCache[javaClass.name]
        /**
         * Must check [createdActivityThemeMode] is not null,
         * on older OS it is possible for [Application.ActivityLifecycleCallbacks.onActivityPreCreated]
         * to not get called and cause recursion to happen
         */
        return if(createdActivityThemeMode != null && createdActivityThemeMode != appThemeType) {
            Timber.v("maybeRecreate:${javaClass.simpleName} theme changed from $createdActivityThemeMode to ${appThemeType}")
            recreate()
            true
        } else {
            false
        }
    }


    private fun Application.initActivityLifeCycleCallback() {
        registerActivityLifecycleCallbacks(object : BaseActivityLifecycleCallbacks() {
            /**
             * Note, only added on API 29+
             */
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                if(activity !is DynamicThemeActivity) return
                if(isAboveOrEqual(API_LEVEL29)) {
                    val themeType = appThemeType()
                    Timber.v("onActivityPreCreated $themeType")
                    themeType.apply(activity)
                    activityThemeModeCache[activity.javaClass.name] = themeType
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if(activity !is DynamicThemeActivity) return
                if(activity._maybeRecreateOnThemeChange()) {
                    return
                }
                activity.setupStatusBar()
                activity.setupNavigationBar()
                activity.appThemeType.applyLightDarkMode()
            }
        })
    }
}

/**
 * This is to know if the OS has been set to use dark mode resources
 */
fun Context.isSystemInDarkMode(): Boolean {
    val currentNightMode = applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == Configuration.UI_MODE_NIGHT_YES
}

/**
 * This is to know if the [Activity] has been set to use dark mode resources
 * e.g
 * was it customized with [AppCompatDelegate.setDefaultNightMode]
 */
fun Activity.isActivityInDarkMode(): Boolean {
    val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == Configuration.UI_MODE_NIGHT_YES
}



/**
 * Update visibility of nav bar, its color and its icons color
 */
private fun Activity.setupNavigationBar() {
    // set background color
    window.navigationBarColor = colorSurface

    val isUseWhiteSystemBarIcons = (this as? DynamicThemeActivity)?.isUseWhiteSystemBarIcons
        ?: isUseWhiteSystemBarIcons(RnThemeManager.appThemeType())

    setNavigationBarIconColor(window, isUseWhiteSystemBarIcons)
}
/**
 * Set navigation bar ICON color (only ICON color, nothing else)
 */
fun setNavigationBarIconColor(window : Window, isUseWhiteSystemBarIcons : Boolean) {
    // set icon colors
    if (isUseWhiteSystemBarIcons) {
        if (isAboveOrEqual(API_LEVEL30)) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    } else {
        if (isAboveOrEqual(API_LEVEL30)) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
}

/**
 * Update visibility of status bar, its color and its icons color
 */
private fun Activity.setupStatusBar() {
    // set background color
    window.statusBarColor = colorSurface
    val isUseWhiteSystemBarIcons = (this as? DynamicThemeActivity)?.isUseWhiteSystemBarIcons ?: isUseWhiteSystemBarIcons(RnThemeManager.appThemeType())
    setStatusBarIconColor(window, isUseWhiteSystemBarIcons)
}


/**
 * Set status bar ICON color (only ICON color, nothing else)
 */
fun setStatusBarIconColor(window : Window, isUseWhiteSystemBarIcons : Boolean) {
    // set icon colors
    if (isUseWhiteSystemBarIcons) {
        if (isAboveOrEqual(API_LEVEL30)) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    } else {
        if (isAboveOrEqual(API_LEVEL30)) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }
}

/**
 * If you want an [Insets] to calculate where it is safe to draw your UI,
 * this is the function to call. This will return insets area whether
 * the bars are visible or not
 *
 * Mainly just trying to return [WindowInsetsCompat.getInsetsIgnoringVisibility] for
 * - Status bars
 * - Navigation bars
 * - Caption bars (used by tablet or desktop)
 * - Display cutout
 */
fun WindowInsetsCompat.toSystemBarInsets() : Insets {
    val mask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    val result = getInsetsIgnoringVisibility(mask)
    return if (isBelowOrEqual(ANDROID_10)) {
        // not really possible for status bar to have a size 0, unless WindowInsetsCompat was unable to get the inset
        Insets.of(result.left, max(result.top, dpToPx(24f).roundToInt()), result.right, result.bottom)
    } else {
        result
    }
}

/**
 * Attempt to get the insets of all the system bars (regardless if it is status bar or whatever)
 */
fun View.getSystemBarInsets() = rootView?.let { ViewCompat.getRootWindowInsets(it) }?.toSystemBarInsets()



fun Activity.showStatusBar(transparent : Boolean = false) = window.showStatusBar(transparent = transparent)
fun Activity.hideStatusBar() = window.hideStatusBar()
fun Activity.fitSystemWindows(isHideNavigationBar : Boolean = true, isLayoutNoLimits : Boolean) =
    window.fitSystemWindows(isHideNavigationBar = isHideNavigationBar, isLayoutNoLimits = isLayoutNoLimits)

fun Window.hideStatusBar() {
    WindowInsetsControllerCompat(
        this,
        decorView
    ).hide(WindowInsetsCompat.Type.statusBars())
}


fun Window.showStatusBar(transparent : Boolean = false) {
    WindowInsetsControllerCompat(
        this,
        decorView
    ).show(WindowInsetsCompat.Type.statusBars())
    if(transparent) {
        statusBarColor = Color.TRANSPARENT
    }
}



fun Activity.showNavigationBarsOnly() = window.showNavigationBarsOnly()
fun Activity.hideNavigationBarsOnly() = window.hideNavigationBarsOnly()

/**
 * [Window.fitSystemWindows] will hide other system bars also (intentional or not)
 *
 * Therefore [hideNavigationBarsOnly] will only hide the navigation bar
 */
fun Window.hideNavigationBarsOnly() = runCatching {
    WindowInsetsControllerCompat(this, this.decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}.logWarningOnError()

fun Window.showNavigationBarsOnly() = runCatching {
    WindowInsetsControllerCompat(this, this.decorView).let { controller ->
        controller.show(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
}.logWarningOnError()


/**
 * Use the entire screen for layout, including system bar area
 *
 * Layout will be overlapping by the system bars
 *
 * @param isHideNavigationBar if true them navigation bar will be hidden also
 */
fun Window.fitSystemWindows(isHideNavigationBar : Boolean = true, isLayoutNoLimits : Boolean = true) = runCatching {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    if(isHideNavigationBar) {
        hideNavigationBarsOnly()
    }
    if (isLayoutNoLimits) {
        this.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }
}.logWarningOnError()

