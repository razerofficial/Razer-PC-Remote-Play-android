package com.razer.neuron.shared

import com.limelight.preferences.PreferenceConfiguration
import com.razer.neuron.extensions.toUriOrNull


object SharedConstants {
    const val nexusAuthorities = "com.razer.bianca"
    const val neuronAuthorities = "com.razer.neuron"

    /**
     * content://com.razer.bianca/Nexus
     */
    val baseNexusUri = checkNotNull("content://$nexusAuthorities/Nexus".toUriOrNull()) { "Failed to parse uri" }
    val baseNeuronUri = checkNotNull("content://$neuronAuthorities/Neuron".toUriOrNull()) { "Failed to parse uri" }

    const val KRATE_ITEM_NAME = "name"
    const val KRATE_ITEM_VALUE = "value"

    // AndroidCryptoProvider
    const val ANDROID_CRYPTO_PROVIDER = "AndroidCryptoProvider"
    const val NAME = "name"
    const val VALUE = "value"
    const val CLIENT_CRT = "client_crt"
    const val CLIENT_KEY = "client_key"
    const val KEY_CLIENT_CRT = CLIENT_CRT
    const val KEY_CLIENT_KEY = CLIENT_KEY

    // ComputerDetails
    const val COMPUTER_DETAILS = "ComputerDetails"
    const val COMPUTER_DETAILS_UUID = "uuid"

    // Settings
    const val REMOTE_PLAY_SETTINGS = "NeuronSettings"

    // neuron Metadata
    const val NEURON_META_DATA = "MetaData"
    const val VIDEO_FORMAT_JSON = "video_format_json"



    // nexus Metadata
    const val NEXUS_META_DATA = "MetaData"
    const val AIDL_VERSION = "aidl_version"
    const val IS_CONTROLLER_FOREGROUND_SERVICE_RUNNING = "is_controller_foreground_service_running"
    const val IS_CONTROLLER_SENSA_SUPPORTED = "is_controller_sensa_supported"
    const val IS_CONTROLLER_MANUAL_XINPUT_VIBRATION_SUPPORTED = "is_controller_manual_xinput_vibration_supported"
    const val BUILD_VERSION_CODE = "version_code"

    // Moonlight pref keys
    const val REDUCE_REFRESH_RATE_PREF_STRING = PreferenceConfiguration.REDUCE_REFRESH_RATE_PREF_STRING
    const val SEEKBAR_BITRATE_KBPS = PreferenceConfiguration.BITRATE_PREF_STRING
    const val FRAME_PACING = PreferenceConfiguration.FRAME_PACING_PREF_STRING
    const val ENABLE_HDR = PreferenceConfiguration.ENABLE_HDR_PREF_STRING
    const val HOST_AUDIO = PreferenceConfiguration.HOST_AUDIO_PREF_STRING
    const val TOUCHSCREEN_TRACKPAD = PreferenceConfiguration.TOUCHSCREEN_TRACKPAD_PREF_STRING
    const val ENABLE_SOPS = PreferenceConfiguration.SOPS_PREF_STRING
    const val LIST_FPS = PreferenceConfiguration.FPS_PREF_STRING
    const val LIST_RESOLUTION = PreferenceConfiguration.RESOLUTION_PREF_STRING
    const val VIDEO_FORMAT_PREF_STRING = PreferenceConfiguration.VIDEO_FORMAT_PREF_STRING

    const val DEFAULT_LIST_FPS = "60" // must match preferences.xml
    const val DEFAULT_LIST_RESOLUTION = "1280x720" // must match preferences.xml
}

object RazerRemotePlaySettingsKey {


    /**
     * nexus-neuron only
     * See BAA-1767
     */
    const val PREF_DISPLAY_MODE = "PREF_DISPLAY_MODE_V2"

    /**
     * nexus-neuron only
     * See BAA-1767
     */
    const val PREF_VIRTUAL_DISPLAY_VIDEO_REFRESH_RATE = "PREF_VIRTUAL_DISPLAY_VIDEO_REFRESH_RATE"


    /**
     * nexus-neuron only
     * See BAA-1767
     */
    const val PREF_PC_DISPLAY_VIDEO_REFRESH_RATE = "PREF_PC_DISPLAY_VIDEO_REFRESH_RATE"

    /**
     * nexus-neuron only
     * See BAA-1767
     */
    const val PREF_VIRTUAL_DISPLAY_CROP_TO_SAFE_AREA = "PREF_VIRTUAL_DISPLAY_CROP_TO_SAFE_AREA"

}





