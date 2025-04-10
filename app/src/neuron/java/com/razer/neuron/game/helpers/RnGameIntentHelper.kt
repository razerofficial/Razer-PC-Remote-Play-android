package com.razer.neuron.game.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.limelight.BuildConfig
import com.limelight.Game
import com.limelight.RemotePlayConfig
import com.razer.neuron.game.RnGame
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvApp
import com.razer.neuron.RnApp
import com.razer.neuron.common.logAndRecordException
import com.razer.neuron.common.toast
import com.razer.neuron.main.RnMainActivity
import com.razer.neuron.nexus.NexusContentProvider
import com.razer.neuron.nexus.sources.NexusComputerDetailsSource
import java.security.cert.CertificateEncodingException

/**
 * A collection of functions to create [Intent] to launch [RnGame]
 */
interface RnGameIntentHelper {

    /**
     * Check if [intent] wants to start [RnGame] ([Game])
     */
    fun hasStartStreamIntent(intent: Intent) = intent.getBooleanExtra(
        RnMainActivity.EXTRA_START_STREAMING,
        false
    )

    /**
     * Check if [intent] has all the [Bundle] extras to start [RnGame] ([Game])
     *
     * No need to check [Game.EXTRA_SERVER_CERT], since it is not secure to send that across via intent
     */
    @Throws(RuntimeException::class)
    fun checkStartStreamExtras(intent: Intent) {
        with(intent) {
            requireNotNull(getStringExtra(Game.EXTRA_HOST)) { "${Game.EXTRA_HOST} not specified" }
            require(getIntExtra(Game.EXTRA_PORT, -1) > -1) { "Valid ${Game.EXTRA_PORT} not specified" }
            require(getIntExtra(Game.EXTRA_HTTPS_PORT, -1) > -1) { "Valid ${Game.EXTRA_HTTPS_PORT} not specified" }
            requireNotNull(getStringExtra(Game.EXTRA_APP_NAME)) { "${Game.EXTRA_APP_NAME} not specified" }
            require(getIntExtra(Game.EXTRA_APP_ID, Int.MAX_VALUE) != Int.MAX_VALUE) { "Valid ${Game.EXTRA_APP_ID} not specified (e.g. 1577243657)" }
            require(hasExtra(Game.EXTRA_APP_HDR)) { "${Game.EXTRA_APP_HDR} not specified" }
            require(hasExtra(Game.EXTRA_UNIQUEID)) { "${Game.EXTRA_UNIQUEID} not specified. Just use ${RemotePlayConfig.default.defaultUniqueId}" }
            requireNotNull(getStringExtra(Game.EXTRA_PC_UUID)) { "${Game.EXTRA_PC_UUID} not specified" }
            requireNotNull(getStringExtra(Game.EXTRA_PC_NAME)) { "${Game.EXTRA_PC_NAME} not specified" }
            // EXTRA_HOST_ACTIVE_DISPLAY_MODE not really required, but it won't work right if we don't
            // have it
        }
    }

    /**
     * Use all the extras from [Activity.getIntent] (e.g. [Game.EXTRA_HOST]..) and start the [RnGame] ([Game])
     * activity to start streaming.
     *
     * Catch the [RuntimeException] for error.
     *
     * In the case when [Game.EXTRA_SERVER_CERT] is not in the [Activity.getIntent], it will
     * use [NexusContentProvider.computerDetailsSource] to get [com.limelight.nvstream.http.ComputerDetails.serverCert]
     * via [NexusComputerDetailsSource.getByUuid] and put the [ByteArray] in [Game.EXTRA_SERVER_CERT]
     */
    @Throws(RuntimeException::class)
    suspend fun createStartStreamIntent(context: Context, intent : Intent): Intent {
        suspend fun getServerCertFromContentProvider() = with(intent) {
            val uuid =
                requireNotNull(getStringExtra(Game.EXTRA_PC_UUID)) { "${Game.EXTRA_PC_UUID} required" }
            val computerDetails =
                requireNotNull(NexusContentProvider.computerDetailsSource.getByUuid(context, uuid)) { "Computer $uuid not found in Nexus" }
            requireNotNull(computerDetails.serverCert) { "Computer $uuid has no cert" }
        }
        checkStartStreamExtras(intent)
        if (!intent.hasExtra(Game.EXTRA_SERVER_CERT)) {
            // TODO remove try-catch once Nexus starts to ComputerDetails these properly.
            val serverCert = try {
                getServerCertFromContentProvider()
            } catch (t: Throwable) {
                toast(t.message)
                null
            }
            if (serverCert != null) {
                intent.putExtra(
                    Game.EXTRA_SERVER_CERT,
                    serverCert.encoded
                )
            }
        }
        return Intent(context, RnGame::class.java).apply {
            intent.flags != Intent.FLAG_ACTIVITY_NEW_TASK
            intent.extras?.let { putExtras(it) }
        }
    }

    @Throws(RuntimeException::class)
    fun createStartStreamIntent(app: NvApp, details: ComputerDetails, uniqueId: String): Intent {
        return Intent(RnApp.appContext, RnGame::class.java).apply {
            putExtra(Game.EXTRA_HOST, details.activeAddress.address)
            putExtra(Game.EXTRA_PORT, details.activeAddress.port)
            putExtra(Game.EXTRA_HTTPS_PORT, details.httpsPort)
            putExtra(Game.EXTRA_APP_NAME, app.appName)
            putExtra(Game.EXTRA_APP_ID, app.appId)
            putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported)
            putExtra(Game.EXTRA_UNIQUEID, uniqueId)
            putExtra(Game.EXTRA_PC_UUID, details.uuid)
            putExtra(Game.EXTRA_PC_NAME, details.name)
            putExtra(Game.EXTRA_HOST_ACTIVE_DISPLAY_MODE, details.activeDisplayMode?.format())
            try {
                if (details.serverCert != null) {
                    putExtra(Game.EXTRA_SERVER_CERT, details.serverCert.encoded)
                }
            } catch (e: CertificateEncodingException) {
                logAndRecordException(e)
            }
        }
    }

    fun updateIntentHostAndPort(intent: Intent, computerDetails: ComputerDetails) {
        computerDetails.activeAddress?.let {
            activeAddress ->
            intent.putExtra(Game.EXTRA_HOST, activeAddress.address)
            intent.putExtra(Game.EXTRA_PORT, activeAddress.port)
        }
        intent.putExtra(Game.EXTRA_HTTPS_PORT, computerDetails.httpsPort)
    }
}