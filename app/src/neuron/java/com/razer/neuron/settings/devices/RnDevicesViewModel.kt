package com.razer.neuron.settings.devices

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limelight.R
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.ComputerDetails.State
import com.limelight.nvstream.http.isPaired
import com.limelight.nvstream.wol.WakeOnLanSender
import com.razer.neuron.common.debugToast
import com.razer.neuron.common.logAndRecordException
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.di.UnexpectedExceptionHandler
import com.razer.neuron.extensions.vv
import com.razer.neuron.game.addRecoveryCount
import com.razer.neuron.model.AppThemeType
import com.razer.neuron.nexus.NexusContentProvider
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.settings.PairingStage
import com.razer.neuron.settings.StreamingManager
import com.razer.neuron.startgame.RnStartGameModel
import com.razer.neuron.startgame.RnStartGameViewModel
import com.razer.neuron.utils.getStringExt
import com.razer.neuron.utils.now
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
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


@HiltViewModel
class RnDevicesViewModel
@Inject constructor(
    private val application: Application,
    @UnexpectedExceptionHandler val unexpectedExceptionHandler: CoroutineExceptionHandler,
    @IoDispatcher override val ioDispatcher: CoroutineDispatcher,
    override val computerDatabaseManager: ComputerDatabaseManager,
    private val streamingManager: StreamingManager
) : ViewModel(), RnStartGameViewModel {

    companion object {
        const val tag = "ComputersViewModel"
        const val LOADING_TAG_PAIR = "pair"
        /**
         * As per Kevin's comment manually unpaired should still be shown on available section
         */
        val showManuallyUnpaired = true
    }

    private val computerDetailsList = mutableListOf<ComputerDetails>()
    private data class StateCache(val timestamp : Long, val state : State? = null, val activeAddress : AddressTuple? = null)
    private val statesCache = mutableMapOf<String, StateCache>()

    private val _viewSharedFlow = MutableSharedFlow<DeviceState>()
    val viewSharedFlow = _viewSharedFlow.asSharedFlow()

    private fun emitState(state: DeviceState) {
        viewModelScope.launch { _viewSharedFlow.emit(state) }
    }

    fun onViewCreated() {
        Timber.v("onViewCreated: ${computerDetailsList}")
        emitState(DeviceState.ShowContent(createContent(computerDetailsList)))
        observe()
    }

    /**
     * Before [DeviceState.StartComputerPolling] we need to fetch the latest data from
     * [NexusContentProvider] first
     */
    fun onBeforeStartComputerUpdates() {
        viewModelScope.launch {
            withContext(ioDispatcher) { NexusContentProvider.sync(application) }
            /**
             * this is important, because [com.limelight.computers.ComputerManagerService] will report
             * all the computers again (Reading all computer from db) which was just updated by [NexusContentProvider.sync]
             */
            computerDetailsList.clear()
            emitState(DeviceState.ShowContent(createContent(computerDetailsList)))
            /**
             * Tell UI to start polling
             */
            emitState(DeviceState.StartComputerPolling)
        }
    }


    private fun observe() {
        viewModelScope.launch {
            streamingManager.pairingStageFlow.collect {
                when(it) {
                    is PairingStage.ShowPinCode -> {
                        emitState(DeviceState.ShowPin(it.computerDetails, it.pinCode))
                    }
                    is PairingStage.Success -> {
                        onComputerDetailsUpdated(fromNeuron = true, it.computerDetails)
                        emitState(DeviceState.HidePin)
                    }
                    is PairingStage.Error -> {
                        emitState(DeviceState.ShowError(it.e))
                        emitState(DeviceState.HideLoading())
                        emitState(DeviceState.HidePin)
                    }
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            streamingManager.startStreamFlow.collect {
                intent ->
                askBeforeStartStream(intent)
            }
        }
    }


    private fun ComputerDetails.removeStateCache() {
        statesCache.remove(uuid)
    }

    private fun ComputerDetails.getStateCache(maxAge : Long = 5.seconds.inWholeMilliseconds) : StateCache? {
        return statesCache[uuid]?.takeIf { now() - it.timestamp < maxAge }
    }

    private fun ComputerDetails.hasKnownState() = (state != State.UNKNOWN && state != null) || activeAddress != null

    private fun ComputerDetails.maybeSaveToCache() {
        if(hasKnownState()) {
            statesCache[uuid] = StateCache(now(), state, activeAddress)
        }
    }

    private fun ComputerDetails.maybeUpdateFromCache() {
        if(!hasKnownState()) {
            getStateCache()?.let {
                state = it.state
                activeAddress = it.activeAddress
            }
        }
    }

    private fun ComputerDetails.lastUsedTimestamp(): Long {
        return RemotePlaySettingsPref.getComputerMeta(uuid)?.lastUsedTimestamp ?: 0
    }

    /**
     * If [fromNeuron] is true (i.e. the change was done by [RnDevicesViewModel] then we
     * need to update [com.limelight.computers.ComputerManagerService] via emitting [DeviceState.InvalidateComputer]
     *
     * Then we notify the UI change by emitting [DeviceState.ShowContent] with the result from
     * [createContent] (i.e. because [DeviceItem.Header] might need to change if [DeviceItem] changes)
     */
    fun onComputerDetailsUpdated(fromNeuron : Boolean, details: ComputerDetails? = null) {
        Timber.vv("onComputerDetailsUpdated: ${"-".repeat(20)}")
        Timber.vv("onComputerDetailsUpdated: fromNeuron=$fromNeuron $details")
        if(details != null) {
            details.maybeSaveToCache()
            details.maybeUpdateFromCache()
            if(fromNeuron) {
                emitState(DeviceState.InvalidateComputer(details))
            }
            computerDetailsList.forEachIndexed { index, computerDetails ->
                Timber.vv("onComputerDetailsUpdated: [$index] $computerDetails")
            }
            val existingComputer = if(details.machineIdentifier != null)
                computerDetailsList.find { it.machineIdentifier == details.machineIdentifier }
            else
                computerDetailsList.find { it.uuid == details.uuid }

            if(existingComputer != null) {
                existingComputer.update(details)
            } else {
                if (showManuallyUnpaired || !wasManuallyUnpaired(details.uuid)) {
                    computerDetailsList.add(details)
                }
            }
        }
        emitState(DeviceState.ShowContent(createContent(computerDetailsList)))
    }

    fun onComputerDetailsRemoved(fromNeuron : Boolean, details: ComputerDetails) {
        Timber.v("onComputerDetailsRemoved: ${"-".repeat(20)}")
        Timber.v("onComputerDetailsRemoved: fromNeuron=$fromNeuron $details")
        details.removeStateCache()
        if (fromNeuron) {
            emitState(DeviceState.InvalidateComputer(details))
        }
        computerDetailsList.removeIf { it.uuid == details.uuid }
        emitState(DeviceState.ShowContent(createContent(computerDetailsList)))
    }


    fun onActionClicked(actionItem: DeviceItem.ActionItem) = viewModelScope.launch {
        when (actionItem) {
            is DeviceItem.SelectTheme -> {
                emitState(DeviceState.ShowSelectTheme(RemotePlaySettingsPref.appThemeType))
            }
            is DeviceItem.AddManually -> {
                emitState(DeviceState.StartManualPairing)
            }
            else -> {
                debugToast("Not supported yet: ${actionItem.id}")
            }
        }
    }

    private fun onAppThemeConfirmed(appThemeType: AppThemeType) {
        RemotePlaySettingsPref.appThemeType = appThemeType
    }


    fun onSelectTheme(appThemeType: AppThemeType) {
        if(appThemeType == RemotePlaySettingsPref.appThemeType) {
            return
        }
        onAppThemeConfirmed(appThemeType)
        onComputerDetailsUpdated(fromNeuron = true)
        emitState(DeviceState.ApplyTheme)
    }

    fun onSwitchChecked(switchItem: DeviceItem.SwitchItem, isChecked: Boolean) = viewModelScope.launch {
        debugToast("Not supported yet: ${switchItem.id}")
    }


    private fun createContent(
        allComputer: List<ComputerDetails>
    ): List<DeviceItem> {
        fun ComputerDetails.toItem(hasDuplicate : Boolean) = DeviceItem.Computer(this, hasDuplicate)
        val content = mutableListOf<DeviceItem>()
        content += DeviceItem.Header
        /**
         * Unlike Nexus the where "paired" is decided by serverCert. In Neuron, [ComputerDetails.pairState]
         * is initially assign as null, so it cannot be used to to determine "paired" if the computer is
         * offline.
         */
        val paired = allComputer.filter { it.isPaired() }
            .sortedWith(compareByDescending<ComputerDetails> { it.lastUsedTimestamp() }.thenBy { it.name })
        val unpaired = allComputer.filter { !it.isPaired() && it.state == ComputerDetails.State.ONLINE }
            .sortedBy { it.name }
        if (unpaired.isEmpty() && paired.isEmpty()) {
            content += DeviceItem.UnpairedGroupHeader(true)
        } else {
            val groupByName = (paired + unpaired).groupBy { it.name }
            fun ComputerDetails.hasDuplicate() = (groupByName[name]?.size ?: 0) > 1
            if (paired.isNotEmpty()) {
                content += DeviceItem.PairedGroupHeader
                content.addAll(paired.map { it.toItem(it.hasDuplicate()) })
            }
            if (unpaired.isNotEmpty()) {
                content += DeviceItem.UnpairedGroupHeader(false)
                content.addAll(unpaired.map { it.toItem(it.hasDuplicate()) })
            }
        }
        content += DeviceItem.AddManually
        return content
    }

    fun onPair(
        uniqueId: String?,
        details: ComputerDetails
    ) = viewModelScope.launch {
        wrapWithLoadingState("pair") {
            emitState(DeviceState.StopComputerUpdates(true))
            streamingManager.doPair(uniqueId, details)
            /**
             * In addition, we can call [onComputerDetailsUpdated] also, but it is done
             * via collecting from [StreamingManager.pairingStageFlow] and checking for
             * [PairingStage.Success]
             */
            emitState(DeviceState.StartComputerUpdates)
        }
    }

    fun onUnpair(
        uniqueId: String,
        details: ComputerDetails,
    ) = viewModelScope.launch {
        wrapWithLoadingState("unpair") {
            emitState(DeviceState.StopComputerUpdates(true))
            streamingManager.doUnpair(uniqueId, details)
            onComputerDetailsUpdated(fromNeuron = true, details)
            emitState(DeviceState.StartComputerUpdates)
        }
    }

    fun sendWakeOnLan(computerDetails: ComputerDetails) {
        if (computerDetails.macAddress == null) {
            debugToast("Mac address not found for ${computerDetails.name}")
            emitState(DeviceState.ShowMessage(getStringExt(R.string.wol_fail)))
            return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                WakeOnLanSender.sendWolPacket(computerDetails)
                emitState(DeviceState.ShowMessage(getStringExt(R.string.wol_waking_msg)))
            } catch (e: IOException) {
                emitState(DeviceState.ShowMessage(getStringExt(R.string.wol_fail)))
            }
        }
    }

    fun onStartStream(
        uniqueId: String,
        details: ComputerDetails
    ) = viewModelScope.launch {
        wrapWithLoadingState("startStream") {
            val result = streamingManager.startStream(uniqueId, details)
            result.exceptionOrNull()?.let { logAndRecordException(it) }
        }
    }

    private suspend fun <T> wrapWithLoadingState(tag: String, task: suspend () -> T): T {
        return try {
            emitState(DeviceState.ShowLoading(tag))
            task()
        } finally {
            emitState(DeviceState.HideLoading(tag))
        }
    }

    private fun wasManuallyUnpaired(uuid: String): Boolean {
        return RemotePlaySettingsPref.manuallyUnpaired.contains(uuid)
    }


    override val appContext get() = application
    override val sessionViewModelScope get() = viewModelScope

    private val _sessionNavigation = MutableSharedFlow<RnStartGameModel.Navigation>()
    override val sessionNavigation = _sessionNavigation.asSharedFlow()

    private val _sessionState = MutableStateFlow<RnStartGameModel.State>(RnStartGameModel.State.Empty)
    override val sessionState = _sessionState.asStateFlow()


    override fun RnStartGameModel.State.emit() = viewModelScope.launch { _sessionState.emit(this@emit) }
    override fun RnStartGameModel.Navigation.emit() = viewModelScope.launch { _sessionNavigation.emit(this@emit) }

}