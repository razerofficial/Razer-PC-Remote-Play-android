package com.razer.neuron.settings.manualpairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import com.razer.neuron.common.debugToast
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.exceptions.RnAddHostException
import com.razer.neuron.extensions.getStringExt
import com.razer.neuron.settings.PairingStage
import com.razer.neuron.settings.StreamingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject


@HiltViewModel
class RnManualPairingViewModel
@Inject constructor(
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
    private val streamingManager: StreamingManager
) : ViewModel() {

    companion object {
        const val tag = "ManualPairingViewModel"
    }

    private val _viewSharedFlow = MutableSharedFlow<ManualPairingState>()
    val viewSharedFlow = _viewSharedFlow.asSharedFlow()

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        viewModelScope.launch {
            emitState(ManualPairingState.Error(e))
            emitState(ManualPairingState.HideLoading("pairing failure"))
            emitState(ManualPairingState.HidePin)
        }
    }

    private fun emitState(state: ManualPairingState) {
        viewModelScope.launch { _viewSharedFlow.emit(state) }
    }

    init {
        viewModelScope.launch {
            streamingManager.pairingStageFlow.collect {
                when(it) {
                    is PairingStage.ShowPinCode -> {
                        emitState(ManualPairingState.ShowPin(it.computerDetails.name, it.pinCode))
                    }
                    is PairingStage.Success, is PairingStage.AlreadyPaired -> {
                        emitState(ManualPairingState.OnPaired)
                    }
                    is PairingStage.Error -> {
                        emitState(ManualPairingState.Error(it.e))
                        emitState(ManualPairingState.HideLoading("pairing failure"))
                        emitState(ManualPairingState.HidePin)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onIPAddressEntered(
        uniqueId: String?,
        userInput: String,
        onComputerAdd: suspend (details: ComputerDetails) -> Boolean
    ) {
        val tag = "onIPAddressEntered"
        viewModelScope.launch(ioDispatcher + errorHandler) {
            val ip = userInput.split(":").getOrNull(0)
            val port = userInput.split(":").getOrNull(1)?.toInt() ?: NvHTTP.DEFAULT_HTTP_PORT
            Timber.v("$tag ip=$ip, port=$port")
            if(ip.isNullOrEmpty()) {
                emitState(ManualPairingState.Error(RnAddHostException(getStringExt(R.string.rn_warning_could_not_connect_to_host))))
                return@launch
            }
            emitState(ManualPairingState.ShowLoading(tag))
            val manualIp = AddressTuple(ip, port)
            val details = ComputerDetails().apply { manualAddress = manualIp }
            val success = onComputerAdd.invoke(details)
            if (success) {
                pairComputer(uniqueId, details)
            } else {
                emitState(ManualPairingState.Error(RnAddHostException(getStringExt(R.string.rn_warning_could_not_connect_to_host))))
                emitState(ManualPairingState.HideLoading(tag))
            }
        }
    }

    private suspend fun pairComputer(uniqueId: String?, computerDetails: ComputerDetails) {
        streamingManager.doPair(uniqueId, computerDetails)
    }

    private fun isIpClassAtoCSameAsDevice(address: AddressTuple): Boolean {
        val deviceAddress = getDeviceIPAddress()
        val manualAddress = address.address
        val deviceAddressList = deviceAddress?.split(".")
        val manualAddressList = manualAddress.split(".")
        if (deviceAddressList?.size != 4 || manualAddressList.size != 4) {
            return false
        }
        for (i in 0..2) {
            if (deviceAddressList[i] != manualAddressList[i]) {
                return false
            }
        }
        return true
    }

    private fun getDeviceIPAddress(): String? {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val enumIpAddress = networkInterface.inetAddresses
                while (enumIpAddress.hasMoreElements()) {
                    val inetAddress = enumIpAddress.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (e: Exception) {
            debugToast("Get device address error: ${e.message ?: "unknown network error"}")
        }
        return null
    }
}