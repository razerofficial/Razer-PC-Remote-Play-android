package com.razer.neuron

import com.limelight.BuildConfig
import com.limelight.RemotePlayConfig
import com.razer.neuron.pref.RemotePlaySettingsPref

object RnConstants {
    const val IS_ALLOW_DEV_MODE = true

    /**
     * BAA-2405
     */
    const val NEXUS_DOWNLOAD_URL = "https://rzr.to/nexus-app-android"
}

fun isShowVerboseErrorMessage() = BuildConfig.DEBUG || RemotePlaySettingsPref.isDevModeEnabled