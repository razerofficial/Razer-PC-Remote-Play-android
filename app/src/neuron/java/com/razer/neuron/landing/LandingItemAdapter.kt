package com.razer.neuron.landing

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.limelight.R
import com.limelight.databinding.RnItemNeuronComputerListBinding
import com.limelight.databinding.RnItemNeuronSettingsActionBinding
import com.limelight.databinding.RnItemNeuronSettingsGroupHeaderBinding
import com.limelight.databinding.RnItemNeuronSettingsLoadingBinding
import com.limelight.nvstream.http.ComputerDetails
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.visible
import com.razer.neuron.extensions.visibleIf
import com.razer.neuron.settings.devices.DeviceAction
import kotlin.math.roundToInt

private const val TYPE_COMPUTER_GROUP_HEADER = 0
private const val TYPE_COMPUTER_LIST = 1
private const val TYPE_LOADING = 2
private const val TYPE_ACTION = 3

class LandingItemAdapter :
    ListAdapter<LandingItem, RecyclerView.ViewHolder> (DiffItemCallback()),
    ComputerItemAdapter.Listener
{

    interface Listener {
        fun onActionFocus(actionItem: LandingItem.ActionItem)
        fun onActionClicked(actionItem : LandingItem.ActionItem)
        fun onComputerFocus(computerDetails: ComputerDetails)
        fun onComputerClicked(computer: ComputerDetails, action: DeviceAction)
    }

    var listener: Listener? = null

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is LandingItem.PairedGroupHeader,
            is LandingItem.UnpairedGroupHeader -> TYPE_COMPUTER_GROUP_HEADER
            is LandingItem.ComputerList -> TYPE_COMPUTER_LIST
            is LandingItem.Loading -> TYPE_LOADING
            is LandingItem.ActionItem -> TYPE_ACTION
            else -> error("Unsupported type ${item.javaClass.simpleName} at $position")
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_COMPUTER_GROUP_HEADER -> GroupHeaderViewHolder(
                RnItemNeuronSettingsGroupHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            TYPE_COMPUTER_LIST -> ComputerListViewHolder(
                RnItemNeuronComputerListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            TYPE_LOADING -> LoadingViewHolder(
                RnItemNeuronSettingsLoadingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            TYPE_ACTION -> ActionViewHolder(
                RnItemNeuronSettingsActionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> error("$viewType not supported")
        }
    }

    private fun hasComputerList() = (0 until itemCount).any { getItemViewType(it) == TYPE_COMPUTER_LIST }

    fun submitList(list: List<LandingItem>, recyclerView: RecyclerView, commitCallback: Runnable) {
        super.submitList(list, commitCallback)
        recyclerView.preserveFocusAfterLayout = hasComputerList()
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder.itemViewType) {
            TYPE_COMPUTER_GROUP_HEADER -> (holder as GroupHeaderViewHolder).bind(getItem(position))
            TYPE_COMPUTER_LIST -> (holder as ComputerListViewHolder).bind(getItem(position))
            TYPE_LOADING -> (holder as LoadingViewHolder).bind(getItem(position))
            TYPE_ACTION -> (holder as ActionViewHolder).bind(getItem(position))
        }
    }

    private inner class GroupHeaderViewHolder(val binding: RnItemNeuronSettingsGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var isShowProgressBar : Boolean
            get() = binding.layoutHeaderLoadingContainer.isVisible
            set(value) {
                binding.layoutHeaderLoadingContainer.visibleIf { value }
            }

        fun bind(data: LandingItem) {
            when (data) {
                is LandingItem.PairedGroupHeader -> data.bind()
                is LandingItem.UnpairedGroupHeader -> data.bind()
                else -> error("${data.javaClass.simpleName} bind impl found")
            }
        }

        private fun LandingItem.PairedGroupHeader.bind() {
            isShowProgressBar = false
            binding.tvHeaderSubtitle.text = itemView.resources.getText(R.string.rn_paired_computers)
        }

        private fun LandingItem.UnpairedGroupHeader.bind() {
            isShowProgressBar = isLoading
            binding.tvHeaderSubtitle.text = itemView.resources.getText(R.string.rn_unpaired_computers)
        }
    }

    private inner class ComputerListViewHolder(val binding: RnItemNeuronComputerListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(data: LandingItem) {
            when (data) {
                is LandingItem.ComputerList -> {
                    binding.rvComputerList.adapter?.let {
                        (it as ComputerItemAdapter).submitList(data.computerItemList)
                    } ?: run {
                        binding.rvComputerList.addItemDecoration(ComputerItemAdapter.ItemDecoration())
                        binding.rvComputerList.layoutManager = GridLayoutManager(binding.root.context, 4)
                        binding.rvComputerList.itemAnimator = null
                        binding.rvComputerList.adapter = ComputerItemAdapter().apply {
                            listener = this@LandingItemAdapter
                            submitList(data.computerItemList)
                        }
                    }
                }
                else -> error("${data.javaClass.simpleName} bind impl found")
            }
        }
    }

    private inner class ActionViewHolder(val binding: RnItemNeuronSettingsActionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val actionIcon by lazy { binding.ivActionIcon }
        fun bind(data: LandingItem) {
            if (data !is LandingItem.ActionItem) return
            itemView.setOnClickListener {
                when(data) {
                    is LandingItem.AddManually -> {
                        listener?.onActionClicked(data)
                    }
                    else -> Unit
                }
            }
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    listener?.onActionFocus(data)
                }
            }

            when (data) {
                is LandingItem.AddManually -> data.bind()
                else -> error("${data.javaClass.simpleName} bind impl found")
            }
        }

        private fun LandingItem.AddManually.bind() {
            actionIcon.visible()
            actionIcon.setImageResource(R.drawable.ic_settings_add)
            binding.tvTitle.text = itemView.resources.getText(R.string.rn_add_computer_manually)
            binding.tvSubtitle.text = itemView.resources.getText(R.string.rn_add_computer_manually_subtitle)
        }
    }

    private inner class LoadingViewHolder(val binding: RnItemNeuronSettingsLoadingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(landingItem: LandingItem) {
            binding.tvLoading.text = (landingItem as LandingItem.Loading).hint
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
            val columnIndex = (view.layoutParams as? GridLayoutManager.LayoutParams)?.spanIndex ?: 0
            if (position == -1) return
            val last = position == (parent.adapter?.itemCount ?: 0) - 1
            val adapter = parent.adapter ?: return
            val itemViewType = adapter.getItemViewType(position)
            when (itemViewType) {
                TYPE_COMPUTER_LIST -> {
                    outRect.bottom = dimenResToPx(R.dimen.margin_2x).roundToInt()
                    outRect.left = if(columnIndex == 0) dimenResToPx(R.dimen.margin_4x).roundToInt() else 0
                }
                TYPE_COMPUTER_GROUP_HEADER -> {
                    val verticalMargin = dimenResToPx(R.dimen.margin_2x).roundToInt()
                    outRect.top = verticalMargin
                    outRect.bottom = verticalMargin
                    //outRect.bottom = dimenResToPx(R.dimen.margin_2x).roundToInt()
                }
                else -> Unit
            }
            if (last) {
                outRect.bottom += dimenResToPx(R.dimen.margin_4x).roundToInt()
            }
        }
    }

    private class DiffItemCallback : DiffUtil.ItemCallback<LandingItem>() {
        override fun areItemsTheSame(oldItem: LandingItem, newItem: LandingItem) = oldItem.isItemTheSame(newItem)
        override fun areContentsTheSame(oldItem: LandingItem, newItem: LandingItem) = oldItem.isContentTheSame(newItem)
    }

    override fun onComputerFocus(computerDetails: ComputerDetails) {
        listener?.onComputerFocus(computerDetails)
    }

    override fun onComputerClicked(computerDetails: ComputerDetails, action: DeviceAction) {
        listener?.onComputerClicked(computerDetails, action)
    }
}

