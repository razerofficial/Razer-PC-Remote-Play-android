package com.razer.neuron.model

import android.content.ContentValues
import com.google.gson.annotations.SerializedName
import com.limelight.BuildConfig
import com.razer.neuron.extensions.defaultJson
import com.razer.neuron.utils.now
import java.util.UUID

/**
 * Add any analytics events here.
 */
sealed class NeuronEvent(
    /**
     * This field should not be serialized, as it is store in a separate [com.razer.neuron.managers.Column]
     * [NeuronEventFields.EVENT_UUID]
     */
    @Transient
    val uuid: String,
    /**
     * This field should not be serialized, as it is store in a separate [com.razer.neuron.managers.Column]
     * [NeuronEventFields.EVENT_TIMESTAMP]
     */
    @Transient
    val timestamp : Long,
    /**
     * Name of the event
     * This field should not be serialized, as it is store in a separate [com.razer.neuron.managers.Column]
     * [NeuronEventFields.EVENT_NAME]
     *
     * e.g. "on_app_foreground"
     */
    @Transient
    val name : String) {

    /**
     * A Json string of all the non-exposed fields
     */
    val jsonString : String? get() = kotlin.runCatching { defaultJson.toJson(this) }.getOrNull()



    data class StreamStarted(
        @SerializedName("pc_app_name")
        val pcAppName: String,
        @SerializedName("negotiated_res")
        val negotiatedRes: String? = null,
        @SerializedName("refresh_rate")
        val refreshRate: String? = null,
        @SerializedName("display_mode")
        val displayMode: String? = null,
    ) : NeuronEvent(randomUuid(), now(), "neuron_stream_started")

    data class NeuronInstalled(
        @SerializedName("neuron_app_version")
        val versionName: String,
        @SerializedName("neuron_app_version_code")
        val versionCode: Long,
    ) : NeuronEvent(randomUuid(), now(), EVENT_NAME) {
        companion object {
            /**
             * Must match the string in Nexus.
             *
             * See com.razer.bianca.model.analytics.AnalyticsEvent.NeuronInstalled.name
             */
            const val EVENT_NAME = "neuron_installed"
        }
    }



    data class StreamEnded(
        @SerializedName("pc_app_name")
        val pcAppName: String,
        @SerializedName("neuron_app_version")
        val neuronAppVersion: String = BuildConfig.VERSION_NAME,
        @SerializedName("total_duration_ms")
        val totalDurationMs: Long? = null,
        @SerializedName("negotiated_res")
        val negotiatedRes: String? = null,
        @SerializedName("refresh_rate")
        val refreshRate: String? = null,
        @SerializedName("display_mode")
        val displayMode: String? = null,
        @SerializedName("decoder_name")
        val decoderName: String? = null,
        @SerializedName("avg_total_fps")
        val avgTotalFps: Float? = null,
        @SerializedName("avg_received_fps")
        val avgReceivedFps: Float? = null,
        @SerializedName("avg_rendered_fps")
        val avgRenderedFps: Float? = null,
        @SerializedName("net_drops_percent")
        val netDropsPercent: Float? = null,
        @SerializedName("avg_net_latency_ms")
        val avgNetLatencyMs: Int? = null,
        @SerializedName("avg_net_latency_variance_ms")
        var avgNetLatencyVarianceMs : Int? = null,
        @SerializedName("min_host_processing_latency_ms")
        var minHostProcessingLatencyMs : Float? = null,
        @SerializedName("max_host_processing_latency_ms")
        var maxHostProcessingLatencyMs : Float? = null,
        @SerializedName("avg_host_processing_latency_ms")
        var avgHostProcessingLatencyMs : Float? = null,
        @SerializedName("decode_time_ms")
        val decodeTimeMs: Float? = null,
    ) : NeuronEvent(randomUuid(), now(), "neuron_stream_ended")


    /**
     * (For adding new events, no need to change)
     * For simplicity sake, all custom fields are just packed inside [NeuronEventFields.EVENT_DATA_JSON]
     */
    fun toContentValues() : ContentValues {
        return ContentValues().apply {
            put(NeuronEventFields.EVENT_UUID, uuid)
            put(NeuronEventFields.EVENT_TIMESTAMP, timestamp)
            put(NeuronEventFields.EVENT_NAME, name)
            put(NeuronEventFields.EVENT_DATA_JSON, jsonString)
        }
    }
}

private fun randomUuid() = UUID.randomUUID().toString()

/**
 * (For adding new events, no need to change)
 *
 * Used by [com.razer.neuron.managers.Table.NeuronEventsTable]
 */
object NeuronEventFields {
    const val EVENT_TIMESTAMP: String = "event_timestamp"
    const val EVENT_UUID: String = "event_uuid"
    const val EVENT_NAME: String = "event_name"
    const val EVENT_DATA_JSON: String = "event_data_json"
    const val EVENT_TABLE_NAME: String = "neuron_events"
}