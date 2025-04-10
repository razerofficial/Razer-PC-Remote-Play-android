package com.razer.neuron.nexus.sources

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.DisplayMode
import com.razer.neuron.extensions.convertToHex
import com.razer.neuron.extensions.defaultJson
import com.razer.neuron.extensions.getIntByColumnName
import com.razer.neuron.extensions.getStringByColumnName
import com.razer.neuron.extensions.hexToByteArray
import com.razer.neuron.extensions.toList
import com.razer.neuron.extensions.toX509Cert
import com.razer.neuron.model.Addresses
import com.razer.neuron.model.RnAddressTuple
import com.razer.neuron.shared.SharedConstants
import com.razer.neuron.shared.SharedContentException
import com.razer.neuron.nexus.NexusContentSource
import com.razer.neuron.nexus.NexusProviderModelHelper
import com.razer.neuron.pref.RemotePlaySettingsPref
import kotlin.jvm.Throws

class NexusComputerDetailsSource(val manager: ComputerDatabaseManager) : NexusContentSource {
    override val path get() = SharedConstants.COMPUTER_DETAILS

    private val uriPath get() = SharedConstants.baseNexusUri.buildUpon().appendPath(path).build()

    @Throws(SharedContentException::class)
    suspend fun getAll(context : Context): List<ComputerDetails> {
        return checkOrThrowPermission(context) {
            context.contentResolver.query(
                uriPath,
                null,
                null,
                null,
                null,
            ).use { c ->
                c?.let { createList(it) } ?: emptyList()
            }
        }
    }

    @Throws(SharedContentException::class)
    suspend fun getByUuid(context : Context, uuid: String): ComputerDetails? {
        return checkOrThrowPermission(context) {
            context.contentResolver.query(
                uriPath,
                null,
                "${SharedConstants.COMPUTER_DETAILS_UUID} = ?",
                arrayOf(uuid),
                null,
            ).use { c ->
                c?.let { createList(it) } ?: emptyList()
            }
        }.firstOrNull()
    }

    @Throws(SharedContentException::class)
    suspend fun insertOrUpdate(context : Context, computerDetails: ComputerDetails) {
        checkOrThrowPermission(context) {
            if (getByUuid(context, computerDetails.uuid) == null) {
                // insert
                context.contentResolver.insert(
                    uriPath,
                    contentValuesOf(computerDetails)
                )
            } else {
                // update
                context.contentResolver.update(
                    uriPath, contentValuesOf(computerDetails),
                    "${SharedConstants.COMPUTER_DETAILS_UUID} = ?",
                    arrayOf(computerDetails.uuid)
                )
            }
        }
    }

    /**
     * We only insert/update paired [ComputerDetails] that are not in [RemotePlaySettingsPref.manuallyUnpaired]
     */
    override suspend fun sync(context: Context) {
        val tag = "NexusComputerDetailsSource.sync"
        logger("$tag: getAll")
        val all = getAll(context).toMutableList()
        val manuallyUnpaired = RemotePlaySettingsPref.manuallyUnpaired
        fun ComputerDetails.wasManuallyUnpaired() = manuallyUnpaired.contains(uuid)
        for(computer in all) {
            logger("$tag: getAll: name=${computer.name}," +
                    "uuid=${computer.uuid}," +
                    "wasManuallyUnpaired=${computer.wasManuallyUnpaired()}," +
                    "isPaired=${computer.serverCert != null}," +
                    "machineIdentifier=${computer.machineIdentifier}"
            )
        }

        val computersToInsertOrUpdate = all.toMutableList().apply {
            replaceAll {
                if(it.wasManuallyUnpaired()) it.apply { serverCert = null } else it
            }
        }

        deletePossibleDuplicates(tag, computersToInsertOrUpdate)

        logger("$tag: computersToInsertOrUpdate=${computersToInsertOrUpdate.size} (out of ${all.size})")
        computersToInsertOrUpdate.forEach {
            manager.updateComputer(it)
        }
    }

    /**
     * BAA-2406, these are computers that were the same but Cortex has been reinstalled
     */
    private fun deletePossibleDuplicates(tag : String, computersToInsertOrUpdate : List<ComputerDetails>) {
        val map = computersToInsertOrUpdate
            .filter { it.machineIdentifier != null }
            .associateBy { it.machineIdentifier }

        val allComputers = manager.allComputers
        logger("$tag: deletePossibleDuplicates: allComputers ${allComputers.size}")
        val duplicates = manager.allComputers.filter {
            computer ->
            val machineIdentifier = computer.machineIdentifier
            logger("$tag: deletePossibleDuplicates: CHECKING ${computer.name} ${computer.uuid} machineIdentifier=${machineIdentifier}")
            if(machineIdentifier != null) {
                val existing = map[machineIdentifier]
                // has matching machineIdentifier but uuid is different
                existing != null && existing.uuid != computer.uuid
            } else {
                false
            }
        }
        duplicates.forEach {
            logger("$tag: deletePossibleDuplicates: DELETING ${it.name} ${it.uuid} machineIdentifier=${it.machineIdentifier}")
            manager.deleteComputer(it)
        }
    }


    companion object : NexusProviderModelHelper<ComputerDetails> {
        private const val COL_UUID = "uuid"
        private const val COL_NAME = "name"
        private const val COL_ADDRESSES_JSON = "addresses_json"
        private const val COL_MAC_ADDRESS = "mac_address"
        private const val COL_SERVER_CERT = "server_cert"
        private const val COL_RUNNING_GAME_ID = "running_game_id"
        private const val COL_ACTIVE_DISPLAY_MODE_JSON = "active_display_mode_json"
        private const val COL_MACHINE_IDENTIFIER = "machine_identifier"
        private const val COL_SERVER_CODEC_MODE_SUPPORT = "server_codec_mode_support"
        private const val COL_RAZER_HOST_VERSION = "cortex_host_version"

        override val allFields = mapOf(
            COL_UUID to String::class.java,
            COL_NAME to String::class.java,
            COL_ADDRESSES_JSON to String::class.java,
            COL_MAC_ADDRESS to String::class.java,
            COL_SERVER_CERT to String::class.java,
            COL_RUNNING_GAME_ID to Int::class.java,
            COL_ACTIVE_DISPLAY_MODE_JSON to String::class.java,
            COL_MACHINE_IDENTIFIER to String::class.java,
            COL_SERVER_CODEC_MODE_SUPPORT to Int::class.java,
            COL_RAZER_HOST_VERSION to String::class.java,
        )

        override fun createList(cursor: Cursor): List<ComputerDetails> {
            return cursor.toList { c ->
                ComputerDetails().apply {
                    uuid = requireNotNull(c.getStringByColumnName(COL_UUID)) { "$COL_UUID is null" }
                    name = requireNotNull(c.getStringByColumnName(COL_NAME)) { "$COL_NAME is null" }
                    val addressesJson =
                        requireNotNull(c.getStringByColumnName(COL_ADDRESSES_JSON)) { "$COL_ADDRESSES_JSON is null" }
                    val addresses = defaultJson.fromJson(addressesJson, Addresses::class.java)
                    localAddress = addresses.local?.toAddressTuple()
                    remoteAddress = addresses.remote?.toAddressTuple()
                    ipv6Address = addresses.ipv6?.toAddressTuple()
                    manualAddress = addresses.manual?.toAddressTuple()
                    macAddress = c.getStringByColumnName(COL_MAC_ADDRESS)
                    serverCert =
                        c.getStringByColumnName(COL_SERVER_CERT)?.hexToByteArray()?.toX509Cert()
                    runningGameId = c.getIntByColumnName(COL_RUNNING_GAME_ID) ?: 0
                    activeDisplayMode = c.getStringByColumnName(COL_ACTIVE_DISPLAY_MODE_JSON)?.let { DisplayMode.fromJson(it) }
                    machineIdentifier = c.getStringByColumnName(COL_MACHINE_IDENTIFIER)
                    serverCodecModeSupport = c.getIntByColumnName(COL_SERVER_CODEC_MODE_SUPPORT) ?: 0
                    razerHostVersion = c.getStringByColumnName(COL_RAZER_HOST_VERSION)
                }
            }
        }

        override fun contentValuesOf(obj: ComputerDetails): ContentValues {
            return com.razer.neuron.extensions.contentValuesOf(
                COL_UUID to obj.uuid,
                COL_NAME to obj.name,
                COL_ADDRESSES_JSON to defaultJson.toJson(
                    Addresses(
                        local = obj.localAddress?.let { RnAddressTuple(it) },
                        remote = obj.remoteAddress?.let { RnAddressTuple(it) },
                        ipv6 = obj.ipv6Address?.let { RnAddressTuple(it) },
                        manual = obj.manualAddress?.let { RnAddressTuple(it) },
                    )
                ),
                COL_MAC_ADDRESS to obj.macAddress,
                COL_SERVER_CERT to obj.serverCert?.encoded?.convertToHex(false),
                COL_RUNNING_GAME_ID to obj.runningGameId,
                COL_ACTIVE_DISPLAY_MODE_JSON to obj.activeDisplayMode?.toJsonString(),
                COL_MACHINE_IDENTIFIER to obj.machineIdentifier,
                COL_SERVER_CODEC_MODE_SUPPORT to obj.serverCodecModeSupport,
                COL_RAZER_HOST_VERSION to obj.razerHostVersion
            )
        }
    }
}