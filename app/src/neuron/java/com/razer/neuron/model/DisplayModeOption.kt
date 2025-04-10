package com.razer.neuron.model

import androidx.annotation.StringDef

enum class DisplayModeOption(
    /**
     * A unique serialized name that will be saved to shared preferences
     * Must be consistent with Neuron app
     */
    val displayModeName: String,
    /**
     * True if this display mode uses a virtual display
     */
    val isUsesVirtualDisplay : Boolean) {
    DuplicateDisplay("PcDisplay", isUsesVirtualDisplay = false),
    SeparateDisplay("SeparateDisplay", isUsesVirtualDisplay = true),
    PhoneOnlyDisplay("PhoneOnlyDisplay", isUsesVirtualDisplay = true);

    companion object {
        val default by lazy {
            // BAA-2345
            PhoneOnlyDisplay
        }

        val safest = DisplayModeOption.DuplicateDisplay

        fun findByDisplayModeName(@DisplayModeName displayModeName: String): DisplayModeOption? {
            return DisplayModeOption.entries.firstOrNull { it.displayModeName == displayModeName }
                ?: if (displayModeName == VIRTUAL_DISPLAY) PhoneOnlyDisplay else null
        }
    }
}





private const val PC_DISPLAY = "PcDisplay"
@Deprecated("Use SEPARATE_DISPLAY")
private const val VIRTUAL_DISPLAY = "VirtualDisplay"
private const val SEPARATE_DISPLAY = "SeparateDisplay"
private const val PHONE_ONLY_DISPLAY = "PhoneOnlyDisplay"

@Retention(AnnotationRetention.SOURCE)
@StringDef(
    PC_DISPLAY,
    PHONE_ONLY_DISPLAY,
    SEPARATE_DISPLAY)
annotation class DisplayModeName
