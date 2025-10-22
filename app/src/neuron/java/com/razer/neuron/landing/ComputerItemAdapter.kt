package com.razer.neuron.landing

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.limelight.R
import com.limelight.databinding.RnItemNeuronLandingComputerBinding
import com.limelight.nvstream.http.ComputerDetails
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.requestFocus
import com.razer.neuron.extensions.visible
import com.razer.neuron.settings.devices.DeviceAction
import timber.log.Timber
import kotlin.math.roundToInt

class ComputerItemAdapter : ListAdapter<ComputerItem, RecyclerView.ViewHolder>(
    DiffItemCallback()
) {

    interface Listener {
        fun onComputerFocus(computerDetails: ComputerDetails)

        fun onComputerClicked(computerDetails: ComputerDetails, action: DeviceAction)
    }

    var listener: Listener? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return ComputerViewHolder(
            RnItemNeuronLandingComputerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        (holder as ComputerViewHolder).bind(getItem(position))
    }

    /**
     * If we have duplicate names for paired, we need some way to identify them.
     */
    private fun ComputerDetails.last4UuidChars() = uuid?.let {
        if (it.length >= 4) it.substring(it.length - 4) else this
    }



    private inner class ComputerViewHolder(val binding: RnItemNeuronLandingComputerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(data: ComputerItem) {
            val computerDetails = (data as? ComputerItem)?.computerDetails ?: return
            val nameExtra = computerDetails.last4UuidChars()?.run { "($this)" } ?: ""
            binding.tvComputerTitle.text = "${computerDetails.name} ${if(data.hasDuplicate) nameExtra else ""}".trimEnd()
           Timber.v("ComputerViewHolder: ${computerDetails.name} ${nameExtra} data.hasDuplicate=${data.hasDuplicate}")
            val isOnline = computerDetails.state == ComputerDetails.State.ONLINE
            val isLoading = computerDetails.state == ComputerDetails.State.UNKNOWN
            val isPaired = data.isPaired()
            binding.ivComputerIcon.setImageResource(
                if (isLoading) {
                    binding.progressbarLoading.visible()
                    R.drawable.ic_neuron_landing_computer_available
                } else if (isPaired) {
                    binding.progressbarLoading.gone()
                    // for paired, we only show offline if we are sure
                    if (isOnline) R.drawable.ic_neuron_landing_computer_paired_online else R.drawable.ic_neuron_landing_computer_paired_offline
                } else {
                    binding.progressbarLoading.gone()
                    R.drawable.ic_neuron_landing_computer_available
                }
            )
            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    listener?.onComputerFocus(computerDetails)
                }
            }
            binding.root.setOnClickListener {
                listener?.onComputerClicked(computerDetails,
                    if (!isOnline) {
                        DeviceAction.WOL
                    } else if (isPaired) {
                        DeviceAction.STREAM
                    } else {
                        DeviceAction.PAIR
                    }
                )
            }
            if (data.isFocus) {
                binding.root.post {
                    binding.root.requestFocus(true)
                }
            }
        }
    }

    class ItemDecoration : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            val position = parent.getChildAdapterPosition(view)
            if (position == -1) return
            val last = position == (parent.adapter?.itemCount ?: 0) - 1
            if (!last) {
                outRect.right += dimenResToPx(R.dimen.margin_2x).roundToInt()
            }
        }
    }

    private class DiffItemCallback : DiffUtil.ItemCallback<ComputerItem>() {
        override fun areItemsTheSame(oldItem: ComputerItem, newItem: ComputerItem) = oldItem.isItemTheSame(newItem)
        override fun areContentsTheSame(oldItem: ComputerItem, newItem: ComputerItem) = oldItem.isContentTheSame(newItem)
    }

    private fun toIterator() = object : Iterator<ComputerItem> {
        private var i = 0
        override fun hasNext() = i < itemCount
        override fun next(): ComputerItem {
            check(i + 1 < itemCount)
            return getItem(++i)
        }
    }
}



