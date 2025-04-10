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
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors
import com.limelight.R
import com.razer.neuron.common.BaseApplication.BaseActivityLifecycleCallbacks
import com.razer.neuron.extensions.hideNavigationBars
import com.razer.neuron.extensions.hideStatusBar
import com.razer.neuron.game.RnGame
import com.razer.neuron.main.RnMainActivity
import com.razer.neuron.model.AppThemeType
import com.razer.neuron.model.DynamicThemeActivity
import com.razer.neuron.model.isUseWhiteSystemBarIcons
import com.razer.neuron.oobe.RnOobeActivity
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.settings.RnSettingsActivity
import com.razer.neuron.utils.API_LEVEL28
import com.razer.neuron.utils.API_LEVEL29
import com.razer.neuron.utils.API_LEVEL30
import com.razer.neuron.utils.isAboveOrEqual
import com.razer.neuron.utils.isBelowOrEqual
import okhttp3.internal.toHexString
import timber.log.Timber


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



private const val defaultSystemBarBgColor = Color.BLACK
private const val defaultSystemBarFgColor = Color.WHITE

private fun Activity.setupNavigationBar() {
    if (this is DynamicThemeActivity && isThemeFullscreen) {
        hideNavigationBars()
        return
    }
    // set background color
    window.navigationBarColor =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, defaultSystemBarBgColor)

    val isUseWhiteSystemBarIcons = (this as? DynamicThemeActivity)?.isUseWhiteSystemBarIcons
        ?: isUseWhiteSystemBarIcons(RnThemeManager.appThemeType())

    setNavigationBarIconColor(window, isUseWhiteSystemBarIcons)
}

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


private fun Activity.setupStatusBar() {
    if (this is DynamicThemeActivity && isThemeFullscreen) {
        hideStatusBar()
        return
    }
    // set background color
    window.statusBarColor =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, defaultSystemBarBgColor)

    val isUseWhiteSystemBarIcons = (this as? DynamicThemeActivity)?.isUseWhiteSystemBarIcons ?: isUseWhiteSystemBarIcons(RnThemeManager.appThemeType())

    setStatusBarIconColor(window, isUseWhiteSystemBarIcons)
}

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