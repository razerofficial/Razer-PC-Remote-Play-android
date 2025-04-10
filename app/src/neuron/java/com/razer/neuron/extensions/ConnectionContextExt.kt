package com.razer.neuron.extensions


const val SCM_H264 = 0x00001
const val SCM_HEVC = 0x00100
const val SCM_HEVC_MAIN10 = 0x00200
const val SCM_AV1_MAIN8 = 0x10000 // Sunshine extension
const val SCM_AV1_MAIN10 = 0x20000 // Sunshine extension
const val SCM_AV1_MASK_10BIT = SCM_AV1_MAIN10 or SCM_AV1_MAIN8 // Sunshine extension


private val ALL_SCM_FLAGS = listOf(SCM_H264, SCM_HEVC, SCM_HEVC_MAIN10, SCM_AV1_MAIN8, SCM_AV1_MAIN10)

private fun Int.flagToString() = when(this) {
    SCM_H264 -> "H264"
    SCM_HEVC -> "HEVC"
    SCM_HEVC_MAIN10 -> "HEVC_MAIN10"
    SCM_AV1_MAIN8 -> "AV1_MAIN8"
    SCM_AV1_MAIN10 -> "AV1_MAIN10"
    else -> "UNKNOWN_$this"
}

/**
 * Uses flags like [com.razer.neuron.RnNeuronBridgeImpl.SCM_AV1_MAIN8] ...
 *
 * Don't use the codec for logic, use it for display only
 * @return a string representation of what server codec(s) are supported
 */
fun Int.parseServerCodecModeSupportFlags() : Set<String> {
    return ALL_SCM_FLAGS
        .filter { flag -> (this and flag) == flag }
        .map { flag -> flag.flagToString() }
        .toSet()
}