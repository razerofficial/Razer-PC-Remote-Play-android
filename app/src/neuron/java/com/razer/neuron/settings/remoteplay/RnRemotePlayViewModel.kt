package com.razer.neuron.settings.remoteplay

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import com.razer.neuron.RnApp.Companion.appContext
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.di.UnexpectedExceptionHandler
import com.razer.neuron.extensions.FormatOptionMeta
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.model.FramePacingOption
import com.razer.neuron.model.TouchScreenOption
import com.razer.neuron.nexus.NexusContentProvider
import com.razer.neuron.pref.RemotePlaySettingsPref
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@HiltViewModel
class RnRemotePlayViewModel
@Inject constructor(
    @UnexpectedExceptionHandler val unexpectedExceptionHandler: CoroutineExceptionHandler,
    @IoDispatcher val ioDispatcher : CoroutineDispatcher,
    private val remotePlaySettingsManager: RemotePlaySettingsManager,
) : ViewModel() {

    companion object {
        const val tag = "RnRemotePlayViewModel"
    }

    private val _displayModeLiveData = MutableLiveData<DisplayModeOption>()
    val displayModeLiveData: LiveData<DisplayModeOption> by lazy {
        _displayModeLiveData
    }

    private val _cropDisplaySafeAreaLiveData = MutableLiveData<Boolean>()
    val cropDisplaySafeAreaLiveData: LiveData<Boolean> by lazy {
        _cropDisplaySafeAreaLiveData
    }

    private val _limitRefreshRateLiveData = MutableLiveData<Boolean>()
    val limitRefreshRateLiveData: LiveData<Boolean> by lazy {
        _limitRefreshRateLiveData
    }

    private val _bitrateSettingsLiveData = MutableLiveData<BitrateRateSettings>()
    val bitrateSettingsLiveData: LiveData<BitrateRateSettings> by lazy {
        _bitrateSettingsLiveData
    }

    private val _framePacingLiveData = MutableLiveData<FramePacingOption>()
    val framePacingLiveData: LiveData<FramePacingOption> by lazy {
        _framePacingLiveData
    }

    private val _hdrLiveData = MutableLiveData<Boolean>()
    val hdrLiveData: LiveData<Boolean> by lazy {
        _hdrLiveData
    }

    private val _muteHostPcLiveData = MutableLiveData<Boolean>()
    val muteHostPcLiveData: LiveData<Boolean> by lazy {
        _muteHostPcLiveData
    }

    private val _touchScreenOptionLiveData = MutableLiveData<TouchScreenOption>()
    val touchScreenOptionLiveData: LiveData<TouchScreenOption> by lazy {
        _touchScreenOptionLiveData
    }

    private val _gameOptimizationLiveData = MutableLiveData<Boolean>()
    val gameOptimizationLiveData: LiveData<Boolean> by lazy {
        _gameOptimizationLiveData
    }

    private val _autoCloseGameCountDownLiveData = MutableLiveData<Int>(RemotePlaySettingsPref.autoCloseGameCountDown)
    val autoCloseGameCountDownLiveData: LiveData<Int> by lazy {
        _autoCloseGameCountDownLiveData
    }

    private val _videoFormatOptionLiveData = MutableLiveData(remotePlaySettingsManager.getVideoFormatOption())
    val videoFormatOptionLiveData: LiveData<FormatOption> by lazy {
        _videoFormatOptionLiveData
    }


    init {
        reinitLiveData()
    }

    private fun reinitLiveData() {
        viewModelScope.launch {
            _displayModeLiveData.value = remotePlaySettingsManager.getDisplayMode()
            _cropDisplaySafeAreaLiveData.value = remotePlaySettingsManager.getCropDisplaySafeArea()
            _limitRefreshRateLiveData.value = remotePlaySettingsManager.isLimitRefreshRate()
            _bitrateSettingsLiveData.value = remotePlaySettingsManager.getBitrateSettings()
            _framePacingLiveData.value = remotePlaySettingsManager.getFramePacing()
            _hdrLiveData.value = remotePlaySettingsManager.getEnableHdr()
            _muteHostPcLiveData.value = remotePlaySettingsManager.getMuteHostPc()
            _touchScreenOptionLiveData.value = remotePlaySettingsManager.getTouchScreenOption()
            _gameOptimizationLiveData.value = remotePlaySettingsManager.getGameOptimization()
            _autoCloseGameCountDownLiveData.value = remotePlaySettingsManager.getAutoCloseGameCountDown()
            _videoFormatOptionLiveData.value = remotePlaySettingsManager.getVideoFormatOption()
        }
    }

    fun onResume() {
        viewModelScope.launch {
            withContext(ioDispatcher) { NexusContentProvider.sync(appContext) }
            reinitLiveData()
        }
    }

    fun setDisplayMode(option: DisplayModeOption) {
        viewModelScope.launch {
            remotePlaySettingsManager.setDisplayMode(option)
            _displayModeLiveData.value = option
        }
    }

    fun setCropDisplaySafeArea(isChecked: Boolean) {
        viewModelScope.launch {
            remotePlaySettingsManager.setCropDisplaySafeArea(isChecked)
            _cropDisplaySafeAreaLiveData.value = isChecked
        }
    }

    fun setLimitRefreshRate(isChecked: Boolean) {
        viewModelScope.launch {
            remotePlaySettingsManager.setLimitRefreshRate(isChecked)
            _limitRefreshRateLiveData.value = isChecked
            _displayModeLiveData.value = remotePlaySettingsManager.getDisplayMode()
        }
    }

    fun setBitrateSettings(rate: Int) {
        viewModelScope.launch {
            val default = BitrateRateSettings.default
            remotePlaySettingsManager.setBitrateSettings(rate.coerceIn(default.min, default.max))
            _bitrateSettingsLiveData.value = BitrateRateSettings.default.copy(rate = rate)
        }
    }

    fun setFramePacing(option: FramePacingOption) {
        viewModelScope.launch {
            remotePlaySettingsManager.setFramePacing(option)
            if (_framePacingLiveData.value != option) {
                _framePacingLiveData.value = option
            }
        }
    }

    fun setEnableHdr(isChecked: Boolean) {
        viewModelScope.launch {
            remotePlaySettingsManager.setEnableHdr(isChecked)
            _hdrLiveData.value = isChecked
        }
    }

    fun setMuteHostPc(isChecked: Boolean) {
        viewModelScope.launch {
            remotePlaySettingsManager.setMuteHostPc(isChecked)
            _muteHostPcLiveData.value = isChecked
        }
    }

    fun setTouchScreen(option: TouchScreenOption) {
        viewModelScope.launch {
            remotePlaySettingsManager.setTouchScreen(option)
            if (_touchScreenOptionLiveData.value != option) {
                _touchScreenOptionLiveData.value = option
            }
        }
    }

    fun setGameOptimization(isChecked: Boolean) {
        viewModelScope.launch {
            remotePlaySettingsManager.setGameOptimization(isChecked)
            _gameOptimizationLiveData.value = isChecked
        }
    }


    fun setAutoCloseGameCountDown(value: Int) {
        viewModelScope.launch {
            remotePlaySettingsManager.setAutoCloseGameCountDown(value)
            _autoCloseGameCountDownLiveData.value = value
        }
    }


    fun setVideoFormatOption(videoFormatOption: FormatOption) {
        viewModelScope.launch {
            remotePlaySettingsManager.setVideoFormatOption(videoFormatOption)
            _videoFormatOptionLiveData.value = videoFormatOption
        }
    }


}