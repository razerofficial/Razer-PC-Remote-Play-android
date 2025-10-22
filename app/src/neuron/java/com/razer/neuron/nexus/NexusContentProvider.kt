package com.razer.neuron.nexus

import android.content.Context
import android.content.pm.PackageManager
import com.limelight.computers.ComputerDatabaseManager
import com.razer.neuron.RnApp
import timber.log.Timber


import com.razer.neuron.extensions.getInstalledNexusPackage
import com.razer.neuron.nexus.sources.NexusAndroidCryptoSource
import com.razer.neuron.nexus.sources.NexusComputerDetailsSource
import com.razer.neuron.nexus.sources.NexusMetaDataSource
import com.razer.neuron.nexus.sources.NexusRemotePlaySettingsSource
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.pref.RemotePlaySettingsPref.isReadOnlyKey
import com.razer.neuron.settings.PairingStage
import com.razer.neuron.shared.SharedContentException
import com.razer.neuron.utils.now
import com.razer.neuron.utils.toDebugTimeString
import com.razer.neuron.utils.transformSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.Throws
import kotlin.time.DurationUnit
import kotlin.time.toDuration


/**
 * Central place where we sync all the [SharedContent] together or where you can access
 * all the [SharedContent] instances
 */
object NexusContentProvider {
    val computerDetailsSource = NexusComputerDetailsSource(ComputerDatabaseManager(RnApp.appContext))
    val androidCryptoSource = NexusAndroidCryptoSource()
    @JvmStatic
    val settingsSource = NexusRemotePlaySettingsSource()

    @JvmStatic
    val metaDataSource = NexusMetaDataSource()

    private val sources by lazy {
        /**
         * [computerDetailsSource] is last because it depends on [settingsSource]
         */
        arrayOf(settingsSource, androidCryptoSource, computerDetailsSource)
    }

    private val mutex = Mutex()

    /**
     * Directly read from [NexusRemotePlaySettingsSource]
     * and bypass [RemotePlaySettingsPref.isReadOnlyKey]
     *
     * This is just for timestamps so that we can know if we should override the rest of our local
     * data via [sync]
     *
     * Run [NexusContentSource.sync] of all [sources]
     *
     * Returns [Result]
     */
    suspend fun sync(context: Context): Result<Unit> {
        mutex.withLock {
            /**
             * Since we are using [NexusRemotePlaySettingsSource] directly, we need to
             * check permission first.
             */
            val nexusLastActiveAt = try {
                checkContentProviderPermissionOrThrow(context) {
                    settingsSource.getLongPref(
                        context,
                        RemotePlaySettingsPref.NEXUS_LAST_ACTIVE_TIMESTAMP
                    ) ?: 0
                }
            } catch (t : Throwable) {
                return Result.failure(t)
            }
            val neuronLastSyncAt = RemotePlaySettingsPref.neuronLastSyncAt
            if (nexusLastActiveAt <= neuronLastSyncAt) {
                Timber.w("No need to sync. nexusLastActiveAt=${nexusLastActiveAt.toDebugTimeString()}, neuronLastSyncAt=${neuronLastSyncAt.toDebugTimeString()}")
                return Result.success(Unit)
            } else {
                val lastUpdateDuration = nexusLastActiveAt - neuronLastSyncAt
                Timber.i(
                    "Need to sync. Last change was ${
                        lastUpdateDuration.toDuration(
                            DurationUnit.MILLISECONDS
                        ).inWholeSeconds
                    } ago. nexusLastActiveAt=${nexusLastActiveAt.toDebugTimeString()}, neuronLastSyncAt=${neuronLastSyncAt.toDebugTimeString()}"
                )
            }


            val results = sources.map {
                try {
                    it.sync(context)
                    Result.success(Unit)
                } catch (t: Throwable) {
                    Timber.w(t)
                    Result.failure(t)
                }
            }

            val firstError = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            return if (results.all { it.isSuccess }) {
                RemotePlaySettingsPref.neuronLastSyncAt = now()
                Result.success(Unit)
            } else {
                Result.failure(
                    firstError ?: Exception("Failed with no exception")
                )
            }
        }
    }

    @Throws(SharedContentException::class)
    suspend fun getBuildVersionCode(context: Context) = checkContentProviderPermissionOrThrow(context) {
        metaDataSource.getBuildVersionCode(context)
    }

    @Throws(SharedContentException::class)
    suspend fun getAIDLVersionCode(context: Context) = checkContentProviderPermissionOrThrow(context) {
        metaDataSource.getAIDLVersionCode(context)
    }

    @Throws(SharedContentException::class)
    suspend fun isControllerForegroundServiceRunning(context: Context) = checkContentProviderPermissionOrThrow(context) {
        metaDataSource.isControllerForegroundServiceRunning(context)
    }


    @Throws(SharedContentException::class)
    suspend fun isControllerSensaSupported(context: Context) = checkContentProviderPermissionOrThrow(context) {
        metaDataSource.isControllerSensaSupported(context)
    }


    @Throws(SharedContentException::class)
    suspend fun isControllerManualXInputVibrationSupported(context: Context) = checkContentProviderPermissionOrThrow(context) {
        metaDataSource.isControllerManualXInputVibrationSupported(context)
    }


}


const val SHARED_CONTENT_PROVIDER_DATA_ACCESS_PERMISSION =
    "com.razer.bianca.provider.NexusContentProvider.DATA_ACCESS_PERMISSION"

/**
 * As Kevin mention Nexus with Neuron integration will be 4.0.0
 */
const val MIN_NEXUS_VERSION_CODE_WITH_DATA_ACCESS_PERMISSION = 4000000L
const val DEBUG_NEXUS_VERSION_CODE = 9999999L


enum class NexusPackageStatus {
    NotInstalled, ValidVersion, InvalidVersion
}
fun Context.getNexusPackageStatus() : NexusPackageStatus {
    val installedNexusPackage = getInstalledNexusPackage()
    installedNexusPackage ?: return NexusPackageStatus.NotInstalled
    return if(!isNexusVersionValid(installedNexusPackage)) {
        NexusPackageStatus.InvalidVersion
    } else {
        NexusPackageStatus.ValidVersion
    }
}

private fun Context.isNexusVersionValid(appPackageName : String) : Boolean {
    return runCatching {
        val packageInfo = packageManager.getPackageInfo(appPackageName,
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS) ?: return false
        packageInfo.providers?.toList()?.any {
            it.readPermission == SHARED_CONTENT_PROVIDER_DATA_ACCESS_PERMISSION
        }
    }.getOrNull() ?: false
}

private fun Context.hasNexusInstalled() : Boolean {
    return getInstalledNexusPackage() != null
}

