package com.razer.neuron.model

import kotlinx.coroutines.Job

class OverlayHintState(
    val isPressing: Boolean,
    val keyDownJob: Job?) {

    companion object {
        val empty = OverlayHintState(false, null)
    }

}