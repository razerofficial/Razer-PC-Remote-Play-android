package com.razer.neuron.common

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity.BIND_AUTO_CREATE
import com.limelight.computers.ComputerManagerListener2
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import timber.log.Timber

/**
 * A class to help use [ComputerManagerService] inside an activity
 *
 * Remember to call
 * - [onCreate], [onDestroy], [onResume], [onPause]
 *
 * Override the following:
 * - [onBeforeStartComputerUpdates] if you need to do some operation before polling
 * - [onComputerDetailsUpdated] if you need to update UI
 * - [onComputerDetailsRemoved] if a computer needs to be removed on the UI
 */
abstract class ComputerServiceHelper {

    abstract val computerServiceActivity: ComponentActivity?

    var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    var freezeUpdates = false
        private set
    var runningPolling = false
        private set
    var inForeground = false
        private set

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val localBinder = binder as ComputerManagerService.ComputerManagerBinder
            Thread {
                Timber.v("serviceConnection: waitForReady")
                localBinder.waitForReady()
                Timber.v("serviceConnection: onServiceConnected=$localBinder")
                managerBinder = localBinder
                Timber.v("serviceConnection: startComputerUpdates")
                startComputerUpdates()
            }.start()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            managerBinder = null
        }
    }

    private fun bindComputerService() {
        Timber.v("bindComputerService: bindService")
        val activity = computerServiceActivity ?: return
        Timber.v("bindComputerService: activity=$activity")
        activity.bindService(
            Intent(activity.baseContext, ComputerManagerService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
        Timber.v("bindComputerService: done")
    }


    /**
     * Normally, it will call [startComputerPolling]
     */
    fun startComputerUpdates() {
        Timber.v("startComputerUpdates: runningPolling=$runningPolling, inForeground=$inForeground")
        if (!runningPolling && inForeground) {
            freezeUpdates = false
            val managerBinder = managerBinder
            Timber.v("startComputerUpdates: managerBinder=$managerBinder")
            if (managerBinder != null) {
                onBeforeStartComputerUpdates()
            }
        }
    }

    /**
     * Override this to do operation before call [startComputerPolling]
     */
    open fun onBeforeStartComputerUpdates() {
        Timber.v("onBeforeStartComputerUpdates")
        startComputerPolling()
    }

    /**
     * if you need to do some operation before polling
     */
    fun startComputerPolling()  {
        Timber.v("startComputerPolling")
        managerBinder?.let {
            it.restartPolling(object : ComputerManagerListener2 {
                override fun notifyComputerUpdated(details: ComputerDetails?) {
                    details ?: return
                    Timber.v("startComputerPolling: notifyComputerUpdated: ${details.name} (${details.activeAddress}) (freezeUpdates=$freezeUpdates)")
                    if (!freezeUpdates) {
                        runOnUiThread {
                            onComputerDetailsUpdated(fromNeuron = false, details)
                        }
                    }
                }

                override fun notifyComputerRemoved(details: ComputerDetails) {
                    Timber.v("startComputerPolling: notifyComputerRemoved: ${details.name} (${details.activeAddress}) (freezeUpdates=$freezeUpdates)")
                    if (!freezeUpdates) {
                        runOnUiThread {
                            onComputerDetailsRemoved(fromNeuron = false, details)
                        }
                    }
                }
            })
            runningPolling = true
        }
    }

    /**
     * If you need to update UI
     */
    open fun onComputerDetailsUpdated(fromNeuron : Boolean, details: ComputerDetails?) = Unit

    /**
     * if a computer needs to be removed on the UI
     */
    open fun onComputerDetailsRemoved(fromNeuron : Boolean, details: ComputerDetails) = Unit


    fun onCreate() {
        Timber.v("onCreate: bindComputerService")
        bindComputerService()
    }


    fun onResume() {
        inForeground = true
        Timber.v("onResume: startComputerUpdates")
        startComputerUpdates()
    }

    fun onPause() {
        inForeground = false
        Timber.v("onPause: stopComputerUpdates")
        stopComputerUpdates(false)
    }

    fun stopComputerUpdates(wait: Boolean) {
        Timber.v("stopComputerUpdates: wait=$wait runningPolling=$runningPolling, managerBinder=$managerBinder")
        if (!runningPolling) {
            return
        }
        freezeUpdates = true
        managerBinder?.stopPolling()
        if (wait) {
            managerBinder?.waitForPollingStopped()
        }
        runningPolling = false
    }

    fun onDestroy() {
        Timber.v("onDestroy: managerBinder=$managerBinder")
        if (managerBinder != null) {
            Timber.v("onDestroy: unbindService")
            computerServiceActivity?.unbindService(serviceConnection)
        }
    }
}