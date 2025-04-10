package com.razer.neuron.main

import android.content.Context
import androidx.annotation.WorkerThread
import com.limelight.BuildConfig
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import com.razer.neuron.common.debugToast
import okio.IOException
import timber.log.Timber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val pollingExecutor = Executors.newFixedThreadPool(6)

/**
 * Get the active [AddressTuple] from [ComputerDetails.localAddress], [ComputerDetails.manualAddress],
 * [ComputerDetails.remoteAddress], [ComputerDetails.ipv6Address]
 *
 * @param isUpdateThis, true if you want to also update this [ComputerDetails]
 * @return a [Result] of the active [AddressTuple] and also call [ComputerDetails.update] if
 * [isUpdateThis] is true
 */
@WorkerThread
fun ComputerDetails.findActiveAddress(context : Context, timeoutMs : Long = 2000L, isUpdateThis : Boolean = true) : Result<AddressTuple> {
    val ipAddresses = listOf(localAddress, manualAddress, remoteAddress, ipv6Address)
        .filterNotNull()
    val allFutures = ipAddresses.map { address ->
        CompletableFuture.supplyAsync({
            address to runCatching { pollForUpdatedComputerDetails(context, address) }
        }, pollingExecutor)
    }
    val reachableFutures = allFutures.map { addressAndResult ->
        CompletableFuture<AddressTuple>()
            .apply {
                addressAndResult.thenAccept { (address, result) ->
                    val computerDetails = result.getOrNull()
                    if (result.isSuccess && computerDetails != null) {
                        computerDetails.activeAddress = address // assign the activeAddress
                        if(isUpdateThis) {
                            update(computerDetails)
                        }
                        complete(address)
                    }
                }
            }
    }
    val anyOfFuture = CompletableFuture.anyOf(*reachableFutures.toTypedArray())
    val firstReachableAddress = try { anyOfFuture.get(timeoutMs+ 500, TimeUnit.MILLISECONDS) } catch (t : TimeoutException) { null }
    return (firstReachableAddress as? AddressTuple)?.let { Result.success(it) } ?: Result.failure(IOException("Computer not reachable"))
}

private fun ComputerDetails.pollForUpdatedComputerDetails(context : Context, ipAddress : AddressTuple) : ComputerDetails {
    val portMatchesActiveAddress =
        state == ComputerDetails.State.ONLINE && activeAddress != null && ipAddress.port == activeAddress.port
    val http = NvHTTP(
        ipAddress,
        if (portMatchesActiveAddress) httpsPort else 0,
        null,
        serverCert,
        PlatformBinding.getCryptoProvider(context))
    // If this PC is currently online at this address, extend the timeouts to allow more time for the PC to respond.
    val isLikelyOnline =
        state == ComputerDetails.State.ONLINE && ipAddress == activeAddress
    val serverInfoXml = http.getServerInfo(isLikelyOnline)
    if(BuildConfig.DEBUG) {
        val state = NvHTTP.getXmlString(serverInfoXml, "state", false)
        val currentGame = NvHTTP.getXmlString(serverInfoXml, "currentgame", false)
        Timber.v("pollForUpdatedComputerDetails: state=$state, currentGame=$currentGame")
    }
    return http.getComputerDetails(serverInfoXml)
}

fun ComputerDetails.getRazerHostVersionInt() = razerHostVersion?.versionToLong()

/**
 * After 1.1.0.0, the host will support auto restart
 *
 * NEUR-159
 */
fun ComputerDetails.isAutoRestartSupported() = if(BuildConfig.DEBUG) {
    // TODO: please remove
    debugToast("Assuming host will auto restart")
    true
} else {
    (getRazerHostVersionInt() ?: 0) >= ("1.1.0.0".versionToLong() ?: Long.MAX_VALUE)
}

/**
 * Convert version code like 9.999.9.99 to 009_999_009_099
 */
fun String.versionToLong(maxDigitsPerPart : IntArray = intArrayOf(3, 3, 3, 3)) : Long? {
    require(maxDigitsPerPart.size <= 4)
    val buffer = StringBuilder()
    this.split(".").take(4).forEachIndexed { index, part ->
        val maxDigits = maxDigitsPerPart.getOrNull(index) ?: 3
        buffer.append(("0".repeat(maxDigits) + part).takeLast(maxDigits))
    }
    return buffer.toString().toLongOrNull()
}