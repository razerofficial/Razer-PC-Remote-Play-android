package com.razer.neuron.common

import android.app.Activity
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.razer.neuron.extensions.getContentRootView
import com.razer.neuron.extensions.globalOnUnexpectedError
import com.razer.neuron.utils.PermissionChecker
import com.razer.neuron.utils.RequestMultiplePermissions
import com.razer.neuron.utils.checkSelfSinglePermission
import com.razer.neuron.utils.now
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {

    sealed interface ActivityWindowInsets {
        val boundingRectLeftWidth: Int?
        val boundingRectRightWidth: Int?
        val boundingRectTopHeight: Int?
        val boundingRectBottomHeight: Int?

        @RequiresApi(Build.VERSION_CODES.Q)
        class DetailInsets(val windowInset: WindowInsets) : ActivityWindowInsets {
            override val boundingRectLeftWidth get() = windowInset.displayCutout?.boundingRectLeft?.width()
            override val boundingRectRightWidth get() = windowInset.displayCutout?.boundingRectRight?.width()
            override val boundingRectTopHeight get() = windowInset.displayCutout?.boundingRectTop?.height()
            override val boundingRectBottomHeight get() = windowInset.displayCutout?.boundingRectBottom?.height()
            override fun toString() =
                "DetailInsets($boundingRectLeftWidth,$boundingRectTopHeight,$boundingRectRightWidth,$boundingRectBottomHeight)"
        }

        class BasicInsets(val windowInset: WindowInsetsCompat) : ActivityWindowInsets {
            override val boundingRectLeftWidth get() = windowInset.systemWindowInsets.left
            override val boundingRectRightWidth get() = windowInset.systemWindowInsets.right
            override val boundingRectTopHeight get() = windowInset.systemWindowInsets.top
            override val boundingRectBottomHeight get() = windowInset.systemWindowInsets.bottom
            override fun toString() =
                "BasicInsets($boundingRectLeftWidth,$boundingRectTopHeight,$boundingRectRightWidth,$boundingRectBottomHeight)"
        }
    }

    open val onUnexpectedError = globalOnUnexpectedError

    private val requestMultiplePermissions = RequestMultiplePermissions(this)

    private var onGlobalLayoutListener: InnerOnGlobalLayoutListener? = null

    inner class InnerOnGlobalLayoutListener : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            val rootView = getContentRootView() ?: return
            val windowInsets = rootView.rootWindowInsets
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowInsets?.let { onInsetsChange(ActivityWindowInsets.DetailInsets(it)) }
            } else {
                val windowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
                onInsetsChange(ActivityWindowInsets.BasicInsets(windowInsetsCompat))
            }
            if (onGlobalLayoutListener == this) {
                rootView.viewTreeObserver?.removeOnGlobalLayoutListenerSafely(this)
                onGlobalLayoutListener = null
            }
        }
    }

    override fun onStart() {
        super.onStart()
        onGlobalLayoutListener = InnerOnGlobalLayoutListener().apply {
            getContentRootView()?.viewTreeObserver?.addOnGlobalLayoutListener(this)
        }
    }

    override fun onStop() {
        super.onStop()
        onGlobalLayoutListener?.let {
            getContentRootView()?.viewTreeObserver?.removeOnGlobalLayoutListenerSafely(it)
            onGlobalLayoutListener = null
        }
    }

    /**
     * When the [ActivityWindowInsets] is first calculated or when there is a change
     */
    open fun onInsetsChange(insets: ActivityWindowInsets) = Unit


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