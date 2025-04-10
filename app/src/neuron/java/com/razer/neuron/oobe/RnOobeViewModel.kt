package com.razer.neuron.oobe

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.razer.neuron.common.debugToast
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.di.UnexpectedExceptionHandler
import com.razer.neuron.extensions.hasNexusContentProviderPermission
import com.razer.neuron.game.helpers.RnGameIntentHelper
import com.razer.neuron.model.AppAction
import com.razer.neuron.model.ButtonHint
import com.razer.neuron.model.ControllerInput
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.nexus.NexusContentProvider
import com.razer.neuron.nexus.NexusPackageStatus
import com.razer.neuron.nexus.getNexusPackageStatus
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.settings.remoteplay.RemotePlaySettingsManager
import com.razer.neuron.utils.PermissionChecker
import com.razer.neuron.utils.calculateVirtualDisplayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class RnOobeViewModel
@Inject constructor(
    private val application: Application,
    @UnexpectedExceptionHandler val unexpectedExceptionHandler: CoroutineExceptionHandler,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
    private val remotePlaySettingsManager: RemotePlaySettingsManager,
) : ViewModel(), RnGameIntentHelper {
    private val appContext by lazy { application }
    private val _navigation = MutableSharedFlow<RnOobeModel.Navigation>()
    val navigation = _navigation.asSharedFlow()

    private val _state = MutableStateFlow<RnOobeModel.State>(RnOobeModel.State.Empty)
    val state = _state.asStateFlow()

    private val _loading = MutableStateFlow<Boolean>(false)
    val loading = _loading.asStateFlow()

    private fun RnOobeModel.State.emit() = viewModelScope.launch { _state.emit(this@emit) }
    private fun RnOobeModel.Navigation.emit() =
        viewModelScope.launch { _navigation.emit(this@emit) }

    private var launchIntent: Intent? = null

    private var wasSyncCompleted = false

    private var allPermissions =
        listOf(OobePermission.NexusContentProvider)//, OobePermission.PermissionB, OobePermission.PermissionA)
    private var activePermissions = mutableListOf<OobePermission>()

    private var permissionIndex: Int? = null

    fun onCreate(intent: Intent) {
        Timber.v("onCreate")
        launchIntent = intent
        wasSyncCompleted = false
        activePermissions = allPermissions.filter {
            when (it) {
                OobePermission.NexusContentProvider -> {
                    // we will ask for the permission so long as nexus was installed (if it is too old
                    // we will jusk as for user to update nexus
                    appContext.getNexusPackageStatus() != NexusPackageStatus.NotInstalled && !it.wasGranted(
                        appContext
                    )
                }

                else -> !it.wasGranted(appContext)
            }
        }.toMutableList()
        nextStateOrFinish()
    }

    fun onNewIntent(intent: Intent) {
        launchIntent = intent
        wasSyncCompleted = false
        nextStateOrFinish()
    }

    fun onControllerInput(controllerInput: ControllerInput, isActionUp: Boolean) {
        if (!isActionUp) return
        val currentState = _state.value
        val buttonHint = currentState.buttonHints.firstOrNull { it.controllerInput == controllerInput }
        nextStateOrFinish(buttonHint?.appAction)
    }

    fun onButtonHintClicked(buttonHint: ButtonHint) {
        nextStateOrFinish(buttonHint.appAction)
    }

    fun onTosClicked() = RnOobeModel.Navigation.Tos.emit()

    fun onPpClicked() = RnOobeModel.Navigation.Pp.emit()

    fun onDownloadNexusClicked() =
        RnOobeModel.Navigation.DownloadNexus(true, appContext.getNexusPackageStatus()).emit()

    fun onPermissionRequestResult(result: PermissionChecker.Result) {
        nextStateOrFinish()
    }

    fun onResume() {
        viewModelScope.launch {
            withContext(ioDispatcher) { NexusContentProvider.sync(appContext) }
            nextStateOrFinish(null)
        }
    }

    fun onDisplayModeSelected(displayMode: DisplayModeOption) {
        remotePlaySettingsManager.setDisplayMode(displayMode)
        viewModelScope.launch {
            withContext(ioDispatcher) {
                Timber.v("onDisplayModeSelected: get displayMode=${RemotePlaySettingsPref.displayMode}")
                /**
                 * [allIntroStates] will update the [RnOobeModel.State.DisplayModes.selected] value
                 * and emit
                 *
                 * If for some reason, the state cannot be found, we will just call [nextStateOrFinish]
                 */
                allIntroStates().firstOrNull { it is RnOobeModel.State.DisplayModes }?.emit()
                    ?: nextStateOrFinish(AppAction.Done)
            }
        }
    }


    /**
     * Calls [nextStateImpl]
     */
    private fun nextStateOrFinish(appAction: AppAction? = null) {
        viewModelScope.launch {
            nextStateImpl(appAction)?.emit() ?: run {
                mainOrFinish().emit()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) = viewModelScope.launch { _loading.emit(isLoading) }

    private fun mainOrFinish(): RnOobeModel.Navigation {
        val intent = launchIntent
        return when {
            RemotePlaySettingsPref.isOobeCompleted -> {
                RnOobeModel.Navigation.Main(intent)
            }

            else -> RnOobeModel.Navigation.Finish
        }
    }

    private fun RnOobeModel.State.setAsCompleted() {
        RemotePlaySettingsPref.completedOobeStates += id
    }

    /**
     *
     * @param appAction user triggered action
     * @return null means it is time to finish
     */
    private suspend fun nextStateImpl(appAction: AppAction?): RnOobeModel.State? {
        val currentState = _state.value
        val nexusPackageStatus = appContext.getNexusPackageStatus()
        return when {
            currentState is RnOobeModel.State.Empty -> {
                if (!RemotePlaySettingsPref.isTosAccepted) RnOobeModel.State.Tos else (nextPermissionSummary()
                    ?: nextIntroStateOrSkipToMain())
            }

            currentState is RnOobeModel.State.Tos -> {
                if (appAction == AppAction.Accept) {
                    RemotePlaySettingsPref.isTosAccepted = true
                    nextPermissionSummary() ?: nextIntroStateOrSkipToMain()
                } else {
                    currentState
                }
            }

            currentState is RnOobeModel.State.PermissionSummary -> {
                val focusPermission = currentState.focusPermission
                val focusPermissionGranted = focusPermission.wasGranted(appContext)
                var isNext = false
                var isBack = false
                if (appAction == AppAction.Allow) {
                    if (!focusPermissionGranted) {
                        val nexusPackageStatus = appContext.getNexusPackageStatus()
                        val navigation =
                            if (focusPermission == OobePermission.NexusContentProvider && nexusPackageStatus != NexusPackageStatus.ValidVersion) {
                                RnOobeModel.Navigation.DownloadNexus(false, nexusPackageStatus)
                            } else {
                                RnOobeModel.Navigation.RequestPermission(focusPermission)
                            }
                        navigation.emit()
                    }
                } else if (appAction == AppAction.Skip || appAction == AppAction.Continue) {
                    isNext = true
                } else if (appAction == AppAction.Back) {
                    isBack = true
                }


                if (isNext || currentState.focusPermission.wasGranted(appContext)) {
                    nextPermissionSummary() ?: nextIntroStateOrSkipToMain()
                } else if (isBack) {
                    previousPermissionSummary() ?: currentState
                } else {
                    currentState
                }
            }

            currentState is RnOobeModel.State.Intro -> {
                var isContinue = false
                if (appAction == AppAction.Skip && nexusPackageStatus != NexusPackageStatus.ValidVersion) {
                    RemotePlaySettingsPref.hasUserRejectedNexusUpdate = true
                }
                if (appAction == AppAction.Done || appAction == AppAction.Skip || appAction == AppAction.Continue) {
                    isContinue = true
                    currentState.setAsCompleted()
                } else if (appAction == AppAction.Download) {
                    isContinue = false
                    RnOobeModel.Navigation.DownloadNexus(true, appContext.getNexusPackageStatus())
                        .emit()
                }

                if (isContinue) {
                    nextIntroState()
                } else {
                    allIntroStates().firstOrNull { it::class == currentState::class } ?: currentState
                }
            }

            !RemotePlaySettingsPref.isOobeCompleted -> {
                debugToast("Not possible. Oobe not completed but all state were completed")
                currentState
            }

            else -> null
        }

    }


    private suspend fun allIntroStates(): List<RnOobeModel.State.Intro> {
        val virtualDisplayMode = calculateVirtualDisplayMode(appContext, RemotePlaySettingsPref.isLimitRefreshRate).getOrNull()
        val displayModesInfo = mapOf(
            DisplayModeOption.DuplicateDisplay to "",
            DisplayModeOption.SeparateDisplay to virtualDisplayMode?.format(),
            DisplayModeOption.PhoneOnlyDisplay to virtualDisplayMode?.format()
        )
        val list = mutableListOf<RnOobeModel.State.Intro>()
        val hasValidNexus = appContext.getNexusPackageStatus() == NexusPackageStatus.ValidVersion

        list += RnOobeModel.State.NexusDownload(
            if (hasValidNexus)
                setOf(ButtonHint.Continue)
            else setOf(
                ButtonHint.Skip,
                ButtonHint.Download
            )
        )
        Timber.v("allIntroStates: RemotePlaySettingsPref.displayMode=${RemotePlaySettingsPref.displayMode}")
        list += RnOobeModel.State.DisplayModes(
            RemotePlaySettingsPref.displayMode,
            displayModesInfo,
            setOf(ButtonHint.Done)
        )
        return list
    }

    /**
     * See NEUR-70
     */
    private suspend fun nextIntroStateOrSkipToMain(): RnOobeModel.State? {
        val launchIntent = launchIntent
        return if(launchIntent != null && hasStartStreamIntent(launchIntent)) {
            RnOobeModel.Navigation.Main(launchIntent).emit()
            // no state, we are launching main again since we don't want user to do the rest of the tutorial
            RnOobeModel.State.Empty
        } else {
            nextIntroState()
        }
    }


    private suspend fun nextIntroState(): RnOobeModel.State? {
        val completedOobeStates = RemotePlaySettingsPref.completedOobeStates
        val nextIntroState = allIntroStates().firstOrNull { !completedOobeStates.contains(it.id) }
        if (nextIntroState == null) {
            RemotePlaySettingsPref.isOobeCompleted = true
        }
        return nextIntroState
    }

    private fun previousPermissionSummary() =
        previousPermission()?.let { createPermissionSummary(it) }

    private fun nextPermissionSummary() = nextPermission()?.let { createPermissionSummary(it) }

    private fun createPermissionSummary(focusPermission: OobePermission): RnOobeModel.State.PermissionSummary {
        val index = activePermissions.indexOf(focusPermission)
        val buttonHints = mutableSetOf<ButtonHint>()
        if (index > 0) {
            buttonHints += ButtonHint.Back
        }
        if (focusPermission.wasGranted(appContext)) {
            buttonHints += ButtonHint.Continue
        } else {
            buttonHints += ButtonHint.Skip
        }
        if (!focusPermission.wasGranted(appContext)) {
            buttonHints += ButtonHint.Allow
        }

        return RnOobeModel.State.PermissionSummary(
            focusPermission,
            activePermissions,
            buttonHints
        )
    }

    private fun nextPermission(): OobePermission? {
        val index = permissionIndex?.let { it + 1 } ?: 0
        val permission = activePermissions.getOrNull(index)
        if (permission != null) {
            permissionIndex = index
        }
        return permission
    }

    private fun previousPermission(): OobePermission? {
        val index = permissionIndex?.let { it - 1 } ?: 0
        val permission = activePermissions.getOrNull(index)
        if (permission != null) {
            permissionIndex = index
        }
        return permission
    }


    private suspend fun wrapWithLoading(task: suspend () -> Unit) {
        try {
            setLoading(true)
            task()
        } finally {
            setLoading(false)
        }
    }


    companion object {
        val TAG = "RnMainViewModel"
    }
}

class RnOobeModel {
    sealed class Navigation(val id: String) {

        class Main(val launchIntent: Intent?) : Navigation("main")

        class RequestPermission(val oobePermission: OobePermission) :
            Navigation("permission-${oobePermission.name}")

        /**
         * Should not be possible
         */
        object Finish : Navigation("main")

        class DownloadNexus(
            val isUserTriggered: Boolean,
            val nexusPackageStatus: NexusPackageStatus
        ) : Navigation("download_nexus")

        object Tos : Navigation("tos")
        object Pp : Navigation("pp")

    }

    sealed class State(val id: String, val buttonHints: Set<ButtonHint> = emptySet()) {
        data object Tos : State("tos", setOf(ButtonHint.Accept))


        class PermissionSummary(
            val focusPermission: OobePermission,
            val displayPermissions: List<OobePermission>,
            buttonHints: Set<ButtonHint>
        ) : State("permission", buttonHints)


        open class Intro(id: String, buttonHints: Set<ButtonHint>) : State(id, buttonHints)

        class NexusDownload(buttonHints: Set<ButtonHint>) : Intro("nexus_download", buttonHints)

        class DisplayModes(
            val selected: DisplayModeOption,
            val additionalInfo: Map<DisplayModeOption, String?> = emptyMap(),
            buttonHints: Set<ButtonHint>
        ) : Intro("display-modes", buttonHints)

        data object Empty : State("empty")

    }
}


enum class OobePermission {
    NexusContentProvider,

    @Deprecated("Not used. Placeholder")
    PermissionA,

    @Deprecated("Not used. Placeholder")
    PermissionB
}

val fakePermissions = mutableMapOf<OobePermission, Boolean>()
fun OobePermission.isFakePermission() = this == OobePermission.PermissionB
        || this == OobePermission.PermissionA

fun OobePermission.wasGranted(context: Context): Boolean {
    return when {
        isFakePermission() -> fakePermissions[this] ?: false
        this == OobePermission.NexusContentProvider -> context.hasNexusContentProviderPermission()
        else -> false
    }
}



