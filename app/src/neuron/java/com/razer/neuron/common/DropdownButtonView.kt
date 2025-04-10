package com.razer.neuron.common

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePaddingRelative
import com.limelight.R
import com.limelight.databinding.RnDropdownButtonViewBinding
import com.razer.neuron.extensions.dimenResToPx
import kotlin.math.roundToInt

/**
 * A implementation of a button with a [PopupMenu] dropdown menu, matching the design of dropdown
 * button used by Google Pixel.
 */
class DropdownButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), PopupMenu.OnMenuItemClickListener,
    PopupMenu.OnDismissListener {
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

    private var currentPopupMenu : PopupMenu? = null

    /**
     * Set all available item for this dropdown
     */
    var itemObjects = listOf<Pair<Any, CharSequence>>()

    /**
     * True if [PopupMenu] is not dismissed
     */
    val isPopupMenuShowing get() = currentPopupMenu != null

    private var selectedItemObject : Any? = null

    init {
        setBackgroundResource(R.drawable.bg_transparent)
    }



    override fun onMenuItemClick(item: MenuItem?): Boolean {
        item ?: return false
        return item.findItemObjectByTitle()?.let {
            (itemObject, text) ->
            setSelectedItem(itemObject, text)
            onItemClickedListener?.invoke(itemObject) ?: false
        } ?: false
    }

    override fun onDismiss(menu: PopupMenu?) {
        if(menu == currentPopupMenu) {
            currentPopupMenu = null
        }
    }

    private fun calculateSize() : Size{
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        return Size(measuredWidth, measuredHeight)
    }

    private fun MenuItem.findItemObjectByTitle() = itemObjects.firstOrNull { title?.toString() == it.second.toString() }

    private fun getItemObject(menuItem : MenuItem) = menuItem.findItemObjectByTitle()?.first

    private fun getItemText(menuItem : MenuItem) = menuItem.findItemObjectByTitle()?.second

    fun setSelectedItem(itemObject : Any, itemText : CharSequence) {
        text = itemText
        selectedItemObject = itemObject
    }


    /**
     * Show a [PopupMenu]
     *
     * Set a custom [OnClickListener] if you want to call [showMenu] manually
     */
    fun showMenu(gravity: Int = Gravity.RIGHT): PopupMenu {
        return PopupMenu(context, this, gravity).apply {
            currentPopupMenu = this
            itemObjects.forEach { (_, itemText) ->
                menu.add(itemText)
            }
            showAndAddListeners()
        }
    }

    private fun PopupMenu.showAndAddListeners() {
        setOnMenuItemClickListener(this@DropdownButtonView)
        show()
        /// add a bit of margin to popup menu
        if(listOf(dropdownOffsetStartPx, dropdownOffsetTopPx, dropdownOffsetEndPx, dropdownOffsetBottomPx).any { it != null }) {
            post {
                updatePaddingRelative(
                    start = dropdownOffsetStartPx ?: paddingStart,
                    top = dropdownOffsetTopPx ?: paddingTop,
                    end = dropdownOffsetEndPx ?: paddingEnd,
                    bottom = dropdownOffsetBottomPx ?: paddingBottom
                )
            }
        }
    }

    var dropdownOffsetStartPx: Int? = null
    var dropdownOffsetTopPx: Int? = dimenResToPx(R.dimen.margin_1x).roundToInt()
    var dropdownOffsetEndPx: Int? = null
    var dropdownOffsetBottomPx: Int? = dimenResToPx(R.dimen.margin_1x).roundToInt()
}


