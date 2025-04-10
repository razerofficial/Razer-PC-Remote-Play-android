package com.razer.neuron.game.helpers

import android.animation.Animator
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limelight.BuildConfig
import com.limelight.R
import com.razer.neuron.game.RnGame
import timber.log.Timber
import com.razer.neuron.common.materialAlertDialogTheme
import com.razer.neuron.common.toast

import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.fadeOnStart
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.visible
import com.razer.neuron.extensions.visibleIf
import kotlinx.coroutines.launch

/**
 * A helper class with functions show various UI on [RnGame]
 */
class RnGameView(private val activity: ComponentActivity) {
    private val lifecycleScope get() = activity.lifecycleScope
    private val resources get() = activity.resources

    private val loadingBtn : Button by lazy { activity.findViewById(R.id.btn_loading) }
    private val loadingLayout: ViewGroup by lazy { activity.findViewById(R.id.layout_loading) }
    val loadingText: TextView by lazy { activity.findViewById(R.id.tv_loading_text) }
    private val notificationOverlayView: TextView by lazy { activity.findViewById(R.id.notificationOverlay) }

    private var alertDialog: AlertDialog? = null
    private var loadingLayoutAnimator: Animator? = null



    fun showNotificationText(message: String) {
        toast(message)
    }

    fun hideNotificationText() {

    }

    /**
     * Use [com.razer.neuron.game.RnGameError.createAlertIntent] if possible.
     * If the activity [Activity.isFinishing] or if it is due to [Activity.finish]
     * then even showing an [AlertDialog] will not guarentee the user to see it.
     *
     * Calls [showAlert] and also show the [loadingLayout]
     */
    fun showError(title: String, message: String?) {
        message ?: return
        lifecycleScope.launch {
            loadingLayoutAnimator?.cancel()
            if (loadingLayout.isGone) {
                loadingLayoutAnimator = loadingLayout.fadeOnStart(1f, endVisibility = View.VISIBLE)
                    .apply {
                        duration = 500
                        start()
                    }
            }
            showAlert(title, message)
        }
    }


    fun showLoadingProgress(stage: String?) {
        stage ?: return
        showLoadingMessage("${activity.getString(R.string.conn_starting)} $stage")
    }

    fun showLoadingMessage(message: String) {
        Timber.v("showLoadingMessage: ${message}")
        lifecycleScope.launch {
            loadingLayout.visible()
            loadingLayout.alpha = 1f
            loadingText.text = message
        }
    }

    fun showButton(btnText : String, onClick : View.OnClickListener) {
        lifecycleScope.launch {
            loadingBtn.visible()
            loadingBtn.text = btnText
            loadingBtn.setOnClickListener(onClick)
        }
    }

    fun hideButton() {
        lifecycleScope.launch {
            loadingBtn.gone()
        }
    }

    fun hideLoadingProgress() {
        if (loadingLayout.isGone) return
        lifecycleScope.launch {
            hideButton()
            loadingLayoutAnimator?.cancel()
            loadingLayoutAnimator = loadingLayout.fadeOnStart(0f, endVisibility = View.GONE)
                .apply {
                    duration = 500
                    start()
                }
        }
    }

    fun hideError() {
        lifecycleScope.launch {
            alertDialog.dismissSafely()
        }
    }

    fun onDestroy() {
        alertDialog.dismissSafely()
        loadingLayoutAnimator?.cancel()
    }

    /**
     * Show a alert dialog on the UI
     */
    fun showAlert(title: String, message: String, posBtnText : String = activity.getString(R.string.rn_dismiss), onPosClicked : (() -> Unit)? = null, negBtnText : String? = null, onNegClicked : (() -> Unit)? = null) {
        alertDialog.dismissSafely()
        alertDialog = MaterialAlertDialogBuilder(this.activity,  materialAlertDialogTheme())
            .setCancelable(false)
            .setTitle(title)
            .setMessage(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .apply {
                setPositiveButton(posBtnText) { _, p ->
                    onPosClicked?.invoke() ?: activity.finish()
                }

                if(negBtnText != null) {
                    setNegativeButton(negBtnText) { _, p ->
                        onNegClicked?.invoke() ?: activity.finish()
                    }
                }
            }
            .show()
    }


}
