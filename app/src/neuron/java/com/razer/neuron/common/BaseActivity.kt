package com.razer.neuron.common

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.razer.neuron.extensions.getContentRootView
import com.razer.neuron.extensions.getScreenResolution
import com.razer.neuron.extensions.globalOnUnexpectedError
import com.razer.neuron.extensions.height
import com.razer.neuron.extensions.width
import com.razer.neuron.utils.PermissionChecker
import com.razer.neuron.utils.RequestMultiplePermissions
import com.razer.neuron.utils.checkSelfSinglePermission
import com.razer.neuron.utils.now
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

/**
 * Your [Activity] should always define as with android:fitsSystemWindows="true"
 */
@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {
    /**
     * If true [onSystemBarInsetsChanged] will be called.
     * If false, the system should draw everything within the system window
     */
    open val isRegisterSystemBarInsetsChange : Boolean = false

    open val onUnexpectedError = globalOnUnexpectedError

    private val requestMultiplePermissions = RequestMultiplePermissions(this)


    /**
     * Request permissions if not already granted.
     * [task] will be invoked with the [PermissionChecker.Result] of the permission request
     *
     * @param permissions permissions that you need (even though it might already be granted)
     * @param task task that you want to execute afterward
     *
     */
    suspend fun maybeRequestPermissions(
        vararg permissions: String,
    ): PermissionChecker.Result {
        val missing = permissions.toMutableList().filter { !checkSelfSinglePermission(it) }
        val permissionResult = if (missing.isEmpty()) {
            PermissionChecker.Result((permissions.map { it to PermissionChecker.State.Granted }).toMap())
        } else {
            requestMultiplePermissions.request(missing.toSet())
        }
        return permissionResult
    }

    private var lastUserInputAt = 0L
    private var isPaused = false
    private var onReview : ((Long) -> Unit)? = null
    private var reviewTimerJob : Job? = null
    private var minWaitBeforeReviewDurationMs = 5000L

    /**
     * Allow the [requestReview] when [onReview] should be called
     */
    fun setReviewAllowed(enabled : Boolean) {
        onReview = if(enabled) {
            {
                lifecycleScope.launch {
                    if(isShowInAppReview()) requestReview()
                }
            }
        } else {
            null
        }
    }

    private fun startReviewTimer() {
        reviewTimerJob?.cancel()
        val startAt = now()
        if(onReview == null) return
        reviewTimerJob = lifecycleScope.launch {
            delay(minWaitBeforeReviewDurationMs)
            if(!isPaused) {
                onReview?.invoke(now() - startAt)
            }
        }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        lastUserInputAt = now()
        startReviewTimer()
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastUserInputAt = now()
        startReviewTimer()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        lastUserInputAt = now()
        startReviewTimer()
        return super.dispatchTouchEvent(ev)
    }


    private var lastSystemBarInsetsApplied : Insets? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rootView = getContentRootView() ?: return
        if(isRegisterSystemBarInsetsChange) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                val systemBarInsets = insets.toSystemBarInsets()
                if (lastSystemBarInsetsApplied != systemBarInsets) {
                    lastSystemBarInsetsApplied = systemBarInsets
                    Timber.v("onSystemBarInsetsChanged: ${insets.toSystemBarInsets()}")
                    onSystemBarInsetsChanged(systemBarInsets)
                }
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    /**
     * Called when [com.razer.neuron.common.getSystemBarInsets] which is expected to change in:
     * - Size
     * - Position
     * - Color?
     * Also called on initial start of the [Activity]
     *
     * Will not be called if [isRegisterSystemBarInsetsChange] is false at the time of [onCreate]
     */
    open fun onSystemBarInsetsChanged(insets: Insets) = Unit

    override fun onResume() {
        isPaused = false
        startReviewTimer()
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        reviewTimerJob?.cancel()
        isPaused = true
    }

}

fun Activity.viewTreeObserver() = window?.decorView?.viewTreeObserver

fun Fragment.viewTreeObserver() = activity?.viewTreeObserver()


fun ViewTreeObserver.removeOnGlobalLayoutListenerSafely(victim: OnGlobalLayoutListener) =
    runCatching {
        removeOnGlobalLayoutListener(victim)
    }


const val defaultMidLinePercent = 0.363f
const val flatFoldableMidLinePercent = 0.5f

/**
 * Get the percentage of screen width that [com.razer.neuron.settings.RnNavigationView3] should take.
 *
 * Normally, in Pixel tablet this should be 36%. In Samsung this percent is not the same as
 * Pixel, so there is no point in customizing it for Samsung.
 *
 * However, in foldable (once opened) the percent should be 50%.
 */
fun Context.getMidLinePercent(defaultPercent : Float = defaultMidLinePercent) : Float {
    val screenRes = getScreenResolution().takeIf { it.width() > 0 && it.height() > 0} ?: return defaultPercent
    val ratioWH = 1f * screenRes.width() / screenRes.height()
    return when {
        abs(1f - ratioWH) < 0.25 -> flatFoldableMidLinePercent
        else -> defaultPercent
    }
}