package com.razer.neuron.extensions

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Point
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.annotation.StringRes
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import com.razer.neuron.RnApp
import com.razer.neuron.utils.ANDROID_10
import com.razer.neuron.utils.isBelowOrEqual
import kotlin.math.roundToInt


fun View.gone() {
    visibility = View.GONE
}

fun View.isVisible() = visibility == View.VISIBLE
fun View.visibleIf(elseVisibility: Int = View.GONE, predicate: () -> Boolean) {
    visibility = if (predicate()) View.VISIBLE else elseVisibility
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.requestFocus(disableTouchModeFocus: Boolean = false): Boolean {
    /**
     * Set isFocusableInTouchMode to true before requestFocus() or requestFocus() will fail in touch mode.
     */
    return try {
        if (disableTouchModeFocus) {
            isFocusableInTouchMode = true
        }
        requestFocus()
    } finally {
        /**
         * Set isFocusableInTouchMode to false or onClick() will not be triggered when selecting the view by touch.
         */
        if (disableTouchModeFocus) {
            isFocusableInTouchMode = false
        }
    }
}

fun View.fadeOnStart(
    fadeToValue: Float,
    endVisibility: Int? = null,
    interpolator: Interpolator = DecelerateInterpolator()
): ValueAnimator =
    ValueAnimator.ofFloat(0f, 1f).apply {
        this.interpolator = interpolator
        var startAlpha = alpha
        doOnStart { startAlpha = this@fadeOnStart.alpha }
        addUpdateListener {
            val valueFloat = it.animatedValue as? Float ?: 1f
            alpha = startAlpha + (fadeToValue - startAlpha) * valueFloat
        }
        doOnEnd {
            if (endVisibility != null) {
                visibility = endVisibility
            }
        }
    }

fun getStringExt(@StringRes id: Int, vararg input: Any): String {
    return RnApp.appContext.getString(id, *input)
}

/**
 * Convert a Dimen reference [R.dimen._100dp] to pixels
 *
 * Return value is [Float]. Please use [Float.roundToInt] to get [Int] but it won't be precise
 */
@Px
fun dimenResToPx(@DimenRes id: Int) = RnApp.appContext.resources.getDimension(id)

fun dpToPx(dp: Float) = (dp * RnApp.appContext.resources.displayMetrics.density)

fun View.setOnlyPadding(
    start: Int? = null,
    top: Int? = null,
    end: Int? = null,
    bottom: Int? = null
) {
    setPaddingRelative(
        start ?: paddingStart,
        top ?: paddingTop,
        end ?: paddingEnd,
        bottom ?: paddingBottom,
    )
}


fun View.setOnlyMargin(
    start: Int? = null,
    top: Int? = null,
    end: Int? = null,
    bottom: Int? = null
) {
    updateLayoutParams {
        if (this is MarginLayoutParams) {
            marginStart = start ?: marginStart
            topMargin = top ?: marginTop
            marginEnd = end ?: marginEnd
            bottomMargin = bottom ?: marginBottom
        }
    }
}

fun View.setEnabledWithAlpha(isEnabled: Boolean, disableAlpha: Float = 0.5f) {
    setEnabled(isEnabled)
    alpha = if (isEnabled) 1f else disableAlpha
}


/**
 * Set size of a view (specify in px)
 */
fun View.setSizePx(widthPx: Int? = null, heightPx: Int? = null) {
    // even if layoutParams is null we cannot set it to null again
    this.layoutParams?.let { lp ->
        widthPx?.let {
            lp.width = it
        }
        heightPx?.let {
            lp.height = it
        }
        this.layoutParams = lp
    }
}


fun View.getSizePx(): Size {
    return Size(width, height)
}


fun KeyEvent.getViewFocusDirection(): Int? {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        else -> null
    }
}


/**
 * Attempt to get the insets of all the system bars (regardless if it is status bar or whatever)
 *
 */
fun View.getSystemBarInsets(): Insets? {
    return rootView?.let { ViewCompat.getRootWindowInsets(it) }?.mergeToInsets()
}

fun WindowInsetsCompat.mergeToInsets(): Insets {
    val windowInsets = this
    val displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
    var statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
    var navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
    if (isBelowOrEqual(ANDROID_10)) {
        if(statusBars.top == 0) {
            statusBars = Insets.of(statusBars.left, dpToPx(24f).roundToInt(),statusBars.right,statusBars.bottom)
        }
        if(navigationBars.right == 0) {
            navigationBars = Insets.of(navigationBars.left, navigationBars.top, dpToPx(48f).roundToInt(), navigationBars.bottom)
        }
    }

    val allInsets = listOf(statusBars, navigationBars, displayCutout)
    return Insets.of(
        allInsets.sumOf { it.left },
        allInsets.sumOf { it.top },
        allInsets.sumOf { it.right },
        allInsets.sumOf { it.bottom }
    )
}

fun Point.width(): Int {
    return this.x
}

fun Point.height(): Int {
    return this.y
}


fun View.setForegroundResource(@DrawableRes resid: Int) {
    ContextCompat.getDrawable(context, resid)?.let { foreground = it }
}