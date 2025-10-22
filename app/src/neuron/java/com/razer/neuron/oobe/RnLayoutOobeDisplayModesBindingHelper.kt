package com.razer.neuron.oobe

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.CompoundButton
import androidx.annotation.DrawableRes
import com.limelight.R
import com.limelight.databinding.RnLayoutOobeDisplayModesBinding
import com.razer.neuron.common.ImageRadioButton
import com.razer.neuron.extensions.visibleIf
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.utils.calculateVirtualDisplayMode
import com.razer.neuron.utils.getStringExt
import timber.log.Timber

interface RnLayoutOobeDisplayModesBindingHelper {

    val activity: Activity

    val layoutDisplayModesBinding: RnLayoutOobeDisplayModesBinding

    fun showLayout(view: View)

    fun RnOobeModel.State.DisplayModes.handle() {
        showLayout(layoutDisplayModesBinding.root)
        setup(selected)
    }


    private fun ImageRadioButton.setup(
        displayMode: DisplayModeOption,
        isChecked: Boolean,
        @DrawableRes iconRes : Int,
        title: String,
        description: String,
        @DrawableRes leftImageRes : Int,
    ) {
        val tag = "ImageRadioButton.setup($displayMode)"
        with(this) {
            clickableLayout.setOnFocusChangeListener { _, _isFocused ->
                Timber.v("$tag: _isFocused=$_isFocused")
                if(_isFocused) {
                    this@setup.isChecked = true
                }
            }
            onCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, _isChecked ->
                Timber.v("$tag: _isChecked=$_isChecked")
                if(_isChecked) {
                    onDisplayModeSelected(displayMode)
                }
            }
            if(isChecked) {
                layoutDisplayModesBinding.tvDisplayModeFooter.text = description
                layoutDisplayModesBinding.ivLeftImage.setImageResource(leftImageRes)
            }
            update(
                title = title,
                subtitle = null, // hide subtitle
                isChecked = isChecked,
                iconRes = iconRes
            )
        }
    }

    private fun RnOobeModel.State.DisplayModes.setup(selected: DisplayModeOption) {
        val tag = "DisplayModes.setup(selected=$selected)"
        Timber.v("$tag: ")
        val virtualDisplayMode = activity.calculateVirtualDisplayMode(isLimitRefreshRate = RemotePlaySettingsPref.isLimitRefreshRate).getOrNull()
        val virtualResolution = virtualDisplayMode?.let { "${it.width}x${it.height}" } ?: ""
        val virtualRefreshRate = virtualDisplayMode?.refreshRate ?: ""
        layoutDisplayModesBinding.irbDuplicateScreen.setup(
            displayMode = DisplayModeOption.DuplicateDisplay,
            isChecked = selected == DisplayModeOption.DuplicateDisplay,
            title = getStringExt(R.string.rn_settings_display_mode_duplicate),
            iconRes = R.drawable.ic_display_mode_duplicate,
            description = getStringExt(R.string.rn_settings_display_mode_duplicate_desc),
            leftImageRes = R.drawable.bg_duplicate_screen
        )

        layoutDisplayModesBinding.irbSeparateScreen.setup(
            displayMode = DisplayModeOption.SeparateDisplay,
            isChecked = selected == DisplayModeOption.SeparateDisplay,
            title = getStringExt(R.string.rn_settings_display_mode_separate),
            iconRes = R.drawable.ic_display_mode_separate,
            description = activity.getString(R.string.rn_settings_display_mode_separate_desc_x_resolution_x_refresh_rate, virtualResolution, virtualRefreshRate),
            leftImageRes = R.drawable.bg_separate_screen
        )

        layoutDisplayModesBinding.irbPhoneOnly.setup(
            displayMode = DisplayModeOption.PhoneOnlyDisplay,
            isChecked = selected == DisplayModeOption.PhoneOnlyDisplay,
            title = getStringExt(R.string.rn_settings_display_mode_phone_only_2_lines),
            iconRes = R.drawable.ic_display_mode_phone_only,
            description = activity.getString(R.string.rn_settings_display_mode_phone_only_desc_x_resolution_x_refresh_rate, virtualResolution, virtualRefreshRate),
            leftImageRes = R.drawable.bg_phone_only
        )
        layoutDisplayModesBinding.irbSeparateScreen.visibleIf {
            RemotePlaySettingsPref.isSeparateScreenDisplayEnabled
        }
    }

    fun onDisplayModeSelected(displayMode: DisplayModeOption)

}