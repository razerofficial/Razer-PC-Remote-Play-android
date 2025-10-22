package com.razer.neuron.common

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import com.limelight.R
import com.razer.neuron.utils.randomInt
import kotlin.random.Random


val Context.colorOnBackground
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorOnBackground,
        R.color.md_theme_onBackground
    )

val Context.colorOutline
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorOutline,
        R.color.md_theme_outline
    )

val Context.colorBackground
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurface,
        R.color.md_theme_background
    )


val Context.colorOnSurface
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorOnSurface,
        R.color.md_theme_onSurface
    )

val Context.colorSurfaceDim
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurfaceDim,
        R.color.md_theme_surfaceDim
    )

val Context.colorSurface
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurface,
        R.color.md_theme_surface
    )


val Context.colorSurfaceContainerHighest
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurfaceContainerHighest,
        R.color.md_theme_surfaceContainerHighest
    )

val Context.colorSurfaceContainerHigh
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurfaceContainerHigh,
        R.color.md_theme_surfaceContainerHigh
    )

val Context.colorSurfaceContainer
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurfaceContainer,
        R.color.md_theme_surfaceContainer
    )


val Context.colorSurfaceContainerLow
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurfaceContainerLow,
        R.color.md_theme_surfaceContainerLow
    )

val Context.colorSurfaceContainerLowest
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorSurfaceContainerLowest,
        R.color.md_theme_surfaceContainerLowest
    )



val Context.colorOnPrimaryContainer
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorOnPrimaryContainer,
        R.color.md_theme_onPrimaryContainer
    )

val Context.colorPrimaryContainer
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorPrimaryContainer,
        R.color.md_theme_primaryContainer
    )

val Context.colorOnPrimary
    get() = getMaterialColor(
        com.google.android.material.R.attr.colorOnPrimary,
        R.color.md_theme_onPrimary
    )

val Context.colorPrimary
    get() = getMaterialColor(
        androidx.appcompat.R.attr.colorPrimary,
        R.color.md_theme_primary
    )

/**
 * https://stackoverflow.com/a/70605825
 */
fun Context.getMaterialColor(@AttrRes googleMaterialAttr: Int, @ColorRes fallbackColor: Int) =
    when {
        DynamicColors.isDynamicColorAvailable() -> getColorFromAttr(googleMaterialAttr)
        else -> ContextCompat.getColor(this, fallbackColor)
    }


/**
 * https://stackoverflow.com/a/70605825
 */
@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun materialAlertDialogTheme() = R.style.RnAlertDialogDefault