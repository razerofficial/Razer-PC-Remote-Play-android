package com.razer.neuron.settings.devices

import android.content.Intent
import com.limelight.nvstream.http.ComputerDetails
import com.razer.neuron.model.AppThemeType

enum class DeviceAction {
    PAIR, UNPAIR, STREAM, WOL
}

sealed class DeviceState {

    /**
     * Tell the UI to stop polling using [com.limelight.computers.ComputerManagerService]
     */
    data class StopComputerUpdates(val wait : Boolean) : DeviceState()

    /**
     * Tell the UI to prepare polling using [com.limelight.computers.ComputerManagerService]
     */
    data object StartComputerUpdates : DeviceState()

    /**
     * Tell the UI to actually start polling using [com.limelight.computers.ComputerManagerService]
     */
    data object StartComputerPolling : DeviceState()

    data class ShowLoading(val tag: String) : DeviceState()
    data class HideLoading(val tag: String = "") : DeviceState()

    data class ShowError(val error: Throwable) : DeviceState()
    data class ShowMessage(val message: String) : DeviceState()

    data object ApplyTheme : DeviceState()

    data class ShowPin(val computerDetails: ComputerDetails, val pinCode: String) : DeviceState()

    data object HidePin : DeviceState()

    data class ShowContent(val items: List<DeviceItem>) : DeviceState()

    data class ShowSelectTheme(val defaultOption: AppThemeType) : DeviceState()

    data object StartManualPairing : DeviceState()

    /**
     * Use [com.razer.neuron.startgame.RnStartGameViewModel.askBeforeStartStream] to trigger
     * [com.razer.neuron.startgame.RnStartGameModel.Navigation.Stream] instead
     */
    @Deprecated("Don't start stream directly")
    data class StartStreaming(val intent: Intent) : DeviceState()

    /**
     * Invalidate a [ComputerDetails] in [com.limelight.computers.ComputerManagerService]
     */
    data class InvalidateComputer(val computerDetails: ComputerDetails) : DeviceState()

}
