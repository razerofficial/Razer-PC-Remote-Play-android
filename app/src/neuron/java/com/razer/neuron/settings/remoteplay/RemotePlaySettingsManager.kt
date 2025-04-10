package com.razer.neuron.settings.remoteplay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import com.razer.neuron.extensions.FormatOptionMeta
import com.razer.neuron.extensions.getMeta
import com.razer.neuron.extensions.putAny
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.model.FramePacingOption
import com.razer.neuron.model.HostAudioOption
import com.razer.neuron.model.TouchScreenOption
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.shared.SharedConstants.ENABLE_HDR
import com.razer.neuron.shared.SharedConstants.ENABLE_SOPS
import com.razer.neuron.shared.SharedConstants.FRAME_PACING
import com.razer.neuron.shared.SharedConstants.HOST_AUDIO
import com.razer.neuron.shared.SharedConstants.REDUCE_REFRESH_RATE_PREF_STRING
import com.razer.neuron.shared.SharedConstants.SEEKBAR_BITRATE_KBPS
import com.razer.neuron.shared.SharedConstants.TOUCHSCREEN_TRACKPAD
import com.razer.neuron.shared.SharedConstants.VIDEO_FORMAT_PREF_STRING
import kotlin.math.max


class RemotePlaySettingsManager(
    val context: Context,
    val moonlightPref: SharedPreferences
) {

    init {
        applyPreset()
    }
    fun getDisplayMode(): DisplayModeOption {
        return RemotePlaySettingsPref.displayMode
    }

    fun getCropDisplaySafeArea(): Boolean {
        return RemotePlaySettingsPref.isCropDisplaySafeArea
    }

    fun isLimitRefreshRate(): Boolean {
        return moonlightPref.getBoolean(REDUCE_REFRESH_RATE_PREF_STRING, false)
    }

    fun getBitrateSettings(): BitrateRateSettings {
        val default = BitrateRateSettings.default
        return default.copy(rate = moonlightPref.getInt(SEEKBAR_BITRATE_KBPS, default.rate))
    }

    fun getFramePacing(): FramePacingOption {
        val framePacing = moonlightPref.getString(FRAME_PACING, FramePacingOption.Balanced.key)
        return when (framePacing) {
            FramePacingOption.LowLatency.key -> {
                FramePacingOption.LowLatency
            }
            FramePacingOption.Balanced.key -> {
                FramePacingOption.Balanced
            }
            FramePacingOption.SmoothestVideo.key -> {
                FramePacingOption.SmoothestVideo
            }
            else -> FramePacingOption.Balanced
        }
    }

    fun getEnableHdr(): Boolean {
        return moonlightPref.getBoolean(ENABLE_HDR, false)
    }

    fun getMuteHostPc(): Boolean {
        val playHostAudio =  when (moonlightPref.getBoolean(HOST_AUDIO, false)) {
            true -> HostAudioOption.DeviceAndPc
            false -> HostAudioOption.DeviceOnly
        }
        return playHostAudio == HostAudioOption.DeviceOnly
    }

    fun getTouchScreenOption(): TouchScreenOption {
        val value = moonlightPref.getBoolean(TOUCHSCREEN_TRACKPAD, false)
        return if (value) TouchScreenOption.VirtualTrackpad else TouchScreenOption.DirectTouch
    }

    fun getGameOptimization(): Boolean {
        return moonlightPref.getBoolean(ENABLE_SOPS, true)
    }


    fun getAutoCloseGameCountDown() = RemotePlaySettingsPref.autoCloseGameCountDown

    fun getVideoFormatOption(): FormatOption {
        val prefValue = moonlightPref.getString(VIDEO_FORMAT_PREF_STRING, null)
        return FormatOptionMeta.values().firstOrNull { it.prefValue == prefValue }?.formatOption ?: FormatOptionMeta.AUTO.formatOption
    }


    fun setDisplayMode(displayMode: DisplayModeOption) {
        val previousDisplayMode = getDisplayMode()
        RemotePlaySettingsPref.displayMode = displayMode
        // BAA-2341
        if(displayMode == DisplayModeOption.PhoneOnlyDisplay) {
            setMuteHostPc(true)
        }
        if(previousDisplayMode != displayMode) {
            RemotePlaySettingsPref.isUseFallbackResolution = false
        }
    }

    fun setCropDisplaySafeArea(isChecked: Boolean) {
        RemotePlaySettingsPref.isCropDisplaySafeArea = isChecked
    }

    fun setLimitRefreshRate(isChecked: Boolean) {
        moonlightPref.edit().putBoolean(REDUCE_REFRESH_RATE_PREF_STRING, isChecked).apply()
    }

    fun setBitrateSettings(rate: Int) {
        moonlightPref.edit().putInt(SEEKBAR_BITRATE_KBPS, rate).apply()
    }

    fun setFramePacing(option: FramePacingOption) {
        moonlightPref.edit().putString(FRAME_PACING, option.key).apply()
    }

    fun setEnableHdr(isChecked: Boolean) {
        moonlightPref.edit().putBoolean(ENABLE_HDR, isChecked).apply()
    }

    fun setMuteHostPc(isMute: Boolean) {
        val option = if (isMute) HostAudioOption.DeviceOnly else HostAudioOption.DeviceAndPc
        val playHostAudio = when (option) {
            HostAudioOption.DeviceAndPc -> true
            HostAudioOption.DeviceOnly -> false
        }
        moonlightPref.edit().putBoolean(HOST_AUDIO, playHostAudio).apply()
    }

    fun setTouchScreen(option: TouchScreenOption) {
        val value = when (option) {
            TouchScreenOption.DirectTouch -> false
            TouchScreenOption.VirtualTrackpad -> true
        }
        moonlightPref.edit().putBoolean(TOUCHSCREEN_TRACKPAD, value).apply()
    }

    fun setGameOptimization(isChecked: Boolean) {
        moonlightPref.edit().putBoolean(ENABLE_SOPS, isChecked).apply()
    }

    fun setAutoCloseGameCountDown(value : Int) {
        RemotePlaySettingsPref.autoCloseGameCountDown = value
    }

    fun setVideoFormatOption(formatOption: FormatOption) {
        moonlightPref.edit()
            .putString(VIDEO_FORMAT_PREF_STRING, formatOption.getMeta()?.prefValue ?: FormatOptionMeta.default.prefValue)
            .apply()
    }



    /**
     * NEUR-90
     *
     * Similar to default, but this is set on applications start
     * for keys that are not populated yet.
     */
    private fun applyPreset() = with(moonlightPref) {
        val keyValuePairs = listOf(
            SEEKBAR_BITRATE_KBPS to BitrateRateSettings.default.rate,
            FRAME_PACING to "balanced", // this value is mapped to neuron's preferences.xml (and then arrays.xml)
            PreferenceConfiguration.STRETCH_PREF_STRING to false,
            PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING to "2",// 2=STEREO, (also 51 and 71)  this value is mapped to neuron's preferences.xml (and then arrays.xml)
            PreferenceConfiguration.ENABLE_AUDIO_FX_PREF_STRING to false,
            PreferenceConfiguration.DEADZONE_PREF_STRING to 1, // should be set as 1 (moonlight default is 7)
            PreferenceConfiguration.MULTI_CONTROLLER_PREF_STRING to true,
            PreferenceConfiguration.USB_DRIVER_PREF_SRING to false,
            PreferenceConfiguration.MOUSE_EMULATION_STRING to false,
            PreferenceConfiguration.FLIP_FACE_BUTTONS_PREF_STRING to false,
            PreferenceConfiguration.GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING to false, // need to be true actually for mouse to work in desktop
            PreferenceConfiguration.GAMEPAD_MOTION_SENSORS_PREF_STRING to false,
            PreferenceConfiguration.GAMEPAD_MOTION_FALLBACK_PREF_STRING to false,
            PreferenceConfiguration.TOUCHSCREEN_TRACKPAD_PREF_STRING to false,
            PreferenceConfiguration.MOUSE_NAV_BUTTONS_STRING to false,
            PreferenceConfiguration.ABSOLUTE_MOUSE_MODE_PREF_STRING to false,
            PreferenceConfiguration.ONSCREEN_CONTROLLER_PREF_STRING to false,
            PreferenceConfiguration.ENABLE_PIP_PREF_STRING to false,
            PreferenceConfiguration.DISABLE_TOASTS_PREF_STRING to false,
            PreferenceConfiguration.VIDEO_FORMAT_PREF_STRING to FormatOptionMeta.default.prefValue,
            PreferenceConfiguration.ENABLE_HDR_PREF_STRING to false,
            PreferenceConfiguration.FULL_RANGE_PREF_STRING to false,
            PreferenceConfiguration.ENABLE_PERF_OVERLAY_STRING to false,
            PreferenceConfiguration.LATENCY_TOAST_PREF_STRING to false,
        )
        edit {
            keyValuePairs.forEach { (k,v) ->
                if(!contains(k)) {
                    Log.v("RemotePlaySettingsManager", "applyPreset: $k=$v")
                    putAny(k, v)
                }
            }
        }
    }
}

data class BitrateRateSettings(
    val min : Int,
    val max : Int,
    val rate : Int
) {

    companion object {
        /**
         * Taken from "seekbar_bitrate_kbps" in preferences.xml
         *
         * HOWEVER ...
         *
         * In moonlight it is 500 Mbps
         * but in neuron it is 1000 Mbps
         * https://razersw.atlassian.net/wiki/spaces/RTWWEAR/pages/2817163291/Neuron+Setting+2024-06-11+Android+iOS
        */
        val default = BitrateRateSettings(min = 1000, max = 150000, rate = 30000)
        const val KEY_STEP = 1000 // 1000 mbps
    }
}
