package com.razer.neuron.nexus.sources

import android.content.Context
import android.database.Cursor
import com.limelight.binding.crypto.AndroidCryptoProvider
import com.razer.neuron.extensions.convertToHex
import com.razer.neuron.extensions.getBooleanByColumnName
import com.razer.neuron.extensions.getIntByColumnName
import com.razer.neuron.extensions.getStringByColumnName
import com.razer.neuron.extensions.hexToByteArray
import com.razer.neuron.shared.SharedConstants
import com.razer.neuron.nexus.NexusContentSource
import timber.log.Timber
import java.io.File

class NexusMetaDataSource : NexusContentSource {
    override val path get() = SharedConstants.NEXUS_META_DATA
    private val uriPath get() = SharedConstants.baseNexusUri.buildUpon().appendPath(path).build()

    private fun getIntValue(context: Context, key: String): Int? {
        return getCursor(context, key)?.use { c ->
            val value =
                if (c.moveToFirst()) c.getIntByColumnName(key) else null
            value
        }
    }

    private fun getBooleanValue(context: Context, key: String): Boolean? {
        return getCursor(context, key)?.use { c ->
            val value =
                if (c.moveToFirst()) c.getBooleanByColumnName(key) else null
            value
        }
    }

    private fun getCursor(context: Context, key: String): Cursor? {
        return context.contentResolver.query(
            uriPath,
            arrayOf(key),
            null,
            null,
            null,
        )
    }


    fun getBuildVersionCode(context: Context) = getIntValue(context, SharedConstants.BUILD_VERSION_CODE).also {
        Timber.v("getBuildVersionCode: $it")
    }

    fun getAIDLVersionCode(context: Context) = getIntValue(context, SharedConstants.AIDL_VERSION).also {
        Timber.v("getAIDLVersionCode: $it")
    }

    fun isControllerForegroundServiceRunning(context: Context) = getBooleanValue(context, SharedConstants.IS_CONTROLLER_FOREGROUND_SERVICE_RUNNING).also {
        Timber.v("isControllerForegroundServiceRunning: $it")
    }

    fun isControllerSensaSupported(context: Context) = getBooleanValue(context, SharedConstants.IS_CONTROLLER_SENSA_SUPPORTED).also {
        Timber.v("isControllerSensaSupported: $it")
    }

    fun isControllerManualXInputVibrationSupported(context: Context) = getBooleanValue(context, SharedConstants.IS_CONTROLLER_MANUAL_XINPUT_VIBRATION_SUPPORTED).also {
        Timber.v("isControllerManualXInputVibrationSupported: $it")
    }

    /**
     * See [com.limelight.binding.crypto.AndroidCryptoProvider]
     */
    override suspend fun sync(context: Context) = Unit

}