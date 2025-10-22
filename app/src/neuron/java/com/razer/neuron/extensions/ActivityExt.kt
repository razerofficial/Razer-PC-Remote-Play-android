package com.razer.neuron.extensions

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.transition.Fade
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.razer.neuron.common.logWarningOnError
import timber.log.Timber



fun Activity.setDrawIntoSafeArea(isDrawIntoSafeArea : Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            if(isDrawIntoSafeArea)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
    }

    // false means we draw into the system area
    WindowCompat.setDecorFitsSystemWindows(window, !isDrawIntoSafeArea)
    getContentRootView()?.let {
        mainContainer ->
        WindowInsetsControllerCompat(window, mainContainer).let { controller ->
            if(isDrawIntoSafeArea) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                //controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    if(isDrawIntoSafeArea) {
        // In multi-window mode on N+, we need to drop our layout flags or we'll
        // be drawing underneath the system UI.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
            window.decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        } else {
            // Use immersive mode
            window.decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    } else{
        // If we're going to use immersive mode, we want to have
        // the entire screen
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

}

fun Activity.hideStatusBar() {
    WindowInsetsControllerCompat(
        window,
        window.decorView
    ).hide(WindowInsetsCompat.Type.statusBars())
}

fun Activity.showNavigationBarsOnly() = window.showNavigationBarsOnly()
fun Activity.hideNavigationBarsOnly() = window.hideNavigationBarsOnly()
fun Activity.fitSystemWindows(isHideNavigationBar : Boolean = true) = window.fitSystemWindows(isHideNavigationBar = isHideNavigationBar)


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



fun Window.fitSystemWindows(isHideNavigationBar : Boolean = true) = runCatching {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    if(isHideNavigationBar) {
        hideNavigationBarsOnly()
    }
    this.setFlags(
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )
}.logWarningOnError()


fun Activity.hideSystemUI(mainContainer: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, mainContainer).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}


fun Activity.showSystemUI(mainContainer: View) {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowInsetsControllerCompat(
        window, mainContainer
    ).show(WindowInsetsCompat.Type.systemBars())
}

fun Activity.showKeyboard(view: View, flags: Int = InputMethodManager.SHOW_IMPLICIT) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(view, flags)
}

fun Activity.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}


fun Activity.getContentRootView(): View? {
    return try {
        findViewById<View>(android.R.id.content)?.rootView
    } catch (t: Throwable) {
        Timber.w(t)
        null
    }
}


/**
 * Call [Dialog.dismiss] if there is any exception, it will be part of the return [Result]
 *
 * In most cases, you can ignore the [Result].
 *
 * @param onlyIfShowing should be true if you only want to dismiss when [Dialog.isShowing] is also true
 *
 * @return true in [Result] if [Dialog.dismiss] was called (can ignore)
 */
fun Dialog?.dismissSafely(onlyIfShowing : Boolean = false) : Result<Boolean> {
    return runCatching {
        this?.let {
            val isShowing = this.isShowing
            if(!onlyIfShowing || isShowing) {
                it.dismiss()
                Timber.v("dismissSafely: Dialog ${this} dismissed")
                true
            } else {
                Timber.v("dismissSafely: Dialog ${this} not dismiss. (isShowing=$isShowing,onlyIfShowing=$onlyIfShowing)")
                false
            }
        } ?: run {
            Timber.v("dismissSafely: Dialog is null")
            false
        }
    }
}

fun Activity.applyTransition() {
    with(window) {
        requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        // Set an exit transition
        exitTransition = Fade()
        enterTransition = Fade()
    }
}