package com.razer.neuron

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.limelight.BuildConfig
import com.limelight.NeuronBridge
import com.razer.neuron.common.BaseApplication.BaseActivityLifecycleCallbacks
import com.razer.neuron.common.RnThemeManager
import com.razer.neuron.common.debugToast
import com.razer.neuron.extensions.globalOnUnexpectedError
import com.razer.neuron.managers.AIDLManager
import com.razer.neuron.nexus.NexusContentProvider
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.settings.remoteplay.RemotePlaySettingsManager
import com.razer.neuron.utils.now
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NotNull
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * 1. Open [com.razer.neuron.main.RnMainActivity]. It will determine which activity to open next
 * 2. Open [com.razer.neuron.oobe.RnOobeActivity], if it [RemotePlaySettingsPref.isOobeCompleted] is not true
 *   or show other intro pages
 * 3. If oobe closes [com.razer.neuron.main.RnMainActivity] will be opened again
 * 4. Perform sync with [NexusContentProvider.sync]
 * 5. If [com.razer.neuron.game.helpers.RnGameIntentHelper.hasStartStreamIntent] then call
 * [com.razer.neuron.game.helpers.RnGameIntentHelper.createStartStreamIntent] so that view layer can start
 * [com.razer.neuron.game.RnGame]
 * 5. If no stream intent then call [com.razer.neuron.settings.RnSettingsActivity.startActivity]
 */
@HiltAndroidApp
class RnApp : Application(){

    @Inject
    lateinit var remotePlaySettingsManager: RemotePlaySettingsManager // do not remove, as we want to run RemotePlaySettingsManager.init


    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        initActivityLifeCycleCallback()
        RnThemeManager.onApplicationCreated(this)
        NeuronBridge.setImplementation(RnNeuronBridgeImpl(this))
        Timber.plant(if(BuildConfig.DEBUG || RemotePlaySettingsPref.isDevModeEnabled) Timber.DebugTree() else ReleaseTree())
    }

    private fun initActivityLifeCycleCallback() {
        registerActivityLifecycleCallbacks(object : BaseActivityLifecycleCallbacks() {
            private fun updateLastActiveTimestamp() {
                RemotePlaySettingsPref.neuronLastActiveAt = now()
            }
            override fun onActivityResumed(p0: Activity) {
                updateLastActiveTimestamp()
                globalScope.launch {
                    AIDLManager.bindService(this@RnApp).exceptionOrNull()?.let {
                        debugToast(it.message)
                    }
                }
                lastResumedRef = WeakReference(p0)
            }
            override fun onActivityPaused(p0: Activity) {
                updateLastActiveTimestamp()
                AIDLManager.unbindService(this@RnApp)
            }
        })
    }


    companion object {
        lateinit var appContext : Context
        val appContextOrNull get() = if(::appContext.isInitialized) appContext else null
        val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + globalOnUnexpectedError)

        private var lastResumedRef: WeakReference<Activity>? = null

        /**
         * The last [Activity] that called [Activity.onResume]
         */
        val lastResumed: Activity?
            get() = lastResumedRef?.get()
    }
}



/**
 * Restart the app. (should only use it for testing config change)
 */
fun Context.restartApp(bundle: Bundle = Bundle()) {
    packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        val restartIntent = android.content.Intent.makeRestartActivityTask(intent.component)
        restartIntent.putExtras(bundle)
        startActivity(restartIntent) // Launches a new launch intent.
        Runtime.getRuntime().exit(0) // Kills the current app process.
    }
}


class ReleaseTree : @NotNull Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.ERROR || priority == Log.WARN){
            //SEND ERROR REPORTS TO YOUR Crashlytics.
        }
    }
}