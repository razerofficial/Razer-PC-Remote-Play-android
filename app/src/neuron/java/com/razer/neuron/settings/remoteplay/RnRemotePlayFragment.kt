package com.razer.neuron.settings.remoteplay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import com.limelight.R
import com.limelight.databinding.RnFragmentRemotePlayBinding
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import com.razer.neuron.common.debugToast
import com.razer.neuron.common.showSingleChoiceItemsDialog
import com.razer.neuron.common.updateCorners
import com.razer.neuron.di.IoDispatcher
import com.razer.neuron.extensions.Disposable
import com.razer.neuron.extensions.FormatOptionMeta
import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.dpToPx
import com.razer.neuron.extensions.getDisplayCutout
import com.razer.neuron.extensions.getMeta
import com.razer.neuron.extensions.getNativeResolutions
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.isCurrentNativeResolutionAlreadyMax
import com.razer.neuron.extensions.isHdrSupported
import com.razer.neuron.extensions.setupSingleChoiceItemsDropdown
import com.razer.neuron.extensions.setupSwitch
import com.razer.neuron.extensions.visible
import com.razer.neuron.extensions.visibleIf
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.model.FramePacingOption
import com.razer.neuron.model.TouchScreenOption
import com.razer.neuron.model.VideoFormatMetaData
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.settings.SettingsStackFragment
import com.razer.neuron.utils.calculateVirtualDisplayMode
import com.razer.neuron.utils.getDefaultDisplayRefreshRateHz
import com.razer.neuron.utils.getStringExt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt


@AndroidEntryPoint
class RnRemotePlayFragment : Fragment(), SettingsStackFragment {

    override val fragmentTitle get() = getString(R.string.rn_settings_streaming_options)

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private lateinit var binding: RnFragmentRemotePlayBinding

    private val viewModel: RnRemotePlayViewModel by activityViewModels()

    private val imageRadioButtonsMap
        get() = mapOf(
            DisplayModeOption.DuplicateDisplay to binding.irbDuplicateScreen,
            DisplayModeOption.SeparateDisplay to binding.irbSeparateScreen,
            DisplayModeOption.PhoneOnlyDisplay to binding.irbPhoneOnly
        )

    private val onDisplayModeChangedListener =
        CompoundButton.OnCheckedChangeListener { button, isChecked ->
            Timber.v("OnCheckedChangeListener: $button, isChecked=${isChecked}")
            if (isChecked) {
                val checkedDisplayOption =
                    imageRadioButtonsMap.entries.firstOrNull { (_, radioButton) -> radioButton.radioBtn == button }?.key
                Timber.v("OnCheckedChangeListener: ${checkedDisplayOption?.displayModeName}, isChecked=${isChecked}")
                if (checkedDisplayOption != null) {
                    viewModel.setDisplayMode(checkedDisplayOption)
                }
            }
        }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RnFragmentRemotePlayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDisplayMode()
        setupCropToSafeArea()
        setupLimitRefreshRate()
        setupOnlyUseCurrentNativeRes()
        setupVideoBitrate()
        setupFramePacing()
        setupHDR()
        setupMuteHostPc()
        setupTouchScreen()
        setupAutoCloseGameCountDown()
        setupVideoFormat()
        updateAllCorners()
    }

    private fun updateAllCorners() = pillGroups.forEach { it.updateCorners() }

    private val videoSettingsPill by lazy {
        listOf(
            binding.rowBitrate.root,
            binding.rowHdr.root,
        )
    }

    private val generalSettingsPill by lazy {
        listOf(
            binding.rowMuteHostPc.root,
            binding.rowTouchScreen.root
        )
    }

    private val advanceSettingsPill by lazy {
        listOf(
            binding.rowAutoCloseGameCountdownDropdown.root,
            binding.rowLimitRefreshRate.root,
            binding.rowOnlyUseCurrentNativeRes.root,
            binding.rowLimitToSafeArea.root,
            binding.rowStretchToFullScreen.root,
            binding.rowFramePacing.root,
            binding.rowVideoFormatDropdown.root,
        )
    }

    private val pillGroups by lazy {
        arrayOf(videoSettingsPill, generalSettingsPill, advanceSettingsPill)
    }


    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }


    override fun onPause() {
        super.onPause()
        touchScreenDisposable?.dispose()
        framePacingDisposable?.dispose()
    }

    private fun DisplayModeOption.getDisplaySubtitleText() : String {
        val virtualDisplayMode = activity?.calculateVirtualDisplayMode(isLimitRefreshRate = RemotePlaySettingsPref.isLimitRefreshRate)?.getOrNull()
        val virtualResolution = virtualDisplayMode?.let { "${it.width}x${it.height}" } ?: ""
        val virtualRefreshRate = virtualDisplayMode?.refreshRate ?: ""
        return when(this) {
            DisplayModeOption.DuplicateDisplay -> getString(R.string.rn_settings_display_mode_duplicate_desc)
            DisplayModeOption.PhoneOnlyDisplay -> getString(R.string.rn_settings_display_mode_phone_only_desc_x_resolution_x_refresh_rate, virtualResolution, virtualRefreshRate)
            DisplayModeOption.SeparateDisplay -> getString(R.string.rn_settings_display_mode_separate_desc_x_resolution_x_refresh_rate, virtualResolution, virtualRefreshRate)
        }
    }

    private fun setupDisplayMode() {
        viewModel.displayModeLiveData.observe(viewLifecycleOwner) { displayMode ->
            binding.irbDuplicateScreen.update(
                title = getStringExt(R.string.rn_settings_display_mode_duplicate),
                subtitle = null,
                iconRes = R.drawable.ic_display_mode_duplicate,
                isChecked = displayMode == DisplayModeOption.DuplicateDisplay
            )
            binding.irbPhoneOnly.update(
                title = getStringExt(R.string.rn_settings_display_mode_phone_only_2_lines),
                subtitle = null,
                iconRes = R.drawable.ic_display_mode_phone_only,
                isChecked = displayMode == DisplayModeOption.PhoneOnlyDisplay
            )
            binding.irbSeparateScreen.update(
                title = getStringExt(R.string.rn_settings_display_mode_separate),
                subtitle = null,
                iconRes = R.drawable.ic_display_mode_separate,
                isChecked = displayMode == DisplayModeOption.SeparateDisplay
            )
            updateHDRSwitch(displayMode)
            updateMuteSpeakersSwitch(displayMode)
            updateCropToSafeArea(displayMode)
            binding.tvDisplayModeDescription.text = displayMode.getDisplaySubtitleText()

        }
        imageRadioButtonsMap.values.forEach { imageRadioButtons ->
            imageRadioButtons.onCheckedChangeListener = onDisplayModeChangedListener
        }
        binding.irbSeparateScreen.visibleIf {
            RemotePlaySettingsPref.isSeparateScreenDisplayEnabled
        }
        updateAllCorners()
    }


    private fun updateCropToSafeArea(selected : DisplayModeOption?) {
        val context = context ?: return
        val window = activity?.window ?: return
        binding.rowLimitToSafeArea.root.visibleIf { context.getDisplayCutout(window).isNotEmpty() && selected == DisplayModeOption.PhoneOnlyDisplay }
        binding.rowStretchToFullScreen.root.visibleIf { context.getDisplayCutout(window).isNotEmpty() && selected == DisplayModeOption.DuplicateDisplay }
        updateAllCorners()
    }

    private fun setupCropToSafeArea() {
        with(binding.rowLimitToSafeArea) {
            setupSwitch(
                viewLifecycleOwner = viewLifecycleOwner,
                title = getString(R.string.rn_setting_neuron_limit_to_safe_area),
                subtitle = getString(R.string.rn_setting_neuron_limit_to_safe_area_desc),
                isChecked = viewModel.cropDisplaySafeAreaLiveData.value,
                liveData = viewModel.cropDisplaySafeAreaLiveData,
                onCheckedChangeListener = { _, isChecked ->
                    viewModel.setCropDisplaySafeArea(isChecked)
                }
            )
        }

        with(binding.rowStretchToFullScreen) {
            // we use ! here because the function setCropDisplaySafeArea is opposite
            // of the switch's text
            setupSwitch(
                viewLifecycleOwner = viewLifecycleOwner,
                title = getString(R.string.rn_setting_neuron_stretch_to_full_screen),
                subtitle = getString(R.string.rn_setting_neuron_stretch_to_full_screen_desc),
                isChecked = (viewModel.cropDisplaySafeAreaLiveData.value != true),
                liveData = viewModel.cropDisplaySafeAreaLiveData.map { !it },
                onCheckedChangeListener = { _, isChecked ->
                    viewModel.setCropDisplaySafeArea(!isChecked)
                }
            )
        }
    }

    private fun setupLimitRefreshRate() {
        //BAA-2345
        binding.rowLimitRefreshRate.root.visibleIf { (context?.getDefaultDisplayRefreshRateHz() ?: 0) > 60f }
        updateAllCorners()
        with(binding.rowLimitRefreshRate) {
            setupSwitch(
                viewLifecycleOwner = viewLifecycleOwner,
                title = getString(R.string.rn_setting_neuron_limit_refresh_title),
                isChecked = viewModel.limitRefreshRateLiveData.value == true,
                liveData = viewModel.limitRefreshRateLiveData,
                onCheckedChangeListener = { _, isChecked ->
                    viewModel.setCropDisplaySafeArea(!isChecked)
                }
            )
        }
    }


    /**
     * BAA-2656
     *
     */
    private fun setupOnlyUseCurrentNativeRes() {
        /**
         * "can we set it so we only show that option if there’s a difference between native resolution and active screen resolution?"
         */
        fun isSettingNecessary() = !(activity?.let { isCurrentNativeResolutionAlreadyMax(window = it.window) } ?: false)
        binding.rowOnlyUseCurrentNativeRes.root.visibleIf { isSettingNecessary() }
        with(binding.rowOnlyUseCurrentNativeRes) {
            fun updateSubtitle() {
                val window = activity?.window ?: return
                val nativeRes = getNativeResolutions(window = window, isOnlyUseCurrentNativeRes = switchAction.isChecked).maxByOrNull { it.pixelsCount } ?: return
                tvSubtitle.text = "${nativeRes.width}x${nativeRes.height}"
            }
            tvTitle.text = getString(R.string.rn_setting_neuron_only_use_current_native_resolution)
            updateSubtitle()
            switchLayout.visible()
            ivActionStartIcon.gone()
            root.setOnClickListener {
                switchAction.toggle()
                viewModel.setOnlyUseCurrentNativeRes(switchAction.isChecked)
            }
            switchAction.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setOnlyUseCurrentNativeRes(isChecked)
                updateSubtitle()
            }
        }
        viewModel.displayModeLiveData.observe(viewLifecycleOwner) { option ->
            /**
             * Since we only use phone fullscreen if [DisplayModeOption.isUsesVirtualDisplay], so
             * we should also check this
             */
            val willUseNativeResolution = option.isUsesVirtualDisplay
            binding.rowOnlyUseCurrentNativeRes.root.visibleIf { willUseNativeResolution && isSettingNecessary() }
            updateAllCorners()
        }
        viewModel.onlyUseCurrentNativeRes.observe(viewLifecycleOwner) { isChecked ->
            with(binding.rowOnlyUseCurrentNativeRes) {
                switchAction.isChecked = isChecked
            }
        }
    }

    /**
     * Requirements
     * 1. 20 level
     * 2. Default (30mbps) must be one of the level
     * 3. Maintain min of 1mbps and max 150mbps
     *
     * Since [BitrateRateSettings.min] is so small, we don't take it into account, so that we get a smooth discrete slider.
     *
     * Instead we will coerce before display and before setting it in [RnRemotePlayViewModel.setBitrateSettings]
     */
    private fun setupVideoBitrate() {
        val levels = 20f
        fun BitrateRateSettings.coerceBitrateToString(bitrate : Int = rate) = "%.1f".format(bitrate.coerceIn(min, max) / 1000f, Locale.ENGLISH) + " Mbps"
        fun BitrateRateSettings.range() = max // Don't use min
        fun BitrateRateSettings.levelToBitrate(level: Float) = range() * (level / levels)
        fun BitrateRateSettings.levelToCoerceString(level: Float) = coerceBitrateToString(levelToBitrate(level).roundToInt())
        fun BitrateRateSettings.bitrateToLevel() = rate.toFloat()/range()*levels
        val bitrateSlider = binding.rowBitrate.slider
        binding.rowBitrate.tvTitle.text = getString(R.string.rn_setting_neuron_video_bitrate)
        var currentSettings : BitrateRateSettings? = null
        viewModel.bitrateSettingsLiveData.observe(viewLifecycleOwner) { bitRateSettings ->
            currentSettings = bitRateSettings
            Timber.v("setupVideoBitrate: bitRateSetting=$bitRateSettings")
            bitrateSlider.contentDescription = bitRateSettings.coerceBitrateToString()
            bitrateSlider.valueFrom = 0f
            bitrateSlider.valueTo = levels
            bitrateSlider.value = bitRateSettings.bitrateToLevel()
            // we cannot use KEY_STEP directly since
            // 1. min value is 500 (which is less than KEY_STEP)
            // 2. Between max and min there are too many steps
            bitrateSlider.setLabelFormatter { level ->
                bitRateSettings.levelToCoerceString(level)
            }
        }
        bitrateSlider.addOnChangeListener { _ ,level, fromUser ->
            if (fromUser) {
                currentSettings?.let {
                    val bitrate = it.levelToBitrate(level).roundToInt()
                    bitrateSlider.contentDescription = it.levelToCoerceString(level)
                    Timber.v("bitrateSlider:addOnChangeListener level=$level rate=${it.coerceBitrateToString(bitrate = bitrate)}")
                    viewModel.setBitrateSettings(bitrate)
                }
            }
        }
        updateAllCorners()
    }

    private fun FramePacingOption.displayNameRes() = when(this) {
        FramePacingOption.LowLatency -> R.string.rn_setting_neuron_low_latency
        FramePacingOption.Balanced -> R.string.rn_setting_neuron_balanced
        FramePacingOption.SmoothestVideo -> R.string.rn_setting_neuron_smoothest_video
    }

    private fun FramePacingOption.displayName() = getString(displayNameRes())

    private var framePacingDialog : AlertDialog? = null

    private var framePacingDisposable : Disposable? = null

    private fun setupFramePacing() {
        val activity = activity ?: return
        framePacingDisposable?.dispose()
        binding.rowFramePacing.setupSingleChoiceItemsDropdown(
            viewLifecycleOwner = activity,
            title = getStringExt(R.string.rn_setting_neuron_frame_pacing),
            subtitle = null,
            options = { FramePacingOption.entries },
            selectedItem = { viewModel.framePacingLiveData.value },
            liveData = viewModel.framePacingLiveData,
            displayName = { it.displayName() },
            onUserSelectItem = {
                viewModel.setFramePacing(it)
            }
        )
        updateAllCorners()
    }


    private fun updateHDRSwitch(displayModeOption: DisplayModeOption? = null) {
        //BAA-2345
        binding.rowHdr.root.visibleIf { (context?.isHdrSupported() ?: false) && displayModeOption?.allowHdrOption == true }
        updateAllCorners()
    }

    private fun setupHDR() {
        with(binding.rowHdr) {
            setupSwitch(
                viewLifecycleOwner = viewLifecycleOwner,
                title = getStringExt(R.string.rn_setting_neuron_hdr_title),
                subtitle = "${getStringExt(R.string.rn_setting_neuron_hdr_subtitle_line1)} ${
                    getStringExt(
                        R.string.rn_setting_neuron_hdr_subtitle_line2
                    )
                }",
                isChecked = viewModel.hdrLiveData.value,
                liveData = viewModel.hdrLiveData,
                onCheckedChangeListener = { _, isChecked ->
                    viewModel.setEnableHdr(isChecked)
                }
            )
        }
    }

    private fun updateMuteSpeakersSwitch(displayModeOption: DisplayModeOption? = null) {
        // disable since BAA-2345
        binding.rowMuteHostPc.root.visibleIf { displayModeOption == DisplayModeOption.DuplicateDisplay }
        updateAllCorners()
    }

    private fun setupMuteHostPc() {
        with(binding.rowMuteHostPc) {
            setupSwitch(
                viewLifecycleOwner = viewLifecycleOwner,
                title = getStringExt(R.string.rn_setting_neuron_mute_host_pc_title),
                isChecked = viewModel.muteHostPcLiveData.value,
                liveData = viewModel.muteHostPcLiveData,
                onCheckedChangeListener = { _, isChecked ->
                    viewModel.setMuteHostPc(isChecked)
                }
            )
        }
    }


    private fun TouchScreenOption.displayNameRes() = when(this) {
        TouchScreenOption.VirtualTrackpad -> R.string.rn_setting_neuron_virtual_trackpad
        TouchScreenOption.DirectTouch -> R.string.rn_setting_neuron_direct_touch
    }

    private fun TouchScreenOption.displayName() = getString(displayNameRes())

    private var touchScreenDisposable : Disposable? = null

    private fun setupTouchScreen() {
        val activity = activity ?: return

        binding.rowTouchScreen.setupSingleChoiceItemsDropdown(
            viewLifecycleOwner = activity,
            title = getStringExt(R.string.rn_setting_neuron_touch_screen),
            subtitle = null,
            options = { TouchScreenOption.entries },
            selectedItem = { viewModel.touchScreenOptionLiveData.value },
            liveData = viewModel.touchScreenOptionLiveData,
            displayName = { getString(it.displayNameRes()) },
            onUserSelectItem = {
                viewModel.setTouchScreen(it)
            }
        )

        updateAllCorners()
    }


    private fun setupAutoCloseGameCountDown() {
        val names = resources.getStringArray(R.array.auto_close_games_names)
        val values = resources.getIntArray(R.array.auto_close_games_values).toTypedArray().toList()
        require(names.size == values.size) { "auto_close_games_names not match auto_close_games_values" }
        fun displayName(value : Int) = values.indexOf(value).takeIf { it > -1 }?.let { names.getOrNull(it) } ?: value.toString()
        binding.rowAutoCloseGameCountdownDropdown.setupSingleChoiceItemsDropdown(
            viewLifecycleOwner = viewLifecycleOwner,
            title = getString(R.string.rn_setting_neuron_autoclose_countdown_title),
            subtitle = getString(R.string.rn_setting_neuron_autoclose_countdown_subtitle),
            options = { values },
            selectedItem = { viewModel.autoCloseGameCountDownLiveData.value ?: values[0] },
            liveData = viewModel.autoCloseGameCountDownLiveData,
            displayName = ::displayName,
            onUserSelectItem = {
                    value ->
                viewModel.setAutoCloseGameCountDown(value)
            }
        )
    }


    private fun setupVideoFormat() {
        lifecycleScope.launch {
            binding.rowVideoFormatDropdown.root.gone()
            val videoFormatMetaData = withContext(ioDispatcher) { VideoFormatMetaData.create(requireContext()) }
            setupVideoFormatDropdown(videoFormatMetaData)
        }
    }

    /**
     * 1. Hide video format dropdown first
     * 2. Only show the [FormatOptionMeta]s that are supported (Based on [videoFormatMetaData])
     * 3. If there is only 1 (excluding [FormatOptionMeta.AUTO]) format, then we select default [FormatOptionMeta.AUTO]
     *    and hide the UI
     * 4. Else show the UI for supported format
     */
    private fun setupVideoFormatDropdown(videoFormatMetaData : VideoFormatMetaData) {
        Timber.v("setupVideoFormatDropdown: $videoFormatMetaData")
        fun FormatOptionMeta.isSupported() = when (this) {
            FormatOptionMeta.AUTO -> true
            FormatOptionMeta.AV1 -> videoFormatMetaData.isAV1Supported
            FormatOptionMeta.HEVC -> videoFormatMetaData.isHevcSupported
            FormatOptionMeta.H264 -> videoFormatMetaData.isAvcSupported
        }
        // keep hiding UI
        binding.rowVideoFormatDropdown.root.gone()
        val supportedFormats = FormatOptionMeta.displayOrder.filter { it.isSupported() }
        if(supportedFormats.count { it != FormatOptionMeta.AUTO } == 1) {
            // only 1 support format, just set it to default then
            debugToast("Only 1 supported video format")
            viewModel.setVideoFormatOption(FormatOptionMeta.default.formatOption)
            return
        }

        // show UI
        binding.rowVideoFormatDropdown.root.visible()
        val default = FormatOptionMeta.default
        binding.rowVideoFormatDropdown.setupSingleChoiceItemsDropdown(
            viewLifecycleOwner = viewLifecycleOwner,
            title = getString(R.string.rn_setting_neuron_video_format_title),
            subtitle = getString(R.string.rn_setting_neuron_video_format_subtitle),
            options = { supportedFormats },
            selectedItem = { viewModel.videoFormatOptionLiveData.value?.getMeta() ?: default },
            liveData = viewModel.videoFormatOptionLiveData.map { it.getMeta() ?: default },
            displayName = { getString(it.displayText) },
            onUserSelectItem = { value ->
                viewModel.setVideoFormatOption(value.formatOption)
            }
        )
        updateAllCorners()
    }


}
