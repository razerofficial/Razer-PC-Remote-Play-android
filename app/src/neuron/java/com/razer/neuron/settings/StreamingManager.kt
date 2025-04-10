package com.razer.neuron.settings

import android.content.Intent
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.http.isPaired
import com.limelight.utils.ServerHelper
import com.razer.neuron.RnApp
import timber.log.Timber

import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.exceptions.NeuronPairingException
import com.razer.neuron.exceptions.NeuronStreamException
import com.razer.neuron.exceptions.RnException
import com.razer.neuron.extensions.getStringExt
import com.razer.neuron.game.RnGame
import com.razer.neuron.pref.RemotePlaySettingsPref
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.LinkedList
import javax.inject.Singleton

sealed class PairingStage(val computerDetails: ComputerDetails) {
    class AlreadyPaired(computerDetails: ComputerDetails) : PairingStage(computerDetails)
    class Start(computerDetails: ComputerDetails) : PairingStage(computerDetails)
    class ShowPinCode(computerDetails: ComputerDetails, val pinCode: String) : PairingStage(computerDetails)
    class Pairing(computerDetails: ComputerDetails) : PairingStage(computerDetails)
    class Error(computerDetails: ComputerDetails, val e: Throwable) : PairingStage(computerDetails)
    class Success(computerDetails: ComputerDetails, val pairState: PairState) : PairingStage(computerDetails)
}

/**
 * For handling local streaming class.
 * It mainly create [NvHTTP] to handle pairing, data fetching, and pc's app local db.
 */
@Singleton
class StreamingManager(
    @IoDispatcher
    val ioDispatcher: CoroutineDispatcher,
    val manager: ComputerDatabaseManager
) {

    private val _pairingStageFlow = MutableSharedFlow<PairingStage>()
    val pairingStageFlow by lazy {
        _pairingStageFlow.asSharedFlow()
    }

    private val _startStreamFlow = MutableSharedFlow<Intent>()

    /**
     * Once this emits, you should not call
     * [android.app.Activity.startActivity] on the intent right away.
     * 
     * Use [com.razer.neuron.startgame.RnStartGameViewModel.askBeforeStartStream] first
     * if possible. Then let the [com.razer.neuron.startgame.RnStartGameViewModel] tell
     * [com.razer.neuron.startgame.RnStartGameView] to start the activity.
     */
    val startStreamFlow by lazy {
        _startStreamFlow.asSharedFlow()
    }

    suspend fun doPair(
        uniqueId: String?,
        details: ComputerDetails
    ) {
        withContext(ioDispatcher) {
            if (details.state == ComputerDetails.State.OFFLINE || details.activeAddress == null) {
                _pairingStageFlow.emit(PairingStage.Error(details, NeuronPairingException(
                    getStringExt(R.string.pair_pc_offline)
                )))
            } else if (details.isPaired()) {
                _pairingStageFlow.emit(PairingStage.AlreadyPaired(details))
            } else {
                try {
                    _pairingStageFlow.emit(PairingStage.Start(details))
                    val pinCode = PairingManager.generatePinString()
                    _pairingStageFlow.emit(PairingStage.ShowPinCode(details, pinCode))
                    val httpConn = getNvHttp(uniqueId, details)
                    val pm = httpConn.pairingManager
                    _pairingStageFlow.emit(PairingStage.Pairing(details))
                    val pairState = pm.pair(httpConn.getServerInfo(true), pinCode)
                    if (pairState == PairState.PAIRED) {
                        details.pairState = pairState
                        details.state = ComputerDetails.State.ONLINE
                        details.serverCert = pm.pairedCert
                        RemotePlaySettingsPref.manuallyUnpaired -= details.uuid
                        manager.updateComputer(details)
                        _pairingStageFlow.emit(PairingStage.Success(details, pairState))
                    } else if (pairState == PairState.PIN_WRONG) {
                        _pairingStageFlow.emit(PairingStage.Error(details, NeuronPairingException(getStringExt(R.string.pair_incorrect_pin))))
                    } else {
                        _pairingStageFlow.emit(PairingStage.Error(details, NeuronPairingException(getStringExt(R.string.rn_warning_unable_to_pair))))
                    }
                } catch (e: Exception) {
                    _pairingStageFlow.emit(PairingStage.Error(details, e))
                }
            }
        }
    }


    suspend fun doUnpair(
        uniqueId: String,
        details: ComputerDetails
    ): Result<Unit> {
        return withContext(ioDispatcher) {
            return@withContext try {
                val httpConn = getNvHttp(uniqueId, details)
                httpConn.unpair()
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.w(e)
                Result.failure(e)
            } finally {
                details.serverCert = null
                details.pairState = PairingManager.PairState.NOT_PAIRED
                manager.updateComputer(details)
                RemotePlaySettingsPref.manuallyUnpaired += details.uuid
            }
        }
    }

    suspend fun startStream(
        uniqueId: String,
        details: ComputerDetails,
    ): Result<ComputerDetails> {
        return withContext(ioDispatcher) {
            if (details.state == ComputerDetails.State.OFFLINE || details.activeAddress == null) {
                return@withContext Result.failure(RnException(getStringExt(R.string.pair_pc_offline)))
            }
            return@withContext try {
                val httpConn = getNvHttp(uniqueId, details)
                httpConn.appList.getDesktopAppOrNull()?.let {
                    val intent = RnGame.createStartStreamIntent(
                        it,
                        details,
                        uniqueId
                    )
                    _startStreamFlow.emit(intent)
                    Result.success(details)
                } ?: run {
                    Result.failure(NeuronStreamException(getStringExt(R.string.rn_no_app_to_launch)))
                }
            } catch (e: Exception) {
                Result.failure(NeuronStreamException(e.message ?: "", e))
            }
        }
    }

    fun getNvHttp(uniqueId: String?, details: ComputerDetails): NvHTTP {
        return NvHTTP(
            ServerHelper.getCurrentAddressFromComputer(details),
            details.httpsPort,
            uniqueId,
            details.serverCert,
            PlatformBinding.getCryptoProvider(RnApp.appContext)
        )
    }

    private fun LinkedList<NvApp>.getDesktopAppOrNull(): NvApp? {
        for (app in this) {
            if (app.appName.contains("Desktop", true)) {
                return app
            }
        }
        return this.getOrNull(0)
    }
}