package com.razer.neuron.startgame

import android.content.Context
import android.content.Intent
import com.limelight.Game
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.razer.neuron.common.toast
import com.razer.neuron.game.setReplaceSession
import com.razer.neuron.main.findActiveAddress
import com.razer.neuron.model.isDesktop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.LinkedList
import kotlin.jvm.Throws

/**
 * A model to apply consistent logic in order to start [RnGame]
 */
interface RnStartGameViewModel {

    val ioDispatcher : CoroutineDispatcher

    val appContext : Context
    
    val sessionViewModelScope : CoroutineScope

    val sessionNavigation : SharedFlow<RnStartGameModel.Navigation>

    val sessionState : StateFlow<RnStartGameModel.State>

    val computerDatabaseManager : ComputerDatabaseManager

    fun RnStartGameModel.State.emit() : Job

    fun RnStartGameModel.Navigation.emit() : Job

    @Throws(IllegalArgumentException::class)
    suspend fun askBeforeStartStream(gameIntent : Intent) : RnStartGameModel.Navigation = wrapWithLoading {
        val tag = "askBeforeStartStream"
        val computerDetailsUuid = requireNotNull(gameIntent.getStringExtra(Game.EXTRA_PC_UUID))
        val appId = requireNotNull(gameIntent.getIntExtra(Game.EXTRA_APP_ID, 0).takeIf { it > 0 })
        var appName = gameIntent.getStringExtra(Game.EXTRA_APP_NAME)
        var computerDetails = requireNotNull(computerDatabaseManager.getComputerByUUID(computerDetailsUuid)) {
            "Computer not found"
        }
        Timber.v("$tag: computerDetailsUuid $computerDetailsUuid")
        Timber.v("$tag: $appId ($appName)")
        val result = computerDetails.findActiveAddress(appContext, isUpdateThis = true)
        val activeAddress = computerDetails.activeAddress
        val serverCert = computerDetails.serverCert
        var runningGameId = computerDetails.runningGameId
        var runningGameDevice : String? = null
        val httpsPort = computerDetails.httpsPort
        var runningGame : NvApp? = null
        var app : NvApp? = null
        val http = NvHTTP(
            activeAddress,
            httpsPort,
            null,
            serverCert,
            PlatformBinding.getCryptoProvider(appContext))

        if(activeAddress != null && httpsPort != 0) {
            runCatching {
                // call serverInfo to get latest
                computerDetails = http.getComputerDetails(true)
                computerDetails.activeAddress = activeAddress
                computerDetails.serverCert = serverCert
                runningGameId = computerDetails.runningGameId
                runningGameDevice = computerDetails.runningGameDevice
                // call applist to get runningGame (check if it is desktop and getting the name)
                val list = (http.appList ?: LinkedList())
                runningGame = list.firstOrNull { it.appId == runningGameId }
                app = list.firstOrNull { it.appId == appId }
            }.exceptionOrNull()?.let {
                Timber.w(it)
                Timber.w("$tag: error ${it.message}")
            } // can ignore error here since runningGameName is optional
        }

        appName = app?.appName ?: appName ?: "game"
        if(result.isSuccess) {
            Timber.v("$tag: runningGameId=${runningGameId},runningGameName=${runningGame?.appName}, appId=${appId}")
            Timber.v("$tag: gameIntent=${gameIntent}")
            // NEUR-22, NEUR-75, NEUR-104
            val _runningGame = runningGame
            val runningGameIsDesktop = _runningGame?.isDesktop() == true
            val targetGameIsDesktop = app?.isDesktop() == true
            val hasRunningGame = runningGameId != 0

            if(hasRunningGame && !runningGameIsDesktop) {
                if(targetGameIsDesktop && _runningGame != null) {
                    // NEUR-104
                    RnStartGameModel.Navigation.Stream(Intent(gameIntent).apply {
                        putExtra(Game.EXTRA_APP_ID, _runningGame.appId)
                        putExtra(Game.EXTRA_APP_NAME, _runningGame.appName)
                    })
                } else {
                    if (runningGameId == appId) {
                        RnStartGameModel.Navigation.StartSameGameOrQuit(
                            startGameName = appName,
                            computerDetails = computerDetails,
                            gameIntent = gameIntent
                        )
                    } else {
                        RnStartGameModel.Navigation.ConfirmQuitThenStartDifferentGame(
                            runningGameName = runningGame?.appName,
                            startGameName = appName,
                            computerDetails = computerDetails,
                            gameIntent = gameIntent
                        )
                    }
                }
            } else {
                RnStartGameModel.Navigation.Stream(gameIntent)
            }
        } else {
            RnStartGameModel.Navigation.Stream(gameIntent)
        }
    }

    /**
     * For [RnStartGameModel.Navigation.StartSameGameOrQuit]
     */
    fun onStartGame(gameIntent : Intent) = sessionViewModelScope.launch {
        Timber.v("onStartGame: gameIntent=${gameIntent}")
        RnStartGameModel.Navigation.Stream(gameIntent).emit()
    }

    /**
     * For [RnStartGameModel.Navigation.StartSameGameOrQuit]
     */
    fun onQuitGame(computerDetails: ComputerDetails) = sessionViewModelScope.launch {
        wrapWithLoading {
            try {
                computerDetails.quitApp()
            } catch (t : Throwable) {
                toast(appContext.getString(R.string.rn_unable_quit_game_error, t.message))
                Timber.w("onQuitGame: ${t.message}")
                Timber.w(t)
                RnStartGameModel.Navigation.Error(t).emit()
            }
            RnStartGameModel.Navigation.Finish.emit()
        }
    }


    /**
     * [RnStartGameModel.Navigation.ReplaceSessionOrQuit]
     */
    fun onReplaceSession(computerDetails : ComputerDetails, gameIntent : Intent, isIgnoreQuitAppError : Boolean = true) = sessionViewModelScope.launch {
        gameIntent.setReplaceSession(true)
        RnStartGameModel.Navigation.Stream(gameIntent).emit()
    }

    /**
     * For [RnStartGameModel.Navigation.ConfirmQuitThenStartDifferentGame]
     * or [RnStartGameModel.Navigation.ReplaceSessionOrQuit]
     */
    fun onQuitThenStart(computerDetails : ComputerDetails, gameIntent : Intent, isIgnoreQuitAppError : Boolean = true) = sessionViewModelScope.launch {
        wrapWithLoading {
            try {
                /**
                 * Calling Game (i.e [RnGame]) will quit the game automatically
                 * no need to call [ComputerDetails.quitApp]
                 */
                //computerDetails.quitApp();
                RnStartGameModel.Navigation.Stream(gameIntent).emit()
            } catch (t : Throwable) {
                toast(appContext.getString(R.string.rn_unable_quit_game_error, t.message))
                Timber.w("onQuitGameConfirmed: ${t.message}")
                Timber.w(t)
                if(isIgnoreQuitAppError) {
                    RnStartGameModel.Navigation.Stream(gameIntent).emit()
                } else {
                    RnStartGameModel.Navigation.Error(t).emit()
                }
            }
        }
    }

    private suspend fun ComputerDetails.quitApp() {
        val activeAddress = activeAddress
        Timber.v("quitApp: activeAddress=${activeAddress}")
        check(activeAddress != null) { "No active address" }
        withContext(ioDispatcher) {
            val http = NvHTTP(
                activeAddress,
                httpsPort,
                null,
                serverCert,
                PlatformBinding.getCryptoProvider(appContext))
            Timber.v("quitApp: starting quitApp")
            http.quitApp()
        }
    }
    

    private suspend fun <T> wrapWithLoading(task : suspend () -> T) : T{
        return try {
            RnStartGameModel.State.ShowLoading.emit()
            task()
        } finally {
            RnStartGameModel.State.HideLoading.emit()
        }
    }
}