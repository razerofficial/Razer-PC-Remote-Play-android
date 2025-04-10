package com.limelight.nvstream.http

import android.util.Size
import com.google.gson.annotations.SerializedName
import com.limelight.LimeLog
import com.limelight.chunk
import com.limelight.utils.defaultJson

data class DisplayMode(
    @SerializedName("Width") val width: String?,
    @SerializedName("Height") val height: String?,
    @SerializedName("RefreshRate") val refreshRate: String?,
    @SerializedName("IsActive") val isActiveRaw: String?
) {
    val refreshRateInt get() = refreshRate?.toIntOrNull()
    val heightInt get() = height?.toIntOrNull()
    val widthInt get() = width?.toIntOrNull()
    val isActive get() = isActiveRaw == "1"

    val isValid get() = heightInt != null && widthInt != null

    fun toJsonString() = runCatching { defaultJson.toJson(this, DisplayMode::class.java) }.getOrNull()

    fun format() : String? {
        val w = widthInt
        val h = heightInt
        val rr = refreshRateInt
        return if(w != null && h != null && rr != null) "${w}x${h}x${rr}" else null
    }

    companion object {

        fun createDisplayMode(width: Int, height : Int, refreshRate: Int, isActive : Boolean? = null): DisplayMode {
            return DisplayMode(
                width.toString(),
                height.toString(),
                refreshRate.toString(),
                if(isActive != null) (if(isActive) "1" else "0") else null
            )
        }

        fun createDisplayMode(formatString: String): DisplayMode? {
            val resolution = formatString.toResolution()
            val refreshRate = formatString.getFps()
            return if (resolution != null && refreshRate != null) createDisplayMode(
                resolution.width,
                resolution.height,
                refreshRate
            ) else null
        }

        /**
         * NEUR-103
         */
        private val defaultDisplayMode =
            DisplayMode(width = "1920", height = "1080", refreshRate = "60", isActiveRaw = "1")

        /**
         * Create a [DisplayMode] instance from [json]
         */
        fun fromJson(json : String) = runCatching { defaultJson.fromJson(json, DisplayMode::class.java) }.getOrNull()

        private fun parseDisplayMode(displayModeXml : String) : DisplayMode {
            return DisplayMode(
                width = displayModeXml.getXmlSubstring(0, "Width")?.first,
                height = displayModeXml.getXmlSubstring(0, "Height")?.first,
                refreshRate = displayModeXml.getXmlSubstring(0, "RefreshRate")?.first,
                isActiveRaw = displayModeXml.getXmlSubstring(0, "IsActive")?.first)
        }

        /**
         * Get the first [DisplayMode] from [List] of [DisplayMode] where [DisplayMode.isActive] and
         * [DisplayMode.isValid]
         */
        @JvmStatic
        fun getFirstActiveDisplayMode(serverInfoXml : String) : DisplayMode {
            return getPrimaryDisplayMode(serverInfoXml) ?: getSupportedDisplayModes(serverInfoXml).firstOrNull { it.isValid && it.isActive } ?: defaultDisplayMode
        }

        @JvmStatic
        fun getPrimaryDisplayMode(serverInfoXml : String) : DisplayMode? {
            var primaryDisplayMode : DisplayMode? = null
            serverInfoXml.getXmlSubstring(0, "PrimaryDisplayMode")?.let { (primaryDisplayModeXml, _) ->
                primaryDisplayMode = primaryDisplayModeXml.getXmlSubstring(0, "DisplayMode")?.let { (displayModeXml, _) -> parseDisplayMode(displayModeXml).copy(isActiveRaw = "1") }
            }
            return primaryDisplayMode
        }

        /**
         * Get [List] of [DisplayMode] from [serverInfoXml] (response from serverInfo API)
         *
         * @param serverInfoXml xml response from serverInf API
         */
        @JvmStatic
        fun getSupportedDisplayModes(serverInfoXml : String) : List<DisplayMode> {
            val result = mutableListOf<DisplayMode>()
            val (xml, _) = serverInfoXml.getXmlSubstring(0, "SupportedDisplayMode") ?: return emptyList()
            var offset = 0
            while(true) {
                val (displayModeXml, displayModeEndAt) = xml.getXmlSubstring(offset, "DisplayMode") ?: break
                result += parseDisplayMode(displayModeXml)
                offset = displayModeEndAt
            }
            return result
        }

        private fun String.getXmlSubstring(offset : Int, tagName : String, includeTag : Boolean = false) : Pair<String, Int>? {
            val startTag = "<$tagName>"
            val endTag = "</$tagName>"
            val startPos = indexOf(startTag, offset)
            if(startPos == -1) return null
            val endPos = indexOf(endTag, startPos)
            if(endPos == -1) return null
            val newOffset = endPos + endTag.length
            return (if(includeTag) {
                substring(startPos, endPos + endTag.length)
            } else {
                substring(startPos + startTag.length, endPos)
            }) to newOffset
        }
    }
}

fun Size.toResolutionString() = "${width}x${height}"
fun String.toResolution() = split("x").mapNotNull { it.toIntOrNull() }
    .takeIf { length > 1 }
    ?.let {
        val w = it.getOrNull(0) ?: 0
        val h = it.getOrNull(1) ?: 0
        if(w > 0 && h > 0) Size(w,h) else null
    }

fun String.getFps() = split("x")
    .mapNotNull { it.toIntOrNull() }
    .takeIf { length > 2 }
    ?.getOrNull(2)
