package com.razer.neuron.game

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.limelight.R
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.razer.neuron.common.BaseActivity
import com.razer.neuron.common.ComputerServiceHelper
import com.razer.neuron.common.debugToast
import timber.log.Timber

import com.razer.neuron.common.logAndRecordException
import com.razer.neuron.common.toast

import com.razer.neuron.extensions.applyTransition
import com.razer.neuron.extensions.defaultJson
import com.razer.neuron.extensions.getParcelableExtraExt
import com.razer.neuron.extensions.setDrawIntoSafeArea
import com.razer.neuron.game.helpers.RnGameView
import com.razer.neuron.game.helpers.RnStreamError
import com.razer.neuron.model.DynamicThemeActivity
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.utils.Web
import com.razer.neuron.utils.openInAppBrowser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.jvm.Throws


/**
 * A [android.app.Activity] to restart [RnGame]
 * or just show a message.
 *
 * Using the same splash layout as [RnGame], this activity is intended to be shown after
 * [RnGame] is either forced to close or close by itself, so that we let user know what happened
 * to the original game session.
 */
@AndroidEntryPoint
class RnGameError : BaseActivity(), DynamicThemeActivity {
    override val isThemeFullscreen = true
    override fun getThemeId() = appThemeType.splashThemeId
    override val isUseWhiteSystemBarIcons = false

    private val isCropToSafeArea get() = RemotePlaySettingsPref.isCropDisplaySafeArea
    private val isVirtualDisplayMode get() = RemotePlaySettingsPref.displayMode.isUsesVirtualDisplay


    companion object {
        const val MODE_RESTART_GAME = "MODE_RESTART_GAME"
        const val MODE_SHOW_ALERT = "MODE_SHOW_ALERT"
        const val MODE_HANDLE_STREAM_ERROR = "MODE_HANDLE_STREAM_ERROR"

        private const val EXTRA_MODE = "EXTRA_MODE"
        private const val EXTRA_DELAY_MS = "EXTRA_DELAY_MS"
        private const val EXTRA_STAGE_MSG = "EXTRA_STAGE_MSG"
        private const val EXTRA_TITLE = "EXTRA_TITLE"
        private const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        private const val EXTRA_STREAM_ERROR_JSON = "EXTRA_STREAM_ERROR_JSON"
        private const val EXTRA_GAME_INTENT = "EXTRA_GAME_INTENT"
        const val EXTRA_RECOVERY_COUNT = "EXTRA_RECOVERY_COUNT"
        const val EXTRA_LAST_STREAM_ERROR_TIMESTAMP = "EXTRA_LAST_STREAM_ERROR_TIMESTAMP"
        const val EXTRA_LAST_UNGRACEFUL_TERMINATION_TIMESTAMP = "EXTRA_LAST_UNGRACEFUL_TERMINATION_TIMESTAMP"
        const val MAX_RECOVERY_COUNT = 1
        /**
         * For [RnGame] to read when mode is [MODE_RESTART_GAME]
         */
        const val EXTRA_WAS_RESTARTED = "EXTRA_WAS_RESTARTED"

        private var currentInstance: WeakReference<RnGameError>? = null

        /**
         * Call [android.app.Activity.finish] on existing [RnGameError] so that
         * the delay will be cancel and restart will not be launched.
         */
        fun cancelPendingRestart(): Boolean {
            return currentInstance?.get()
                ?.let {
                    it.delayedRestartJob?.cancel()
                    it.finish()
                    true
                } ?: false
        }

        /**
         * Call [android.app.Activity.finish] on existing [RnGameError] so that
         * the message from [MODE_SHOW_ALERT] won't be shown anymore.
         */
        fun finishShownMessage() {
            currentInstance?.get()?.takeIf { it.mode == MODE_SHOW_ALERT }?.finish()
        }


        /**
         * Create an [Intent] to start [RnGameError]
         *
         * [RnGameError] will show the same splash UI as [RnGame] (i.e. [RnGameView])
         * and immediately also show [AlertDialog] via [RnGameView.showAlertDialog]
         */
        fun createAlertIntent(context: Context, title: String, message: String): Intent {
            return Intent(context, RnGameError::class.java).apply {
                putExtra(EXTRA_MODE, MODE_SHOW_ALERT)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
        }

        /**
         * Create an [Intent] to start [RnGameError]
         *
         * [RnGameError] will show the same splash UI as [RnGame] (i.e. [RnGameView])
         * but after [delayMs] it will [startActivity] of [RnGame] using [originalIntentExtra]
         */
        fun createRestartIntent(
            context: Context,
            delayMs: Long = 3000,
            stageMsg: String?,
            gameIntent: Intent
        ): Intent {
            return Intent(context, RnGameError::class.java).apply {
                putExtra(EXTRA_MODE, MODE_RESTART_GAME)
                putExtra(EXTRA_DELAY_MS, delayMs)
                putExtra(EXTRA_STAGE_MSG, stageMsg)
                putExtra(EXTRA_GAME_INTENT, gameIntent)
            }
        }


        /**
         * Create an [Intent] to start [RnGameError]
         *
         * [RnGameError] will show the same splash UI as [RnGame] (i.e. [RnGameView])
         * but immediately also show [AlertDialog] via [RnGameView.showAlertDialog] or handle the [RnStreamError] in some other way
         */
        fun createHandleStreamErrorIntent(
            context: Context,
            streamError: RnStreamError,
            gameIntent: Intent
        ): Intent {
            return Intent(context, RnGameError::class.java).apply {
                putExtra(EXTRA_MODE, MODE_HANDLE_STREAM_ERROR)
                putExtra(EXTRA_STREAM_ERROR_JSON, defaultJson.toJson(streamError))
                putExtra(EXTRA_GAME_INTENT, gameIntent)
            }
        }
    }

    private val computerServiceHelper by lazy {
        object : ComputerServiceHelper() {
            override val computerServiceActivity = this@RnGameError as? AppCompatActivity
            override fun onComputerDetailsRemoved(fromNeuron: Boolean, details: ComputerDetails) = viewModel.onComputerDetailsRemoved(details)
            override fun onComputerDetailsUpdated(fromNeuron: Boolean, details: ComputerDetails?) = details?.let { viewModel.onComputerDetailsUpdated(it) } ?: Unit
        }
    }

    private val view by lazy { RnGameView(this) }

    private val mode by lazy {
        intent.getStringExtra(EXTRA_MODE) ?: error("Must specify $EXTRA_MODE")
    }

    private var delayedRestartJob: Job? = null

    private val viewModel: RnGameErrorViewModel by viewModels()

    private val gameIntent get() = intent.getParcelableExtraExt<Intent>(EXTRA_GAME_INTENT)


    override fun onCreate(savedInstanceState: Bundle?) {
        applyTransition()
        currentInstance = WeakReference(this)
        super.onCreate(savedInstanceState)
        val isFullscreenLayout = !isVirtualDisplayMode || !isCropToSafeArea
        setDrawIntoSafeArea(isFullscreenLayout)
        setContentView(R.layout.rn_activity_game_error)
        observeNavigation()
        computerServiceHelper.onCreate()
        runCatching {
            when (mode) {
                MODE_RESTART_GAME -> onCreateRestartGameMode()
                MODE_SHOW_ALERT -> onCreateShowAlertMode()
                MODE_HANDLE_STREAM_ERROR -> onCreateHandleStreamErrorMode()
                else -> error("$mode not supported")
            }
        }.exceptionOrNull()?.let {
            toast(it.message)
            finish()
        }
    }

    override fun finish() {
        super.finish()
        if (currentInstance?.get() == this) {
            currentInstance = null
        }
    }

    private fun observeNavigation() {
        lifecycleScope.launch {
            viewModel.navigation.collect { navigation ->
                when(navigation) {
                    is RnGameErrorModel.Navigation.ShowError -> navigation.handle()
                    is RnGameErrorModel.Navigation.FinishAndRestartGame -> navigation.handle()
                    is RnGameErrorModel.Navigation.PromptReplaceSessionOrQuit -> navigation.handle()
                    is RnGameErrorModel.Navigation.Reconnecting -> navigation.handle()
                    is RnGameErrorModel.Navigation.ShowLoading -> navigation.handle()
                }
            }
        }
    }


    private fun RnGameErrorModel.Navigation.ShowError.handle() {
        recoveryError?.let { debugToast(it.message) }
        streamError.displayError(restartGameIntent = gameIntent)
    }

    private fun RnGameErrorModel.Navigation.FinishAndRestartGame.handle() {
        val newGameIntent = gameIntent
        if(newGameIntent != null) {
            Timber.w("handle: RnGameErrorModel.Navigation.Finish $newGameIntent recoveryCount=${newGameIntent.getRecoveryCount()}")
            startActivity(newGameIntent)
        }
        finish()
    }

    private fun RnGameErrorModel.Navigation.PromptReplaceSessionOrQuit.handle() {
        view.showAlert(
            title = getString(R.string.rn_prompt_quit_existing_session_title),
            message = getString(R.string.rn_prompt_quit_existing_session_msg, currentDevice, currentGame),
            posBtnText = getString(R.string.rn_replace),
            onPosClicked = {
               viewModel.onUserReplaceSession(gameIntent)
            },
            negBtnText = getString(R.string.rn_cancel)
        )
    }

    private fun RnGameErrorModel.Navigation.Reconnecting.handle() {
        view.showLoadingMessage(getString(R.string.rn_reconnecting_to_x, computerName))
        view.showButton(getString(R.string.rn_tap_to_cancel)) {
            viewModel.onCancelClicked(streamError)
        }
    }

    private fun RnGameErrorModel.Navigation.ShowLoading.handle() {
        view.showLoadingMessage(text)
    }

    @Throws(IllegalStateException::class)
    private fun onCreateShowAlertMode() {
        view.showLoadingMessage("")
        val intent = intent ?: error("Missing intent")
        val title = intent.getStringExtra(EXTRA_TITLE)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
        lifecycleScope.launch {
            delay(500)
            if (title != null && message != null) {
                view.showAlert(title, message)
            } else {
                error("Missing title/message")
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun onCreateHandleStreamErrorMode() {
        view.showLoadingMessage("")
        val intent = intent ?: error("Missing intent")
        val streamError = requireNotNull(
            intent.getStringExtra(EXTRA_STREAM_ERROR_JSON)?.let { defaultJson.fromJson(it, RnStreamError::class.java) }) { "Missing $EXTRA_STREAM_ERROR_JSON" }
        view.showLoadingMessage(getString(R.string.rn_please_wait))
        viewModel.onStreamErrorDisplayed(streamError, gameIntent)
    }

    private fun RnStreamError.displayError(restartGameIntent : Intent? = null) {
        val title = title ?: getString(R.string.conn_error_title)
        val message = requireNotNull(message) { "Error missing message" }
        view.hideButton()
        val helpWebsiteUrl = this.getHelpWebsite()
        when {
            restartGameIntent != null -> {
                view.showAlert(
                    title = title,
                    message = message,
                    posBtnText = getString(R.string.rn_retry),
                    onPosClicked = {
                        startActivity(restartGameIntent)
                        finish()
                    },
                    negBtnText = getString(R.string.rn_dismiss)
                )
            }
            helpWebsiteUrl != null -> {
                view.showAlert(
                    title = title,
                    message = message,
                    posBtnText = getString(R.string.help),
                    onPosClicked = {
                        openInAppBrowser(Web.Custom(helpWebsiteUrl, title))
                        finish()
                    },
                    negBtnText = getString(R.string.rn_dismiss)
                )
            }
            else -> {
                view.showAlert(
                    title = title,
                    message = message
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        view.onDestroy()
        if (currentInstance?.get() == this) {
            currentInstance = null
        }
        computerServiceHelper.onDestroy()
    }

    @Throws(IllegalStateException::class)
    private fun onCreateRestartGameMode() {
        view.showLoadingMessage(getString(R.string.rn_please_wait))
        val defaultDelayMs = requireNotNull(
            intent?.getLongExtra(EXTRA_DELAY_MS, -1)
                ?.takeIf { it > 0L }) { "Must provide EXTRA_DELAY_MS" }
        val gameIntent = requireNotNull(gameIntent) { "Missing game intent" }

        delayedRestartJob?.cancel()
        delayedRestartJob = lifecycleScope.launch(CoroutineExceptionHandler { _, throwable ->
            if (throwable is CancellationException && isFinishing) {
                finish()
            }
        }) {
            val restartIntentResult =
                runCatching { RnGame.createStartStreamIntent(this@RnGameError, gameIntent) }
            restartIntentResult.exceptionOrNull()?.let {
                logAndRecordException(it)
                finish()
                return@launch
            }
            // make sure we can get a restart intent first.
            val restartIntent = restartIntentResult.getOrNull() ?: run {
                finish()
                return@launch
            }
            restartIntent.setRestarted(true)
            Timber.w("onCreate: delay for ${defaultDelayMs}ms")
            delay(defaultDelayMs)
            if (!isActive) {
                Timber.w("onCreate: job cancelled")
                return@launch
            }
            Timber.w("onCreate: starting intent $restartIntent")
            startActivity(restartIntent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        computerServiceHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        computerServiceHelper.onPause()
    }

}