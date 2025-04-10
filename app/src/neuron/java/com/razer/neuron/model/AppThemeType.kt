package com.razer.neuron.model

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.NightMode
import com.google.android.material.color.DynamicColors
import com.limelight.R
import com.razer.neuron.RnApp
import com.razer.neuron.common.RnThemeManager
import com.razer.neuron.common.doOnError
import timber.log.Timber
import com.razer.neuron.common.isActivityInDarkMode

import com.razer.neuron.utils.API_LEVEL31
import com.razer.neuron.utils.isAboveOrEqual

enum class AppThemeType(
    @StringRes
    val title: Int,
    /**
     * true if this [AppThemeType] is meant to use dynamic color (from system)
     */
    val isUseDynamicColors: Boolean,
    /**
     * For status bar icon color
     */
    @NightMode
    val mode: Int,
    @StyleRes
    val defaultThemeId: Int,
    @StyleRes
    val settingsThemeId: Int,
    @StyleRes
    val splashThemeId: Int,
    @StyleRes
    val gameThemeId: Int,
) {
    Razer(
        title = R.string.rn_theme_razer,
        isUseDynamicColors = false,
        mode = AppCompatDelegate.MODE_NIGHT_YES,
        defaultThemeId = R.style.AppTheme_Razer,
        settingsThemeId = R.style.AppTheme_Razer_Settings,
        splashThemeId = R.style.AppTheme_Razer_Splash,
        gameThemeId = R.style.AppTheme_Razer_Game,
    ),
    System(
        title = R.string.rn_theme_system,
        isUseDynamicColors = true,
        mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        defaultThemeId = R.style.AppTheme_DynamicColors_System,
        settingsThemeId = R.style.AppTheme_DynamicColors_System_Settings,
        splashThemeId = R.style.AppTheme_DynamicColors_System_Splash,
        gameThemeId = R.style.AppTheme_DynamicColors_System_Game,
    ),
    Light(
        title = R.string.rn_theme_light,
        isUseDynamicColors = true,
        mode = AppCompatDelegate.MODE_NIGHT_NO,
        defaultThemeId = R.style.AppTheme_DynamicColors_Light,
        settingsThemeId = R.style.AppTheme_DynamicColors_Light_Settings,
        splashThemeId = R.style.AppTheme_DynamicColors_Light_Splash,
        gameThemeId = R.style.AppTheme_DynamicColors_Light_Game,
    ),
    Dark(
        title = R.string.rn_theme_dark,
        isUseDynamicColors = true,
        mode = AppCompatDelegate.MODE_NIGHT_YES,
        defaultThemeId = R.style.AppTheme_DynamicColors_Dark,
        settingsThemeId = R.style.AppTheme_DynamicColors_Dark_Settings,
        splashThemeId = R.style.AppTheme_DynamicColors_Dark_Splash,
        gameThemeId = R.style.AppTheme_DynamicColors_Dark_Game,
    );

    companion object {

        /**
         * Get a [AppThemeType] where [apply] will work
         * NEUR-106
         */
        fun default() = Razer

        /**
         * Use this instead of [DynamicColors.isDynamicColorAvailable]
         */
        fun isDynamicColorAvailable2() =
            DynamicColors.isDynamicColorAvailable() || isDynamicColorAvailableUnofficially()

        /**
         * See BAA-2200, add more if needed
         */
        private val supportedBrands = setOf("Razer").map { it.lowercase() }

        /**
         * See BAA-2200, add more if needed
         */
        private val supportedManufacturers = setOf("Razer").map { it.lowercase() }

        /**
         * See BAA-2200
         *
         * Check OS version and check [supportedBrands] and [supportedManufacturers]
         */
        private fun isDynamicColorAvailableUnofficially() = isAboveOrEqual(API_LEVEL31) && (
                supportedBrands.contains(Build.BRAND.lowercase())
                        || supportedManufacturers.contains(Build.MANUFACTURER.lowercase()))

    }

    /**
     * As per Greg's requirement, if [DynamicColors] was not applied (and we
     * are forced to use Razer color) then we force it to night mode
     */
    fun apply(activity: Activity) {
        if(activity !is DynamicThemeActivity) return

        runCatching { activity.setTheme(activity.getThemeId()) }
            .doOnError {
                Timber.w(it)
            }

        if (isUseDynamicColors) {
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(activity)
            }
        }

        applyLightDarkMode()
        Timber.v("$name.apply(${activity.javaClass.simpleName}): finalTheme=${activity.appThemeType.name} MODEL=${Build.MODEL},MANUFACTURER=${Build.MANUFACTURER},BRAND=${Build.BRAND}  DynamicColors.isDynamicColorAvailable=${DynamicColors.isDynamicColorAvailable()}")
    }


    fun applyLightDarkMode() {
        AppCompatDelegate.setDefaultNightMode(mode)
        Timber.v("${this}: applyLightDarkMode, mode=$mode")
    }
}

interface DynamicThemeActivity {
    val isThemeTransparentBackground get() = false

    val isThemeFullscreen get() = false

    val isUseWhiteSystemBarIcons get() = RnApp.appContextOrNull?.isUseWhiteSystemBarIcons(appThemeType) ?: false

    val appThemeType get() = RnThemeManager.appThemeType()

    fun getThemeId() = appThemeType.defaultThemeId
}


fun Context.isUseWhiteSystemBarIcons(appThemeType: AppThemeType) : Boolean {
    return when (appThemeType) {
        AppThemeType.Razer, AppThemeType.Dark -> true
        AppThemeType.Light -> false
        AppThemeType.System -> isSystemDarkMode()
    }
}

fun Context.isSystemDarkMode() = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
    Configuration.UI_MODE_NIGHT_YES -> true
    Configuration.UI_MODE_NIGHT_NO -> false
    else -> false
}