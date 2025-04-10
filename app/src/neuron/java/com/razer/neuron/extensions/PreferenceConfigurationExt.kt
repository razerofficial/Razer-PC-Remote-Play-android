package com.razer.neuron.extensions

import androidx.annotation.StringRes
import com.limelight.R
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.PreferenceConfiguration.FormatOption

/**
 * Extends from [FormatOption]
 */
enum class FormatOptionMeta(
    @StringRes val displayText: Int,
    val prefValue: String,
    val formatOption: FormatOption
) {
    AUTO(R.string.rn_video_format_auto, "auto", FormatOption.AUTO),
    AV1(R.string.rn_video_format_av1, "forceav1", FormatOption.FORCE_AV1),
    HEVC(R.string.rn_video_format_h265_hevc, "forceh265", FormatOption.FORCE_HEVC),
    H264(R.string.rn_video_format_h264_avc, "neverh265", FormatOption.FORCE_H264);

    companion object {
        val default = AUTO
        val displayOrder = setOf(AUTO, AV1, HEVC, H264)
    }
}

fun FormatOption.getMeta(): FormatOptionMeta? {
    return FormatOptionMeta.values().firstOrNull { it.formatOption == this@getMeta }
}
