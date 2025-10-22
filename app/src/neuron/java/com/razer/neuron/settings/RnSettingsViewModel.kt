package com.razer.neuron.settings

import SettingsGroup
import SettingsItem
import SettingsState
import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.limelight.R
import com.limelight.computers.ComputerDatabaseManager
import com.razer.neuron.common.debugToast
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.di.UnexpectedExceptionHandler
import com.razer.neuron.model.AppAction
import com.razer.neuron.model.AppThemeType
import com.razer.neuron.model.ControllerInput
import com.razer.neuron.pref.RemotePlaySettingsPref
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import javax.inject.Inject


@HiltViewModel
class RnSettingsViewModel
@Inject constructor(
    private val application: Application,
    @UnexpectedExceptionHandler val unexpectedExceptionHandler: CoroutineExceptionHandler,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
    private val computerDatabaseManager: ComputerDatabaseManager,
) : ViewModel() {

    private val defaultItem = SettingsItem.COMPUTERS

    private val _settingsGroupLiveData = MutableLiveData<List<SettingsGroup>>()
    val settingsGroupLiveData: LiveData<List<SettingsGroup>> by lazy {
        _settingsGroupLiveData
    }

    private val _currentSettingsItemLiveData = MutableLiveData(SelectedSettingsItem(defaultItem))
    val currentSettingsItemLiveData: LiveData<SelectedSettingsItem> by lazy {
        _currentSettingsItemLiveData
    }


    private val _settingsState = MutableLiveData<SettingsState>()
    val settingsState: LiveData<SettingsState> by lazy {
        _settingsState
    }

    init {
        setupSettingsGroup()
    }

    /**
     * Add more to field values [SettingsItem] if needed
     */
    fun navigationTo(itemId: Int) {
        val settingsItem : SettingsItem = when(itemId) {
            R.id.menu_item_computers -> SettingsItem.COMPUTERS
            R.id.menu_item_appearance -> SettingsItem.APPEARANCE
            R.id.menu_item_streaming_options -> SettingsItem.STREAMING_OPTIONS
            R.id.menu_item_about -> SettingsItem.ABOUT
            R.id.menu_item_dev_options -> SettingsItem.DEV_OPTIONS
            else -> {
                debugToast("Unknown itemId: $itemId")
                null
            }
        } ?: return
        _currentSettingsItemLiveData.value = SelectedSettingsItem(settingsItem, true)
    }

    private fun setupSettingsGroup() {
        val settingsGroup = mutableListOf<SettingsGroup>()
        settingsGroup.add(
            SettingsGroup.RemotePlayGroup(
                mutableListOf(
                    SettingsItem.STREAMING_OPTIONS,
                    SettingsItem.COMPUTERS,
                )
            )
        )

        if (AppThemeType.isDynamicColorAvailable2()) {
            settingsGroup.add(
                SettingsGroup.AppSettingsGroup(
                    mutableListOf(
                        SettingsItem.APPEARANCE,
                    )
                )
            )
        }
        

        settingsGroup.add(
            SettingsGroup.HelpGroup(
                mutableListOf(
                    SettingsItem.ABOUT,
                    if (RemotePlaySettingsPref.isDevModeEnabled) SettingsItem.DEV_OPTIONS else null
                ).filterNotNull()
            )
        )
        _settingsGroupLiveData.value = settingsGroup
    }

    fun toggleDevMode() {
        val newValue = !RemotePlaySettingsPref.isDevModeEnabled
        RemotePlaySettingsPref.isDevModeEnabled = newValue
        RemotePlaySettingsPref.isPerfOverlayEnabled = newValue
        setupSettingsGroup()
    }

    fun onControllerInput(controllerInput: ControllerInput, isActionUp : Boolean) : Boolean{
        if(!isActionUp) return false
        return when (controllerInput) {
            ControllerInput.START -> {
                _settingsState.value = SettingsState.Finish
                true
            }
            else -> false
        }
    }
}

data class SelectedSettingsItem(val item : SettingsItem, val autoScroll : Boolean = false)