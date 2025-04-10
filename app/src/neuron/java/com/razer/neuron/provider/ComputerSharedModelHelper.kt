package com.razer.neuron.provider

import android.content.ContentValues
import com.limelight.nvstream.http.ComputerDetails
import com.razer.neuron.extensions.defaultJson
import com.razer.neuron.extensions.toX509Cert
import com.razer.neuron.model.Addresses
import com.razer.neuron.provider.NeuronProviderModelHelper
import com.razer.neuron.extensions.hexToByteArray
import com.razer.neuron.extensions.convertToHex
import com.razer.neuron.model.RnAddressTuple

object ComputerSharedModelHelper: NeuronProviderModelHelper<ComputerDetails> {
    const val COL_UUID = "uuid"
    const val COL_NAME = "name"
    const val COL_ADDRESSES_JSON = "addresses_json"
    const val COL_MAC_ADDRESS = "mac_address"
    const val COL_SERVER_CERT = "server_cert"
    const val COL_RUNNING_GAME_ID = "running_game_id"
    const val COL_MACHINE_IDENTIFIER = "machine_identifier"
    const val COL_SERVER_CODEC_MODE_SUPPORT = "server_codec_mode_support"
    const val COL_RAZER_HOST_VERSION = "cortex_host_version"

    override val allFields = mapOf(
        COL_UUID to String::class.java,
        COL_NAME to String::class.java,
        COL_ADDRESSES_JSON to String::class.java,
        COL_MAC_ADDRESS to String::class.java,
        COL_SERVER_CERT to String::class.java,
        COL_RUNNING_GAME_ID to Int::class.java,
        COL_MACHINE_IDENTIFIER to String::class.java,
        COL_SERVER_CODEC_MODE_SUPPORT to Int::class.java,
        COL_RAZER_HOST_VERSION to String::class.java,
    )

    override fun create(values: ContentValues): ComputerDetails {
        return ComputerDetails().apply {
            uuid = requireNotNull(values.getAsString(COL_UUID)) { "$COL_UUID field missing" }
            name = requireNotNull(values.getAsString(COL_NAME)) { "$COL_NAME field missing" }
            val addressesJson = requireNotNull(values.getAsString(COL_ADDRESSES_JSON)) { "$COL_ADDRESSES_JSON field missing" }
            val addresses = addressesJson.takeIf { it.isNotBlank() }?.let { json -> defaultJson.fromJson(json, Addresses::class.java) }
            localAddress = addresses?.local?.toAddressTuple()
            remoteAddress = addresses?.remote?.toAddressTuple()
            ipv6Address = addresses?.ipv6?.toAddressTuple()
            manualAddress = addresses?.manual?.toAddressTuple()
            macAddress = values.getAsString(COL_MAC_ADDRESS)
            serverCert = values.getAsString(COL_SERVER_CERT)?.hexToByteArray()?.toX509Cert()
            runningGameId = values.getAsInteger(COL_RUNNING_GAME_ID)
            machineIdentifier = values.getAsString(COL_MACHINE_IDENTIFIER)
            serverCodecModeSupport = values.getAsInteger(COL_SERVER_CODEC_MODE_SUPPORT)
            razerHostVersion = values.getAsString(COL_RAZER_HOST_VERSION)
        }
    }

    override fun toKeyValuePairs(obj: ComputerDetails): Array<Pair<String, Any?>> {
        return arrayOf(
            COL_UUID to obj.uuid,
            COL_NAME to obj.name,
            COL_ADDRESSES_JSON to defaultJson.toJson(
                Addresses(
                    local = obj.localAddress?.toRnAddressTuple(),
                    remote = obj.remoteAddress?.toRnAddressTuple(),
                    ipv6 = obj.ipv6Address?.toRnAddressTuple(),
                    manual = obj.manualAddress?.toRnAddressTuple()
                )
            ),
            COL_MAC_ADDRESS to obj.macAddress,
            COL_SERVER_CERT to obj.serverCert?.encoded?.convertToHex(false),
            COL_RUNNING_GAME_ID to obj.runningGameId,
            COL_MACHINE_IDENTIFIER to obj.machineIdentifier,
            COL_SERVER_CODEC_MODE_SUPPORT to obj.serverCodecModeSupport,
            COL_RAZER_HOST_VERSION to obj.razerHostVersion
        )
    }

    private fun ComputerDetails.AddressTuple.toRnAddressTuple(): RnAddressTuple {
        return RnAddressTuple(
            address = address,
            port = port
        )
    }
}