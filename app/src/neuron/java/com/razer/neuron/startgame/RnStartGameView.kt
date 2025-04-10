package com.razer.neuron.startgame

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limelight.R
import com.razer.neuron.RnApp
import com.razer.neuron.common.materialAlertDialogTheme
import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.toHtmlSpanned
import com.razer.neuron.isShowVerboseErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber


/**
 * A interface for "View" class like [Activity] or [Fragment]
 * inorder to use [RnStartGameViewModel]
 *
 * Eventually, it should call [startGame]
 */
interface RnStartGameView {

    val sessionViewModel : RnStartGameViewModel

    val sessionLifecycleScope : CoroutineScope

    var alertDialog: AlertDialog?

    val activity : Activity?

    /**
     * must call at the creation of lifecycle
     */
    fun observeSessionViewModel() {
        sessionLifecycleScope.launch {
            sessionViewModel.sessionState.collect {
                when (it) {
                    is RnStartGameModel.State.ShowLoading -> it.handle()
                    is RnStartGameModel.State.HideLoading -> it.handle()
                    is RnStartGameModel.State.Empty -> hideLoading()
                }
            }
        }
        sessionLifecycleScope.launch {
            sessionViewModel.sessionNavigation.collect {
                when (it) {
                    is RnStartGameModel.Navigation.Stream -> it.handle()
                    is RnStartGameModel.Navigation.StartSameGameOrQuit -> it.handle()
                    is RnStartGameModel.Navigation.ConfirmQuitThenStartDifferentGame -> it.handle()
                    is RnStartGameModel.Navigation.Error -> it.handle()
                    is RnStartGameModel.Navigation.Finish -> finishFromStartStream()
                    is RnStartGameModel.Navigation.ReplaceSessionOrQuit -> it.handle()
                }
            }
        }
    }


    private fun RnStartGameModel.Navigation.StartSameGameOrQuit.handle() {
        val activity = activity ?: return
        alertDialog.dismissSafely()
        val title = getString(R.string.rn_start_game)
        val message = getString(R.string.rn_resume_or_quit_msg, startGameName)
        Timber.v("StartOrQuit.handle: $message")
        alertDialog = MaterialAlertDialogBuilder(activity, materialAlertDialogTheme())
            .setCancelable(false)
            .setTitle(title)
            .setMessage(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.applist_menu_resume) { _, p ->
                sessionViewModel.onStartGame(gameIntent)
            }
            .setNegativeButton(R.string.rn_quit_and_dismiss) { _, p ->
                sessionViewModel.onQuitGame(computerDetails = computerDetails)
            }
            .show()
    }



    private fun RnStartGameModel.Navigation.ConfirmQuitThenStartDifferentGame.handle() {
        val activity = activity ?: return
        alertDialog.dismissSafely()
        val title = getString(R.string.rn_start_game)
        val message = getString(
            R.string.rn_confirm_quit_then_start_msg, runningGameName ?: getString(
                R.string.rn_currently_running_game), startGameName)
        Timber.v("AskToQuitRunningGame.handle: $message")
        alertDialog = MaterialAlertDialogBuilder(activity, materialAlertDialogTheme())
            .setCancelable(false)
            .setTitle(title)
            .setMessage(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.rn_quit_and_start) { _, p ->
                sessionViewModel.onQuitThenStart(computerDetails, gameIntent, true)
            }
            .setNegativeButton(R.string.rn_cancel) { _, p ->
                finishFromStartStream()
            }
            .show()
    }

    private fun RnStartGameModel.Navigation.ReplaceSessionOrQuit.handle() {
        val activity = activity ?: return
        alertDialog.dismissSafely()
        val message = getString(R.string.rn_prompt_quit_existing_session_msg, startGameName, runningGameDeviceNickName).toHtmlSpanned()
        alertDialog = MaterialAlertDialogBuilder(activity, materialAlertDialogTheme())
            .setCancelable(false)
            .setTitle(getString(R.string.rn_prompt_quit_existing_session_title))
            .setMessage(message)
            .setPositiveButton(R.string.rn_replace) { _, p ->
                sessionViewModel.onReplaceSession(computerDetails, gameIntent, true)
            }
            .setNegativeButton(R.string.rn_dismiss) { _, p ->
                finishFromStartStream()
            }
            .show()
    }

    fun RnStartGameModel.Navigation.Error.handle() = showErrorAlert(throwable)

    fun RnStartGameModel.State.HideLoading.handle() = hideLoading()

    fun RnStartGameModel.State.ShowLoading.handle() = showLoading()

    fun showErrorAlert(throwable: Throwable) {
        val activity = activity ?: return
        alertDialog.dismissSafely()
        var message = getString(R.string.conn_error_msg)
        if(isShowVerboseErrorMessage()) {
            message += "\n\n"
            message += throwable.message
        }
        alertDialog = MaterialAlertDialogBuilder(activity, materialAlertDialogTheme())
            .setCancelable(false)
            .setTitle(getString(R.string.conn_error_title))
            .setMessage(message)
            .setPositiveButton(R.string.rn_dismiss) { _, p ->
                finishFromStartStream()
            }
            .show()
    }

    private fun getString(@StringRes stringResId : Int, vararg args : Any) = (activity?: RnApp.appContext).getString(stringResId, *args)

    private fun RnStartGameModel.Navigation.Stream.handle() {
        startGame(gameIntent)
        finishFromStartStream()
    }

    fun startGame(gameIntent: Intent) = activity?.startActivity(gameIntent)

    /**
     * Use choose to now start the game
     *
     * Override this to call [Activity.finish] or just leave it as it is.
     */
    fun finishFromStartStream() {
        hideLoading()
    }


    /**
     * Show some loading overlay
     */
    fun showLoading()

    /**
     * hide the loading overlay
     */
    fun hideLoading()

}