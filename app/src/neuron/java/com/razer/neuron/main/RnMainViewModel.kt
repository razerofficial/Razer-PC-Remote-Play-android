package com.razer.neuron.main

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limelight.computers.ComputerDatabaseManager
import com.razer.neuron.common.logAndRecordException
import timber.log.Timber


import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.di.UnexpectedExceptionHandler
import com.razer.neuron.extensions.hasNexusContentProviderPermission
import com.razer.neuron.game.helpers.RnGameIntentHelper
import com.razer.neuron.nexus.NexusContentProvider
import com.razer.neuron.nexus.NexusPackageStatus
import com.razer.neuron.nexus.getNexusPackageStatus
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.startgame.RnStartGameModel
import com.razer.neuron.startgame.RnStartGameViewModel
import com.razer.neuron.settings.StreamingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
class RnMainViewModel
@Inject constructor(
    private val application: Application,
    @UnexpectedExceptionHandler val unexpectedExceptionHandler: CoroutineExceptionHandler,
    @IoDispatcher override val ioDispatcher : CoroutineDispatcher,
    override val computerDatabaseManager: ComputerDatabaseManager,
    private val streamingManager: StreamingManager
) : ViewModel(), RnGameIntentHelper, RnStartGameViewModel {
    companion object {
        val TAG = "RnMainViewModel"
    }

    override val appContext by lazy { application }
    private val _navigation = MutableSharedFlow<RnMainModel.Navigation>()
    val navigation = _navigation.asSharedFlow()

    private val _state = MutableStateFlow<RnMainModel.State>(RnMainModel.State.Empty)
    val state = _state.asStateFlow()

    private fun RnMainModel.State.emit() = viewModelScope.launch { _state.emit(this@emit) }
    private fun RnMainModel.Navigation.emit() =
        viewModelScope.launch { _navigation.emit(this@emit) }

    override val sessionViewModelScope get() = viewModelScope

    private val _sessionNavigation = MutableSharedFlow<RnStartGameModel.Navigation>()
    override val sessionNavigation = _sessionNavigation.asSharedFlow()

    private val _sessionState = MutableStateFlow<RnStartGameModel.State>(RnStartGameModel.State.Empty)
    override val sessionState = _sessionState.asStateFlow()


    override fun RnStartGameModel.State.emit() = viewModelScope.launch { _sessionState.emit(this@emit) }
    override fun RnStartGameModel.Navigation.emit() = viewModelScope.launch { _sessionNavigation.emit(this@emit) }

    private var launchIntent: Intent? = null

    private var wasSyncCompleted = false

    private val syncMutex = Mutex()

    fun onCreate(intent: Intent) {
        Timber.v("onCreate")
        launchIntent = intent
        wasSyncCompleted = false
        viewModelScope.launch { emitNextNavigation() }
    }

    fun onNewIntent(intent: Intent) {
        launchIntent = intent
        wasSyncCompleted = false
        viewModelScope.launch { emitNextNavigation() }
    }

    fun onUpdateNexusRejected() {
        RemotePlaySettingsPref.hasUserRejectedNexusUpdate = true
        viewModelScope.launch { emitNextNavigation() }
    }

    private suspend fun emitNextNavigation() {
        val intent = launchIntent
        val nexusPackageStatus = appContext.getNexusPackageStatus()
        val isOobeCompleted =  RemotePlaySettingsPref.isOobeCompleted
        val isTosAccepted = RemotePlaySettingsPref.isTosAccepted
        val hasStartStreamIntent = intent != null && hasStartStreamIntent(intent)

        suspend fun startStream() : RnStartGameModel.Navigation {
            intent ?: return RnStartGameModel.Navigation.Error(Exception("Missing launch intent"))
            check(hasStartStreamIntent(intent))
            return wrapWithLoading {
                // last opportunity to sync before starting game
                if(!wasSyncCompleted && appContext.hasNexusContentProviderPermission()) {
                    appContext.sync()
                }
                withContext(ioDispatcher) {
                    val gameIntent = createStartStreamIntent(appContext, intent)
                    try {
                        askBeforeStartStream(gameIntent)
                    } catch (t : Throwable) {
                        logAndRecordException(t)
                        RnStartGameModel.Navigation.Error(t)
                    }
                }
            }
        }

        when {
            !isTosAccepted -> {
                RnMainModel.Navigation.Oobe(intent).emit()
            }
            !isOobeCompleted -> {
                if(hasStartStreamIntent) {
                    startStream().emit()
                } else {
                    RnMainModel.Navigation.Oobe(intent).emit()
                }
            }
            !wasSyncCompleted -> {
                if(appContext.hasNexusContentProviderPermission()) {
                    wrapWithLoading { appContext.sync() }
                } else {
                    wasSyncCompleted = true
                }
                emitNextNavigation() // repeat again
            }
            hasStartStreamIntent -> startStream().emit()
            else -> RnMainModel.Navigation.Landing.emit()
        }
    }


    private suspend fun <T> wrapWithLoading(task : suspend () -> T) : T{
        return try {
            RnMainModel.State.ShowLoading.emit()
            task()
        } finally {
            RnMainModel.State.HideLoading.emit()
        }
    }

    private suspend fun Context.sync() {
        try {
            syncMutex.withLock {
                check(!wasSyncCompleted)
                NexusContentProvider.sync(this@sync)
            }
        } catch (t : Exception) {
            Timber.w(t.message)
        } finally {
            wasSyncCompleted = true
        }
    }

}

class RnMainModel {
    sealed class Navigation(val id: String) {
        object Landing : Navigation("landing")

        object Settings : Navigation("settings")

        class Oobe(val launchIntent : Intent?) : Navigation("oobe")

        @Deprecated("Neuron can function by itself")
        class UpdateNexus(val nexusPackageStatus: NexusPackageStatus) : Navigation("update_nexus")

        class Error(val throwable: Throwable) : Navigation("error")

        object Finish : Navigation("finish")
    }

    sealed class State(val id: String) {
        object Empty : State("empty")

        object ShowLoading : State("show_loading")

        object HideLoading : State("hide_loading")
    }
}
