package com.razer.neuron.oobe

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import com.limelight.R
import com.limelight.databinding.RnItemOobePermissionBinding
import com.razer.neuron.common.CheckableConstraintLayout

class OobePermissionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CheckableConstraintLayout(context, attrs, defStyleAttr) {


    private val binding: RnItemOobePermissionBinding =
        RnItemOobePermissionBinding.inflate(LayoutInflater.from(context), this)

    val icon by lazy { binding.icPermissionIcon }
    val title by lazy { binding.tvPermissionName }
    val subtitle by lazy { binding.tvPermissionSubtitle }
    val checkedIcon by lazy { binding.ivChecked }


    init {
        setBackgroundResource(R.drawable.oobe_permission_background)
        isFocusable = false
        backgroundTintList =
            ContextCompat.getColorStateList(context, R.color.oobe_permission_state_list)
        icon.imageTintList = ContextCompat.getColorStateList(context, R.color.oobe_permission_text)
        isClickable = true
    }

}