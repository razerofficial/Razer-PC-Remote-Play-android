package com.razer.neuron.common

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.widget.PopupWindow
import androidx.constraintlayout.widget.ConstraintLayout
import com.limelight.R
import com.limelight.databinding.RnDropdownButtonViewBinding
import com.razer.neuron.exceptions.PopupMenuHelper

/**
 * A implementation of a button with a [PopupWindow] dropdown menu, matching the design of dropdown
 * button used by Google Pixel.
 */
class DropdownButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val binding = RnDropdownButtonViewBinding.inflate(LayoutInflater.from(context), this)

    private val tvTitle by lazy { binding.ddbvTvTitle }
    val ivIcon by lazy { binding.ddbvIvIcon }

    /**
     * Return true if it is consumed
     */
    var onItemClickedListener: ((Any) -> Boolean)? = null


    var text: CharSequence
        get() = tvTitle.text
        private set(value) {
            tvTitle.text = value
        }

    private var popupWindow : PopupWindow? = null

    /**
     * Set all available item for this dropdown
     */
    var itemObjects = listOf<Pair<Any, CharSequence>>()

    /**
     * True if [PopupWindow] is not dismissed
     */
    val isPopupWindowShowing get() = popupWindow != null

    private var selectedItemObject : Any? = null

    init {
        setBackgroundResource(R.drawable.bg_transparent)
    }



    private fun onMenuItemClick(menuItem: PopupMenuHelper.MenuItem<Pair<Any,CharSequence>>?): Boolean {
        menuItem ?: return false
        setSelectedItem(menuItem.item.first, menuItem.item.second)
        return onItemClickedListener?.invoke(menuItem.item.first) ?: false
    }

    private fun onDismiss(popupWindow: PopupWindow?) {
        if(popupWindow == this.popupWindow) {
            this.popupWindow = null
        }
    }

    private fun calculateSize() : Size{
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        return Size(measuredWidth, measuredHeight)
    }



    fun setSelectedItem(itemObject : Any?, itemText : CharSequence) {
        text = itemText
        selectedItemObject = itemObject
    }


    /**
     * Show a [PopupWindow]
     *
     * Set a custom [OnClickListener] if you want to call [showMenu] manually
     */
    fun showMenu(): PopupWindow {
        val menuItems = itemObjects.map {
                itemObject ->
            PopupMenuHelper.MenuItem(itemObject, itemObject.second)
        }
        return PopupMenuHelper.createAndShow(anchorView = this, popupPosition = PopupMenuHelper.PopupPosition.FitScreenVerticalOfAnchor, menuItems = menuItems) {
            menuItem ->
            onMenuItemClick(menuItem = menuItem)
        }.apply {
            setOnDismissListener {
                onDismiss(this)
            }
        }
    }
}


