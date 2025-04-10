package com.razer.neuron.extensions

import com.limelight.nvstream.StreamConfiguration


const val VIDEO_FORMAT_H264 = 0x0001
const val VIDEO_FORMAT_H265 = 0x0100
const val VIDEO_FORMAT_H265_MAIN10 = 0x0200
const val VIDEO_FORMAT_AV1_MAIN8 = 0x1000
const val VIDEO_FORMAT_AV1_MAIN10 = 0x2000

const val VIDEO_FORMAT_MASK_H264 = 0x000F
const val VIDEO_FORMAT_MASK_H265 = 0x0F00
const val VIDEO_FORMAT_MASK_AV1 = 0xF000
const val VIDEO_FORMAT_MASK_10BIT = 0x2200


private val ALL_VIDEO_FORMATS = listOf(
    VIDEO_FORMAT_H264,
    VIDEO_FORMAT_MASK_H264,
    VIDEO_FORMAT_H265,
    VIDEO_FORMAT_H265_MAIN10,
    VIDEO_FORMAT_MASK_H265,
    VIDEO_FORMAT_AV1_MAIN8,
    VIDEO_FORMAT_AV1_MAIN10,
    VIDEO_FORMAT_MASK_AV1
)

private fun Int.flagToString() = when(this) {
    VIDEO_FORMAT_MASK_H264,
    VIDEO_FORMAT_H264 -> "H264"
    VIDEO_FORMAT_MASK_H265, VIDEO_FORMAT_H265 -> "HEVC"
    VIDEO_FORMAT_H265_MAIN10 -> "HEVC_MAIN10"
    VIDEO_FORMAT_MASK_AV1, VIDEO_FORMAT_AV1_MAIN8 -> "AV1_MAIN8"
    VIDEO_FORMAT_AV1_MAIN10 -> "AV1_MAIN10"
    else -> "UNKNOWN_$this"
}

/**
 * @return a string representation of what server codec(s) are supported
 */
fun Int.parseSupportedVideoFormat() : Set<String> {
    return ALL_VIDEO_FORMATS
        .filter { flag -> (this and flag) == flag }
        .map { flag -> flag.flagToString() }
        .toSet()
}


fun StreamConfiguration.getSupportedVideoFormatStrings() = supportedVideoFormats.parseSupportedVideoFormat()