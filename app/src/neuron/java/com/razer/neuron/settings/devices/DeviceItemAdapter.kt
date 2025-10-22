package com.razer.neuron.settings.devices

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.limelight.R
import com.limelight.databinding.RnItemNeuronSettingsActionBinding
import com.limelight.databinding.RnItemNeuronSettingsGroupHeaderBinding
import com.limelight.databinding.RnItemNeuronSettingsHeaderBinding
import com.limelight.databinding.RnItemNeuronSettingsLoadingBinding
import com.limelight.nvstream.http.ComputerDetails
import com.razer.neuron.RnApp
import com.razer.neuron.common.updateCorners
import com.razer.neuron.exceptions.PopupMenuHelper
import com.razer.neuron.exceptions.dismissSafely
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.gone
import com.razer.neuron.common.pillBackground
import com.razer.neuron.extensions.visible
import com.razer.neuron.extensions.visibleIf
import timber.log.Timber
import kotlin.math.roundToInt


private const val TYPE_COMPUTER_HEADER = 0
private const val TYPE_COMPUTER_GROUP_HEADER = 1
private const val TYPE_COMPUTER = 2
private const val TYPE_ACTION = 3
private const val TYPE_LOADING = 5


class DeviceItemAdapter(
    private val lifecycleOwner: LifecycleOwner
) : ListAdapter<DeviceItem, RecyclerView.ViewHolder>(
    DiffItemCallback()
) {

    interface Listener {
        fun onComputerActionClicked(computer: ComputerDetails, action: DeviceAction)

        fun onActionClicked(actionItem : DeviceItem.ActionItem)

        fun onSwitchChecked(switchItem : DeviceItem.SwitchItem, isChecked : Boolean)

    }

    var listener: Listener? = null

    private var popupWindow: PopupWindow? = null

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is DeviceItem.Computer -> TYPE_COMPUTER
            is DeviceItem.Header -> TYPE_COMPUTER_HEADER
            is DeviceItem.PairedGroupHeader,
            is DeviceItem.UnpairedGroupHeader,
            -> TYPE_COMPUTER_GROUP_HEADER

            is DeviceItem.Loading -> TYPE_LOADING
            is DeviceItem.ActionItem -> TYPE_ACTION
            else -> error("Unsupported type ${item.javaClass.simpleName} at $position")
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _: LifecycleOwner, event: Lifecycle.Event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    popupWindow.dismissSafely()
                    popupWindow = null
                }
                else -> Unit
            }
        })
    }

    private fun getItemOrNull(position: Int) : DeviceItem? {
        return if(itemCount > 0 && position in 0 until itemCount) getItem(position) else null
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_COMPUTER_HEADER -> HeaderViewHolder(
                RnItemNeuronSettingsHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            TYPE_COMPUTER_GROUP_HEADER -> GroupHeaderViewHolder(
                RnItemNeuronSettingsGroupHeaderBinding.inflate(
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

            TYPE_COMPUTER -> ComputerViewHolder(
                RnItemNeuronSettingsActionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            TYPE_ACTION -> ComputerActionViewHolder(
                RnItemNeuronSettingsActionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> error("$viewType not supported")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder.itemViewType) {
            TYPE_COMPUTER_HEADER -> (holder as HeaderViewHolder).bind()
            TYPE_COMPUTER_GROUP_HEADER -> (holder as GroupHeaderViewHolder).bind(getItem(position), position)
            TYPE_COMPUTER -> (holder as ComputerViewHolder).bind(getItem(position), position)
            TYPE_ACTION -> (holder as ComputerActionViewHolder).bind(getItem(position), position)
            TYPE_LOADING -> (holder as LoadingViewHolder).bind()

        }
    }

    private inner class HeaderViewHolder(val binding: RnItemNeuronSettingsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.tvHeaderTitle.text = itemView.resources.getText(R.string.rn_all_computers)
            binding.tvHeaderSubtitle.text = itemView.resources.getText(R.string.rn_all_computers_subtitle)
        }
    }

    private fun DeviceItem.Computer.hasDuplicateComputerName() = runCatching {
        toIterator().asSequence().any {
            it is DeviceItem.Computer
                    && it != this
                    && it.computerDetails.name == computerDetails.name
        }
    }.getOrNull() ?: false



    private inner class GroupHeaderViewHolder(val binding: RnItemNeuronSettingsGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var isShowProgressBar : Boolean
            get() = binding.layoutHeaderLoadingContainer.isVisible
            set(value) {
                binding.layoutHeaderLoadingContainer.visibleIf { value }
            }


        fun bind(data: DeviceItem, position: Int) {

            when (data) {
                is DeviceItem.PairedGroupHeader -> data.bind()
                is DeviceItem.UnpairedGroupHeader -> data.bind()
                else -> error("${data.javaClass.simpleName} bind impl found")
            }
        }

        private fun DeviceItem.PairedGroupHeader.bind() {
            isShowProgressBar = false
            binding.tvHeaderSubtitle.text = itemView.resources.getText(R.string.rn_paired_computers)
        }

        private fun DeviceItem.UnpairedGroupHeader.bind() {
            isShowProgressBar = isLoading
            binding.tvHeaderSubtitle.text = itemView.resources.getText(R.string.rn_unpaired_computers)
        }
    }


    private inner class LoadingViewHolder(val binding: RnItemNeuronSettingsLoadingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.tvLoading.text = itemView.resources.getString(R.string.rn_pairing_hint)
        }
    }

    /**
     * If we have duplicate names for paired, we need some way to identify them.
     */
    private fun ComputerDetails.last4UuidChars() = with(uuid) {
        if (length >= 4) substring(length - 4) else this
    }

    private fun View.updateCorners(position: Int) {
        val previous = getItemOrNull(position - 1)
        val next = getItemOrNull(position + 1)
        val isFirstInGroup = when(getItemOrNull(position - 1)) {
            is DeviceItem.ActionItem,
            is DeviceItem.Computer -> false
            else -> true
        }
        val isLastInGroup = when(getItemOrNull(position + 1)) {
            is DeviceItem.ActionItem,
            is DeviceItem.Computer -> false
            else -> true
        }
        updateCorners(isFirst = isFirstInGroup, isLast = isLastInGroup)
    }


    private inner class ComputerViewHolder(val binding: RnItemNeuronSettingsActionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(data: DeviceItem, position : Int) {
            val computerDetails = (data as? DeviceItem.Computer)?.computerDetails ?: return
            val nameExtra = computerDetails.last4UuidChars()?.run { "($this)" } ?: ""
            binding.pillBackground()?.updateCorners(position = position)
            binding.tvTitle.text = "${computerDetails.name} ${if(data.hasDuplicateComputerName()) nameExtra else ""}".trimEnd()
            val isOnline = computerDetails.state == ComputerDetails.State.ONLINE
            val isOffline = computerDetails.state == ComputerDetails.State.OFFLINE
            val isPaired = data.isPaired()

            binding.tvSubtitle.visible()
            binding.ivActionStartIcon.imageTintList = null
            binding.ivActionStartIcon.visible()
            binding.ivActionStartIcon.alpha = 1f
            if(isOnline) {
                binding.tvSubtitle.text = itemView.resources.getString(R.string.rn_discovery_state_pc_online)
                if (computerDetails.offlineCount > 0) {
                    // some visual indication that it is trying to poll the computer
                    // but hide the subtitle
                    binding.ivActionStartIcon.alpha = 0.66f
                    binding.tvSubtitle.gone()
                }
            } else if(isOffline) {
                // since it is offline we can say it is offline
                // icon will also indicate this
                binding.tvSubtitle.gone()
            } else {
                // hide the subtitle since it is not paired and we don't know
                // if it is online or not
                binding.tvSubtitle.gone()
            }


            binding.ivActionStartIcon.setImageResource(
                if (isPaired) {
                    // for paired, we only show offline if we are sure
                    if (isOnline) R.drawable.ic_neuron_computer_online_paired_padding else R.drawable.ic_neuron_computer_offline_paired_padding
                } else {
                    // for unpaired, we only show online if we are sure
                    if (isOnline) R.drawable.ic_neuron_computer_online_padding else R.drawable.ic_neuron_computer_offline_padding
                }
            )

            binding.ivActionEndIcon.setImageResource(R.drawable.ic_neuron_settings_overflow_3dots_padding)
            binding.ivActionEndIcon.visible()
            itemView.setOnClickListener {
                val menuItems = data.actions.map { it.toMenuItem() }
                popupWindow?.dismissSafely()
                popupWindow = PopupMenuHelper.createAndShow(anchorView = binding.ivActionEndIcon, popupPosition = PopupMenuHelper.PopupPosition.EndTopToStartTopOfAnchor, menuItems = menuItems) {
                    listener?.onComputerActionClicked(computerDetails, it.item)
                    true
                }
            }

        }
    }

    fun DeviceAction.toMenuItem() = when(this) {
        DeviceAction.PAIR -> PopupMenuHelper.MenuItem(this, RnApp.appContext.resources.getText(R.string.rn_device_page_pc_pair))
        DeviceAction.UNPAIR -> PopupMenuHelper.MenuItem(this, RnApp.appContext.resources.getText(R.string.rn_device_page_pc_unpair))
        DeviceAction.STREAM -> PopupMenuHelper.MenuItem(this, RnApp.appContext.resources.getText(R.string.rn_device_page_pc_start_play))
        DeviceAction.WOL -> PopupMenuHelper.MenuItem(this, RnApp.appContext.resources.getText(R.string.rn_retry))
    }


    private inner class ComputerActionViewHolder(val binding: RnItemNeuronSettingsActionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val switchLayout by lazy { binding.switchLayout }
        private val switchAction by lazy { binding.switchAction }
        private val actionIcon by lazy { binding.ivActionStartIcon }
        fun bind(data: DeviceItem, position : Int) {
            if(data !is DeviceItem.ActionItem) return
            Timber.v("updateCorners: position=$position, data=$data")
            binding.pillBackground()?.updateCorners(position = position)
            itemView.setOnClickListener {
                when(data) {
                    is DeviceItem.SwitchItem -> {
                        switchAction.isChecked = !data.isChecked
                    }
                    else -> {
                        listener?.onActionClicked(data)
                    }
                }
            }

            if(data is DeviceItem.SwitchItem) {
                switchAction.setOnCheckedChangeListener(null)
                switchAction.isChecked = data.isChecked
                switchAction.setOnCheckedChangeListener { compoundButton, b ->
                    listener?.onSwitchChecked(data, b)
                }
                switchLayout.visible()
            } else {
                switchLayout.gone()
            }


            when (data) {
                is DeviceItem.AddManually -> data.bind()
                is DeviceItem.SelectTheme -> data.bind()
                else -> error("${data.javaClass.simpleName} bind impl found")
            }

        }

        private fun DeviceItem.SelectTheme.bind() {
            actionIcon.gone()
            binding.tvTitle.text = itemView.resources.getText(R.string.rn_theme_title)
            binding.tvSubtitle.text = itemView.resources.getText(selected.title)
        }

        private fun DeviceItem.AddManually.bind() {
            actionIcon.visible()
            actionIcon.setImageResource(R.drawable.ic_settings_add)
            binding.tvTitle.text = itemView.resources.getText(R.string.rn_add_computer_manually)
            binding.tvSubtitle.text = itemView.resources.getText(R.string.rn_add_computer_manually_subtitle)
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
            if (last) {
                outRect.bottom += dimenResToPx(R.dimen.margin_4x).roundToInt()
            }
        }
    }

    class DiffItemCallback : DiffUtil.ItemCallback<DeviceItem>() {
        override fun areItemsTheSame(oldItem: DeviceItem, newItem: DeviceItem) = oldItem.isItemTheSame(newItem)
        override fun areContentsTheSame(oldItem: DeviceItem, newItem: DeviceItem) : Boolean {
            val isContentTheSame = oldItem.isContentTheSame(newItem)
            return false // due to pillBackground, even if isContentTheSame is true, we still need a redraw
        }

    }

    private fun toIterator() = object : Iterator<DeviceItem> {
        private var i = 0
        override fun hasNext() = i < itemCount
        override fun next(): DeviceItem {
            check(i + 1 < itemCount)
            return getItem(++i)
        }
    }
}
