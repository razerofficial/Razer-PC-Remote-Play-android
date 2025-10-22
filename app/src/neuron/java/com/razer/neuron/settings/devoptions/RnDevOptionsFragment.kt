package com.razer.neuron.settings.devoptions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.limelight.R
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.databinding.RnFragmentDevOptionsBinding
import com.razer.neuron.common.CustomerSupportHelper
import com.razer.neuron.common.debugToast
import com.razer.neuron.common.doOnError
import com.razer.neuron.common.toast
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.extensions.defaultJsonPrettyPrint
import com.razer.neuron.extensions.dpToPx
import com.razer.neuron.extensions.getNativeResolutions
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.prefConfig
import com.razer.neuron.extensions.setEnabledWithAlpha
import com.razer.neuron.extensions.visible
import com.razer.neuron.game.RnGameError
import com.razer.neuron.game.helpers.transformToStreamError
import com.razer.neuron.model.SessionStats
import com.razer.neuron.model.VideoFormatMetaData
import com.razer.neuron.nexus.NexusContentProvider
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.settings.SettingsStackFragment
import com.razer.neuron.utils.now
import com.razer.neuron.utils.toDateTimeString
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * BAA-2249
 *
 * A page for developer options
 *
 * No translation needed
 */
@AndroidEntryPoint
class RnDevOptionsFragment : Fragment(), DevOptionsHelper, SettingsStackFragment {

    override val fragmentTitle get() = getString(R.string.settings_dev_options)

    private lateinit var binding: RnFragmentDevOptionsBinding

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RnFragmentDevOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun setupToggleButtons() {
        fun MaterialButton.updateIcon() {
            if(isSelected || isChecked) {
                setIconResource(R.drawable.ic_tick_22dp_padding)
            } else {
                icon = null
            }
        }
        val allToggleButtons = binding.toggleButtons.children.filterIsInstance(MaterialButton::class.java).toList()
        if(allToggleButtons.isEmpty()) {
            debugToast("need button in toggleButtons")
            return
        }
        allToggleButtons.forEach {
            it.updateIcon()
            it.addOnCheckedChangeListener { button, _ ->
                button.updateIcon()
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToggleButtons()
        setupTestColors()
        setupTestActions()
        binding.tvDevInfo.text = StringBuilder().apply {
            val activity = activity ?: return@apply
            var prefConfig = activity.prefConfig()
            append("User selected video format: ${prefConfig.videoFormat}\n")
            append("${"-".repeat(30)}\r\n")

            val videoFormatMetaData = VideoFormatMetaData.create(activity, prefConfig = prefConfig)
            append("${defaultJsonPrettyPrint.toJson(videoFormatMetaData)}\n")

            append("${"-".repeat(30)}\r\n")
            getSupportedVideoDecoders().forEach { codecInfo ->
                val videoSupportedTypeString = codecInfo.supportedTypes.filter { it.startsWith("video/") }.joinToString()
                val support = when {
                    MediaCodecDecoderRenderer.isSoftwareOnly(codecInfo) -> "SW"
                    MediaCodecDecoderRenderer.isHardwareAccelerated(codecInfo) -> "HW"
                    else -> "?"
                }
                append("${codecInfo.name} ($support, $videoSupportedTypeString)\r\n")
            }


            val allNativeResolutions = getNativeResolutions(activity.window, false)
            val currentNativeRes = getNativeResolutions(activity.window, true).firstOrNull()
            append("${"-".repeat(30)}\r\n")
            allNativeResolutions.forEach {
                nativeResolution ->
                append("${nativeResolution.width}x${nativeResolution.height} ${if(nativeResolution == currentNativeRes) "(current)" else ""}\r\n")
            }
        }
    }

    private fun setupTestColors() {
        if(!DynamicColors.isDynamicColorAvailable()) {
            return
        }
        val parent = binding.llColorContainer
        val itemsPerRow = (allMaterialYouColors.size/parent.childCount) + 1
        fun getColumn() = parent.children.filterIsInstance<ViewGroup>().toList().run { firstOrNull { it.childCount < itemsPerRow } ?: last() }
        fun addColor(name : String, color : Int) {
            getColumn().addView(TextView(requireContext()).apply {
                text = name
                val useBlackText = color > Color.parseColor("#FF888888")
                setTextColor(if(useBlackText) Color.BLACK else Color.WHITE)
                setBackgroundColor(color)
                textSize = 8f
                setLines(2)
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dpToPx(12f).roundToInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }

        runCatching {
            allMaterialYouColors.forEach {
                    (name, color) ->
                addColor(name, requireContext().systemResToColor(color))
                return@forEach
            }
        }.doOnError {
            addColor(it.message ?: "error", Color.BLACK)
        }
    }
    private fun setupTestActions(){
        with(binding.btnTestAction1) {
            visible()
            text = "Show error 5040"
            setOnClickListener {
                activity?.let {
                    activity ->
                    activity.startActivity(RnGameError.createHandleStreamErrorIntent(
                        context = activity,
                        streamError = resources.transformToStreamError("testing", 0, errorCode = 5040),
                        gameIntent = activity.intent))
                }
            }
        }

    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            withContext(ioDispatcher) { NexusContentProvider.sync(requireContext()) }
            initExportCurrentSettings()
            reinitExportLastSessionStats()
            initSeparateScreenDisplay()
            initAppLogcats()
            val nexusVersionCode = kotlin.runCatching { NexusContentProvider.getBuildVersionCode(requireContext()) }.getOrNull()
            Timber.v("Nexus version code is $nexusVersionCode")
        }
    }

    @SuppressLint
    private fun reinitExportLastSessionStats() {
        with(binding.rowExportLastSessionStats) {
            val lastSession = RemotePlaySettingsPref.savedLastSessionStats
            root.setEnabledWithAlpha(RemotePlaySettingsPref.isPerfOverlayEnabled)
            tvTitle.text = "Export last session streaming stats"
            tvSubtitle.text =
                if (!root.isEnabled) {
                    "Disabled. Please re-enable \'Show performance stats while streaming\'"
                } else {
                    if (lastSession != null) {
                        "${Date(lastSession.startedAt)} (Lasted for ${(lastSession.elapsedTimeMs ?: 0L).milliseconds.inWholeSeconds}s)\n" +
                                "randomId: ${lastSession.randomId}"
                    } else {
                        "No stats yet"
                    }
                }
            ivActionStartIcon.gone()
            switchLayout.gone()

            root.setOnClickListener {
                if (!root.isEnabled) return@setOnClickListener
                if (lastSession != null) {
                    launchWithLoading {
                        export(requireActivity(), lastSession)
                    }
                } else {
                    toast("Start a streaming session first")
                }
            }
        }
    }

    private fun initExportCurrentSettings() {
        with(binding.rowExportCurrentSettings) {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
            tvTitle.text = "Export all settings"
            ivActionStartIcon.gone()
            switchLayout.gone()
            tvSubtitle.text = "${sharedPref.all.keys.size} entries"
            root.setOnClickListener {
                launchWithLoading {
                    export(
                        requireActivity(),
                        sharedPref
                    )
                }
            }
        }
    }

    private fun initSeparateScreenDisplay() {
        with(binding.rowSeparateScreenDisplay) {
            val isSeparateScreenDisplayEnabled =
                RemotePlaySettingsPref.isSeparateScreenDisplayEnabled
            tvTitle.text = "Enable separate screen display"
            ivActionStartIcon.gone()
            tvSubtitle.gone()
            switchLayout.visible()
            switchAction.isChecked = isSeparateScreenDisplayEnabled
            root.setOnClickListener {
                switchAction.toggle()
                RemotePlaySettingsPref.isSeparateScreenDisplayEnabled =
                    !isSeparateScreenDisplayEnabled
            }
            switchAction.setOnCheckedChangeListener { _, isEnabled ->
                RemotePlaySettingsPref.isSeparateScreenDisplayEnabled = isEnabled
            }
        }
    }

    private fun initAppLogcats() {
        with(binding.rowExportCustomerSupportLogs) {
            tvTitle.text = "Export ${getString(R.string.applabel)} logs"
            ivActionStartIcon.gone()
            tvSubtitle.gone()
            switchLayout.gone()
            root.setOnClickListener {
                CustomerSupportHelper.getLogs()?.let {
                    launchWithLoading {
                        export(requireActivity(), it)
                    }
                }
            }
        }
    }

    private fun launchWithLoading(task: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                binding.loadingLayoutContainer.visible()
                task()
            } catch (t: Throwable) {
                Timber.w(t)
                toast(t.message)
            } finally {
                binding.loadingLayoutContainer.gone()
            }
        }
    }
}

interface DevOptionsHelper {
    /**
     * BAA-2249
     */
    suspend fun export(activity: Activity, sessionStats: SessionStats) {
        val last4Chars = sessionStats.randomId.takeLast(4)
        val fileName =
            "session_stats_${last4Chars}_${sessionStats.startedAt.toDateTimeString("yyyyMMdd_HHmmss")}.txt"
        shareFile(activity, fileName = fileName, intentTitle = "Session stats $last4Chars") {
            it.write(
                requireNotNull(sessionStats.toJson(true)) { "Cannot serialize SessionStats ${sessionStats.randomId}" }.toByteArray()
            )
        }
    }

    /**
     * BAA-2249
     */
    suspend fun export(activity: Activity, sharedPreferences: SharedPreferences) {
        val fileName =
            "neuron_settings_${now().toDateTimeString("yyyyMMdd_HHmmss")}.txt"

        shareFile(activity, fileName = fileName, intentTitle = "Neuron settings") {
            val jsonObj = JSONObject().apply {
                sharedPreferences.all.entries.forEach { (k, v) ->
                    when (k) {
                        RemotePlaySettingsPref.LAST_SESSION_STATS_JSON -> Unit // ignore key
                        else -> put(k, v)
                    }
                }
            }
            it.write(
                requireNotNull(jsonObj.toString(3)) { "Cannot serialize settings" }.toByteArray()
            )
        }
    }

    /**
     * NEUR-170
     */
    suspend fun export(activity: Activity, logs: String) {
        val fileName =
            "neuron_customer_support_logs_${now().toDateTimeString("yyyyMMdd_HHmmss")}.txt"

        shareFile(activity, fileName = fileName, intentTitle = "Neuron settings") {
            it.write(
                requireNotNull(logs) { "Cannot serialize settings" }.toByteArray()
            )
        }
    }

    private suspend fun shareFile(
        activity: Activity,
        /**
         * Must match [com.limelight.R.xml.filepaths]
         */
        folderName: String = "neuron_dev",
        mimeType: String = "text/plain",
        fileName: String,
        intentTitle: String,
        write: (FileOutputStream) -> Unit
    ) {
        /**
         * Must match [com.limelight.R.xml.filepaths]
         */
        val folder =
            File(activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        val uri = FileProvider.getUriForFile(
            activity,
            activity.getString(R.string.neuron_dev_authority),
            File(folder, fileName).apply {
                withContext(Dispatchers.IO) {
                    FileOutputStream(this@apply).use {
                        write(it)
                    }
                }
            }
        )
        val shareIntent = ShareCompat.IntentBuilder.from(activity)
            .setType(mimeType) // Specify the MIME type for text files
            .setStream(uri)
            .setChooserTitle(intentTitle)
            .createChooserIntent()
        activity.startActivity(shareIntent)
    }

    fun getSupportedVideoDecoders(): List<MediaCodecInfo> {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos
            .filter { codecInfo ->
                !codecInfo.isEncoder && codecInfo.supportedTypes.any { it.startsWith("video/") }
            }
    }
}

fun Context.systemResToColor(@ColorRes systemColor : Int): Int {
    return ContextCompat.getColor(this, systemColor)
}

val allMaterialYouColors = listOf(
    "system_accent1_0" to android.R.color.system_accent1_0,
    "system_accent1_10" to android.R.color.system_accent1_10,
    "system_accent1_100" to android.R.color.system_accent1_100,
    "system_accent1_1000" to android.R.color.system_accent1_1000,
    "system_accent1_200" to android.R.color.system_accent1_200,
    "system_accent1_300" to android.R.color.system_accent1_300,
    "system_accent1_400" to android.R.color.system_accent1_400,
    "system_accent1_50" to android.R.color.system_accent1_50,
    "system_accent1_500" to android.R.color.system_accent1_500,
    "system_accent1_600" to android.R.color.system_accent1_600,
    "system_accent1_700" to android.R.color.system_accent1_700,
    "system_accent1_800" to android.R.color.system_accent1_800,
    "system_accent1_900" to android.R.color.system_accent1_900,
    "system_accent2_0" to android.R.color.system_accent2_0,
    "system_accent2_10" to android.R.color.system_accent2_10,
    "system_accent2_100" to android.R.color.system_accent2_100,
    "system_accent2_1000" to android.R.color.system_accent2_1000,
    "system_accent2_200" to android.R.color.system_accent2_200,
    "system_accent2_300" to android.R.color.system_accent2_300,
    "system_accent2_400" to android.R.color.system_accent2_400,
    "system_accent2_50" to android.R.color.system_accent2_50,
    "system_accent2_500" to android.R.color.system_accent2_500,
    "system_accent2_600" to android.R.color.system_accent2_600,
    "system_accent2_700" to android.R.color.system_accent2_700,
    "system_accent2_800" to android.R.color.system_accent2_800,
    "system_accent2_900" to android.R.color.system_accent2_900,
    "system_accent3_0" to android.R.color.system_accent3_0,
    "system_accent3_10" to android.R.color.system_accent3_10,
    "system_accent3_100" to android.R.color.system_accent3_100,
    "system_accent3_1000" to android.R.color.system_accent3_1000,
    "system_accent3_200" to android.R.color.system_accent3_200,
    "system_accent3_300" to android.R.color.system_accent3_300,
    "system_accent3_400" to android.R.color.system_accent3_400,
    "system_accent3_50" to android.R.color.system_accent3_50,
    "system_accent3_500" to android.R.color.system_accent3_500,
    "system_accent3_600" to android.R.color.system_accent3_600,
    "system_accent3_700" to android.R.color.system_accent3_700,
    "system_accent3_800" to android.R.color.system_accent3_800,
    "system_accent3_900" to android.R.color.system_accent3_900,
    "system_background_dark" to android.R.color.system_background_dark,
    "system_background_light" to android.R.color.system_background_light,
    "system_control_activated_dark" to android.R.color.system_control_activated_dark,
    "system_control_activated_light" to android.R.color.system_control_activated_light,
    "system_control_highlight_dark" to android.R.color.system_control_highlight_dark,
    "system_control_highlight_light" to android.R.color.system_control_highlight_light,
    "system_control_normal_dark" to android.R.color.system_control_normal_dark,
    "system_control_normal_light" to android.R.color.system_control_normal_light,
    "system_error_0" to android.R.color.system_error_0,
    "system_error_10" to android.R.color.system_error_10,
    "system_error_100" to android.R.color.system_error_100,
    "system_error_1000" to android.R.color.system_error_1000,
    "system_error_200" to android.R.color.system_error_200,
    "system_error_300" to android.R.color.system_error_300,
    "system_error_400" to android.R.color.system_error_400,
    "system_error_50" to android.R.color.system_error_50,
    "system_error_500" to android.R.color.system_error_500,
    "system_error_600" to android.R.color.system_error_600,
    "system_error_700" to android.R.color.system_error_700,
    "system_error_800" to android.R.color.system_error_800,
    "system_error_900" to android.R.color.system_error_900,
    "system_error_container_dark" to android.R.color.system_error_container_dark,
    "system_error_container_light" to android.R.color.system_error_container_light,
    "system_error_dark" to android.R.color.system_error_dark,
    "system_error_light" to android.R.color.system_error_light,
    "system_neutral1_0" to android.R.color.system_neutral1_0,
    "system_neutral1_10" to android.R.color.system_neutral1_10,
    "system_neutral1_100" to android.R.color.system_neutral1_100,
    "system_neutral1_1000" to android.R.color.system_neutral1_1000,
    "system_neutral1_200" to android.R.color.system_neutral1_200,
    "system_neutral1_300" to android.R.color.system_neutral1_300,
    "system_neutral1_400" to android.R.color.system_neutral1_400,
    "system_neutral1_50" to android.R.color.system_neutral1_50,
    "system_neutral1_500" to android.R.color.system_neutral1_500,
    "system_neutral1_600" to android.R.color.system_neutral1_600,
    "system_neutral1_700" to android.R.color.system_neutral1_700,
    "system_neutral1_800" to android.R.color.system_neutral1_800,
    "system_neutral1_900" to android.R.color.system_neutral1_900,
    "system_neutral2_0" to android.R.color.system_neutral2_0,
    "system_neutral2_10" to android.R.color.system_neutral2_10,
    "system_neutral2_100" to android.R.color.system_neutral2_100,
    "system_neutral2_1000" to android.R.color.system_neutral2_1000,
    "system_neutral2_200" to android.R.color.system_neutral2_200,
    "system_neutral2_300" to android.R.color.system_neutral2_300,
    "system_neutral2_400" to android.R.color.system_neutral2_400,
    "system_neutral2_50" to android.R.color.system_neutral2_50,
    "system_neutral2_500" to android.R.color.system_neutral2_500,
    "system_neutral2_600" to android.R.color.system_neutral2_600,
    "system_neutral2_700" to android.R.color.system_neutral2_700,
    "system_neutral2_800" to android.R.color.system_neutral2_800,
    "system_neutral2_900" to android.R.color.system_neutral2_900,
    "system_on_background_dark" to android.R.color.system_on_background_dark,
    "system_on_background_light" to android.R.color.system_on_background_light,
    "system_on_error_container_dark" to android.R.color.system_on_error_container_dark,
    "system_on_error_container_light" to android.R.color.system_on_error_container_light,
    "system_on_error_dark" to android.R.color.system_on_error_dark,
    "system_on_error_light" to android.R.color.system_on_error_light,
    "system_on_primary_container_dark" to android.R.color.system_on_primary_container_dark,
    "system_on_primary_container_light" to android.R.color.system_on_primary_container_light,
    "system_on_primary_dark" to android.R.color.system_on_primary_dark,
    "system_on_primary_fixed_variant" to android.R.color.system_on_primary_fixed_variant,
    "system_on_primary_fixed" to android.R.color.system_on_primary_fixed,
    "system_on_primary_light" to android.R.color.system_on_primary_light,
    "system_on_secondary_container_dark" to android.R.color.system_on_secondary_container_dark,
    "system_on_secondary_container_light" to android.R.color.system_on_secondary_container_light,
    "system_on_secondary_dark" to android.R.color.system_on_secondary_dark,
    "system_on_secondary_fixed_variant" to android.R.color.system_on_secondary_fixed_variant,
    "system_on_secondary_fixed" to android.R.color.system_on_secondary_fixed,
    "system_on_secondary_light" to android.R.color.system_on_secondary_light,
    "system_on_surface_dark" to android.R.color.system_on_surface_dark,
    "system_on_surface_light" to android.R.color.system_on_surface_light,
    "system_on_surface_variant_dark" to android.R.color.system_on_surface_variant_dark,
    "system_on_surface_variant_light" to android.R.color.system_on_surface_variant_light,
    "system_on_tertiary_container_dark" to android.R.color.system_on_tertiary_container_dark,
    "system_on_tertiary_container_light" to android.R.color.system_on_tertiary_container_light,
    "system_on_tertiary_dark" to android.R.color.system_on_tertiary_dark,
    "system_on_tertiary_fixed_variant" to android.R.color.system_on_tertiary_fixed_variant,
    "system_on_tertiary_fixed" to android.R.color.system_on_tertiary_fixed,
    "system_on_tertiary_light" to android.R.color.system_on_tertiary_light,
    "system_outline_dark" to android.R.color.system_outline_dark,
    "system_outline_light" to android.R.color.system_outline_light,
    "system_outline_variant_dark" to android.R.color.system_outline_variant_dark,
    "system_outline_variant_light" to android.R.color.system_outline_variant_light,
    "system_palette_key_color_neutral_dark" to android.R.color.system_palette_key_color_neutral_dark,
    "system_palette_key_color_neutral_light" to android.R.color.system_palette_key_color_neutral_light,
    "system_palette_key_color_neutral_variant_dark" to android.R.color.system_palette_key_color_neutral_variant_dark,
    "system_palette_key_color_neutral_variant_light" to android.R.color.system_palette_key_color_neutral_variant_light,
    "system_palette_key_color_primary_dark" to android.R.color.system_palette_key_color_primary_dark,
    "system_palette_key_color_primary_light" to android.R.color.system_palette_key_color_primary_light,
    "system_palette_key_color_secondary_dark" to android.R.color.system_palette_key_color_secondary_dark,
    "system_palette_key_color_secondary_light" to android.R.color.system_palette_key_color_secondary_light,
    "system_palette_key_color_tertiary_dark" to android.R.color.system_palette_key_color_tertiary_dark,
    "system_palette_key_color_tertiary_light" to android.R.color.system_palette_key_color_tertiary_light,
    "system_primary_container_dark" to android.R.color.system_primary_container_dark,
    "system_primary_container_light" to android.R.color.system_primary_container_light,
    "system_primary_dark" to android.R.color.system_primary_dark,
    "system_primary_fixed_dim" to android.R.color.system_primary_fixed_dim,
    "system_primary_fixed" to android.R.color.system_primary_fixed,
    "system_primary_light" to android.R.color.system_primary_light,
    "system_secondary_container_dark" to android.R.color.system_secondary_container_dark,
    "system_secondary_container_light" to android.R.color.system_secondary_container_light,
    "system_secondary_dark" to android.R.color.system_secondary_dark,
    "system_secondary_fixed_dim" to android.R.color.system_secondary_fixed_dim,
    "system_secondary_fixed" to android.R.color.system_secondary_fixed,
    "system_secondary_light" to android.R.color.system_secondary_light,
    "system_surface_bright_dark" to android.R.color.system_surface_bright_dark,
    "system_surface_bright_light" to android.R.color.system_surface_bright_light,
    "system_surface_container_dark" to android.R.color.system_surface_container_dark,
    "system_surface_container_high_dark" to android.R.color.system_surface_container_high_dark,
    "system_surface_container_high_light" to android.R.color.system_surface_container_high_light,
    "system_surface_container_highest_dark" to android.R.color.system_surface_container_highest_dark,
    "system_surface_container_highest_light" to android.R.color.system_surface_container_highest_light,
    "system_surface_container_light" to android.R.color.system_surface_container_light,
    "system_surface_container_low_dark" to android.R.color.system_surface_container_low_dark,
    "system_surface_container_low_light" to android.R.color.system_surface_container_low_light,
    "system_surface_container_lowest_dark" to android.R.color.system_surface_container_lowest_dark,
    "system_surface_container_lowest_light" to android.R.color.system_surface_container_lowest_light,
    "system_surface_dark" to android.R.color.system_surface_dark,
    "system_surface_dim_dark" to android.R.color.system_surface_dim_dark,
    "system_surface_dim_light" to android.R.color.system_surface_dim_light,
    "system_surface_light" to android.R.color.system_surface_light,
    "system_surface_variant_dark" to android.R.color.system_surface_variant_dark,
    "system_surface_variant_light" to android.R.color.system_surface_variant_light,
    "system_tertiary_container_dark" to android.R.color.system_tertiary_container_dark,
    "system_tertiary_container_light" to android.R.color.system_tertiary_container_light,
    "system_tertiary_dark" to android.R.color.system_tertiary_dark,
    "system_tertiary_fixed_dim" to android.R.color.system_tertiary_fixed_dim,
    "system_tertiary_fixed" to android.R.color.system_tertiary_fixed,
    "system_tertiary_light" to android.R.color.system_tertiary_light,
    "system_text_hint_inverse_dark" to android.R.color.system_text_hint_inverse_dark,
    "system_text_hint_inverse_light" to android.R.color.system_text_hint_inverse_light,
    "system_text_primary_inverse_dark" to android.R.color.system_text_primary_inverse_dark,
    "system_text_primary_inverse_disable_only_dark" to android.R.color.system_text_primary_inverse_disable_only_dark,
    "system_text_primary_inverse_disable_only_light" to android.R.color.system_text_primary_inverse_disable_only_light,
    "system_text_primary_inverse_light" to android.R.color.system_text_primary_inverse_light,
    "system_text_secondary_and_tertiary_inverse_dark" to android.R.color.system_text_secondary_and_tertiary_inverse_dark,
    "system_text_secondary_and_tertiary_inverse_disabled_dark" to android.R.color.system_text_secondary_and_tertiary_inverse_disabled_dark,
    "system_text_secondary_and_tertiary_inverse_disabled_light" to android.R.color.system_text_secondary_and_tertiary_inverse_disabled_light,
    "system_text_secondary_and_tertiary_inverse_light" to android.R.color.system_text_secondary_and_tertiary_inverse_light,
)