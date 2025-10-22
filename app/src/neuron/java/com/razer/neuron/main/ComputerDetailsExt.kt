package com.razer.neuron.main

import android.content.Context
import androidx.annotation.WorkerThread
import com.limelight.BuildConfig
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import com.razer.neuron.common.debugToast
import com.razer.neuron.common.doOnError
import com.razer.neuron.utils.now
import com.razer.neuron.utils.randomInt
import okio.IOException
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.jvm.Throws

private val pollingExecutor = Executors.newFixedThreadPool(6)

/**
 * @return all non-null addresses
 */
fun ComputerDetails.ipAddresses() = listOf(localAddress, manualAddress, remoteAddress, ipv6Address)
    .filterNotNull()

/**
 * A class to encapsulate the result of [getActiveComputer]
 */
class ActiveComputer(
    val computerDetails: ComputerDetails,
    val activeAddress: AddressTuple,
    val http: NvHTTP) {
    override fun toString(): String {
        return computerDetails.toString()
    }
}

/**
 * Get the active [AddressTuple] from [ComputerDetails.localAddress], [ComputerDetails.manualAddress],
 * [ComputerDetails.remoteAddress], [ComputerDetails.ipv6Address]
 *
 * @return a [Result] of the [ComputerDetails] fetched
 */
@Throws(java.io.IOException::class, IllegalStateException::class)
@WorkerThread
fun ComputerDetails.getActiveComputer(tag : String, context : Context, timeoutMs : Long = 2000L) : Result<ActiveComputer> {
    val _tag = "$tag:getActiveComputer(${randomInt(0,100)})"
    val startTime = now()
    val ipAddresses = ipAddresses()

    Timber.v("$_tag: checkActiveComputerDetails from ${ipAddresses}. Current active is ${activeAddress}")
    val allFutures = ipAddresses.map { address ->
        CompletableFuture.supplyAsync({
            address to runCatching { checkActiveComputerDetails(_tag, context, address) }.doOnError {
                Timber.w("$_tag: checkActiveComputerDetails at ${address} result in ${it.message}")
            }
        }, pollingExecutor)
    }
    var activeAddressAssigned = false
    val reachableFutures = allFutures.map { addressAndResult ->
        CompletableFuture<ActiveComputer>()
            .apply {
                addressAndResult.thenAccept { (address, result) ->
                    val activeComputer = result.getOrNull()
                    Timber.v("$_tag: testing address is $address, result isSuccess=${result.isSuccess}, activeAddress=${activeAddress}")
                    if (result.isSuccess && activeComputer != null && !activeAddressAssigned) {
                        activeAddressAssigned = true
                        activeAddress = address // assign the activeAddress
                        complete(activeComputer)
                    }
                }
            }
    }
    val anyOfFuture = CompletableFuture.anyOf(*reachableFutures.toTypedArray())
    val firstResult = try { anyOfFuture.get(timeoutMs+ 500, TimeUnit.MILLISECONDS) } catch (t : TimeoutException) { null }
    Timber.v("$_tag: firstResult is ${firstResult} took ${now() - startTime}ms")
    return (firstResult as? ActiveComputer)?.let { Result.success(it) } ?: Result.failure(IOException("Computer not reachable"))
}

/**
 * Test the [ipAddress] by creating a [NvHTTP], and then call [NvHTTP.getServerInfo]
 * if there is no error, then create a [ActiveComputer]
 */
@Throws(java.io.IOException::class, XmlPullParserException::class)
private fun ComputerDetails.checkActiveComputerDetails(tag : String, context : Context, ipAddress : AddressTuple) : ActiveComputer {
    val _tag = "$tag:checkActiveComputerDetails:(${ipAddress})"
    val logInfo = mutableMapOf<String, Any?>()
    val _activeAddress : AddressTuple? = activeAddress
    logInfo["state"] = state
    logInfo["activeAddress"] = _activeAddress
    val startTime = now()
    val portMatchesActiveAddress =
        state == ComputerDetails.State.ONLINE && _activeAddress != null && ipAddress.port == _activeAddress.port
    val http = NvHTTP(
        ipAddress,
        if (portMatchesActiveAddress) httpsPort else 0,
        null,
        serverCert,
        PlatformBinding.getCryptoProvider(context))
    // If this PC is currently online at this address, extend the timeouts to allow more time for the PC to respond.
    val isLikelyOnline =
        state == ComputerDetails.State.ONLINE && ipAddress == _activeAddress

    /**
     * ACTUAL API call to check if [ipAddress] is "active"
     */
    val serverInfoXml = http.getServerInfo(isLikelyOnline)
    val fetchedComputerDetails = http.getComputerDetails(serverInfoXml)
        .also {
        if(BuildConfig.DEBUG) {
            logInfo["state"]= NvHTTP.getXmlString(serverInfoXml, "state", false)
            logInfo["currentgame"] = NvHTTP.getXmlString(serverInfoXml, "currentgame", false)
        }
        Timber.v("$_tag: ${logInfo.entries.joinToString { (k,v) -> "$k=$v" }} took ${now() - startTime}ms")
    }
    // success, so we are confirmed that the cert and address are valid
    fetchedComputerDetails.serverCert = serverCert
    fetchedComputerDetails.activeAddress = ipAddress
    update(fetchedComputerDetails)

    return ActiveComputer(
        computerDetails = this,
        activeAddress = ipAddress,
        http = http)
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