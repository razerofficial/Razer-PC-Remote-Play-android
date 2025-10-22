package com.razer.neuron.game.helpers

import android.animation.Animator
import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.descendants
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limelight.R
import com.razer.neuron.game.RnGame
import timber.log.Timber
import com.razer.neuron.common.materialAlertDialogTheme
import com.razer.neuron.common.setCancelableExt
import com.razer.neuron.common.toast

import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.fadeOnStart
import com.razer.neuron.extensions.getContentRootView
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.visible
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



    fun showNotificationText(message: String) {
        toast(message)
    }

    fun hideNotificationText() {

    }

    /**
     * When we fix the issue NEUR-17, it is possible (as a side effect) that the [loadingLayout] will no longer
     * matches the current screen size for a brief moment, so we need to fix this by setting the
     * [Activity.getContentRootView] to the [loadingLayout] color.
     */
    private fun updateActivityBackgroundColor() {
        with(activity) {
            getContentRootView()?.setBackgroundColor(getColor(if(loadingLayout.isVisible) R.color.rn_razer_green else R.color.rn_black))
        }
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
            if (loadingLayout.isGone) {
                loadingLayout.visible()
                updateActivityBackgroundColor()
            }
            showAlert(title, message, isCancelable = true)
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
            updateActivityBackgroundColor()
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
            loadingLayout.gone()
            updateActivityBackgroundColor()
        }
    }

    fun hideError() {
        lifecycleScope.launch {
            alertDialog.dismissSafely()
        }
    }

    fun onDestroy() {
        alertDialog.dismissSafely()
    }

    /**
     * Show a alert dialog on the UI
     */
    fun showAlert(title: String, message: String, isCancelable: Boolean, posBtnText : String = activity.getString(R.string.rn_dismiss), onPosClicked : (() -> Unit)? = null, negBtnText : String? = null, onNegClicked : (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
        alertDialog.dismissSafely()
        alertDialog = MaterialAlertDialogBuilder(this.activity,  materialAlertDialogTheme())
            .setCancelableExt(isCancelable)
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

                if(isCancelable) {
                    setOnCancelListener { _ ->
                        onCancel?.invoke() ?: activity.finish()
                    }
                }
            }
            .show()
    }


}
