package com.razer.neuron.common

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import com.limelight.R
import com.limelight.databinding.RnLayoutGameOverlayViewBinding
import com.limelight.databinding.RnOverlayHintViewBinding
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.visible
import com.razer.neuron.model.OverlayHint
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class GameOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    interface OverlayHintActionInterface {
        fun onOverlayHintClicked(overlayHint: OverlayHint)
        fun onOverlayHintsHide()
    }

    private var binding: RnLayoutGameOverlayViewBinding = RnLayoutGameOverlayViewBinding.inflate(LayoutInflater.from(context), this)
    private var onOverlayHintAction: OverlayHintActionInterface? = null

    init {
        setupOverlayHints()
        setupButtons()
    }

    private fun setupOverlayHints() {
        hideOverlayHints()
        OverlayHint.entries.forEach { hint ->
            val overlayHintBinding = RnOverlayHintViewBinding.inflate(LayoutInflater.from(context), null, false).run {
                ivIcon.setImageResource(hint.iconRes)
                this
            }

            overlayHintBinding.root.setOnClickListener {
                onOverlayHintAction?.onOverlayHintClicked(hint)
                if (hint.isToggle) {
                    overlayHintBinding.root.isSelected = !overlayHintBinding.root.isSelected
                }
            }

            binding.overlayContent.addView(overlayHintBinding.root)

            (overlayHintBinding.root.layoutParams as? MarginLayoutParams)?.let {
                it.leftMargin = dimenResToPx(R.dimen.margin_2x).roundToInt()
                it.rightMargin = dimenResToPx(R.dimen.margin_2x).roundToInt()
                overlayHintBinding.root.layoutParams = it
            }
        }
    }

    private fun setupButtons() {
        binding.ivCloseBtn.setOnClickListener {
            resetState()
            hideOverlayHints()
        }
        binding.ivShowBtn.setOnClickListener {
            showOverlayHints()
        }
    }

    private fun showOverlayHints() {
        binding.clOverlayContainer.visible()
        binding.ivShowBtn.gone()
    }

    private fun hideOverlayHints() {
        binding.clOverlayContainer.gone()
        binding.ivShowBtn.visible()
    }

    fun resetState() {
        hideOverlayHints()
        binding.overlayContent.children.forEach {
            it.isSelected = false
        }
        onOverlayHintAction?.onOverlayHintsHide()
    }

    fun setOverlayHintAction(onOverlayHintAction: OverlayHintActionInterface) {
        this.onOverlayHintAction = onOverlayHintAction
    }

}