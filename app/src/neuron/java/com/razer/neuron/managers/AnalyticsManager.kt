package com.razer.neuron.managers

import com.razer.neuron.RnApp
import com.razer.neuron.model.NeuronEvent
import com.razer.neuron.model.NeuronEventFields
import kotlinx.coroutines.launch
import timber.log.Timber

object AnalyticsManager {
    fun queueToSend(event : NeuronEvent) = RnApp.globalScope.launch {
        Timber.v("queueToSend: ${event.uuid} ${event.timestamp} ${event.name}  ${event.jsonString}")
        SimpleDbManager.insertOrReplace(NeuronEventFields.EVENT_TABLE_NAME, event.toContentValues())
    }
}


