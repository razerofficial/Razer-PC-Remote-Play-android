package com.razer.neuron.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.di.UnexpectedExceptionHandler
import com.razer.neuron.model.AppThemeType
import com.razer.neuron.pref.RemotePlaySettingsPref
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class RnAppearanceViewModel
@Inject constructor(
    @UnexpectedExceptionHandler val unexpectedExceptionHandler: CoroutineExceptionHandler,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    companion object {
        const val tag = "RnAppearanceViewModel"
    }

    private val _viewSharedFlow = MutableSharedFlow<AppearanceState>()
    val viewSharedFlow = _viewSharedFlow.asSharedFlow()

    private fun emitState(state: AppearanceState) {
        viewModelScope.launch { _viewSharedFlow.emit(state) }
    }

    fun onThemeClicked() {
        emitState(AppearanceState.ShowSelectTheme(RemotePlaySettingsPref.appThemeType))
    }

    private fun onAppThemeConfirmed(appThemeType: AppThemeType) {
        RemotePlaySettingsPref.appThemeType = appThemeType
    }


    fun onSelectTheme(appThemeType: AppThemeType) {
        if(appThemeType == RemotePlaySettingsPref.appThemeType) {
            return
        }
        onAppThemeConfirmed(appThemeType)
        emitState(AppearanceState.ApplyTheme)
    }
}