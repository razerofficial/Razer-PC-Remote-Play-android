package com.razer.neuron.exceptions

import android.graphics.Point
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.limelight.R
import com.limelight.databinding.RnM3PopupWindowBinding
import com.limelight.databinding.RnM3PopupWindowMenuItemBinding
import com.razer.neuron.RnApp
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.getScreenResolution
import kotlin.math.roundToInt


object PopupMenuHelper {
    /**
     * This is the general space between the [PopupWindow] and the anchor view
     */
    private val anchorGapSpacingPx = dimenResToPx(R.dimen.margin_1x).roundToInt()

    /**
     * See padding of [R.drawable.bg_dropdown_button]
     */
    private val popupWindowBackgroundVerticalPaddingPx =
        dimenResToPx(R.dimen.margin_2x).roundToInt()

    class PopupProperties(
        val offsetX: Int,
        val offsetY: Int,
        val gravity: Int,
        val animationStyle: Int
    )

    sealed class PopupPosition {

        abstract fun getPopupProperties(
            anchorGlobalX: Int,
            anchorGlobalY: Int,
            anchorWidth: Int,
            anchorHeight: Int,
            popupWindowWidth: Int,
            popupWindowHeight: Int
        ): PopupProperties

        data object FitScreenVerticalOfAnchor : PopupPosition() {
            override fun getPopupProperties(
                anchorGlobalX: Int,
                anchorGlobalY: Int,
                anchorWidth: Int,
                anchorHeight: Int,
                popupWindowWidth: Int,
                popupWindowHeight: Int
            ): PopupProperties {
                val useDropUp =
                    (anchorGlobalY + anchorHeight + popupWindowHeight + anchorGapSpacingPx) > RnApp.appContext.getScreenResolution().y
                val popupProp = if (useDropUp) BottomToTopOfAnchor else TopToBottomOfAnchor
                return popupProp.getPopupProperties(
                    anchorGlobalX = anchorGlobalX,
                    anchorGlobalY = anchorGlobalY,
                    anchorWidth = anchorWidth,
                    anchorHeight = anchorHeight,
                    popupWindowWidth = popupWindowWidth,
                    popupWindowHeight = popupWindowHeight
                )
            }
        }

        /**
         * Drop up
         */
        data object BottomToTopOfAnchor : PopupPosition() {

            override fun getPopupProperties(
                anchorGlobalX: Int,
                anchorGlobalY: Int,
                anchorWidth: Int,
                anchorHeight: Int,
                popupWindowWidth: Int,
                popupWindowHeight: Int
            ): PopupProperties {
                return PopupProperties(
                    offsetX = anchorWidth - popupWindowWidth,
                    offsetY = -1 * (popupWindowHeight + anchorGapSpacingPx),
                    gravity = Gravity.TOP or Gravity.START,
                    animationStyle = R.style.RnPopupWindowAnimation_PushUp
                )
            }
        }

        /**
         * Drop down
         */
        data object TopToBottomOfAnchor : PopupPosition() {

            override fun getPopupProperties(
                anchorGlobalX: Int,
                anchorGlobalY: Int,
                anchorWidth: Int,
                anchorHeight: Int,
                popupWindowWidth: Int,
                popupWindowHeight: Int
            ): PopupProperties {
                return PopupProperties(
                    offsetX = anchorWidth - popupWindowWidth,
                    offsetY = anchorHeight + anchorGapSpacingPx,
                    gravity = Gravity.TOP or Gravity.START,
                    animationStyle = R.style.RnPopupWindowAnimation_DropDown
                )
            }

        }

        data object EndToStartOfAnchorCenterVertical : PopupPosition() {

            override fun getPopupProperties(
                anchorGlobalX: Int,
                anchorGlobalY: Int,
                anchorWidth: Int,
                anchorHeight: Int,
                popupWindowWidth: Int,
                popupWindowHeight: Int
            ): PopupProperties {
                return PopupProperties(
                    offsetX = -1 * popupWindowWidth - anchorGapSpacingPx,
                    offsetY = -1 * (popupWindowHeight - anchorHeight) / 2,
                    gravity = Gravity.TOP or Gravity.START,
                    animationStyle = R.style.RnPopupWindowAnimation_DropDownFromEnd
                )
            }

        }

        data object EndTopToStartTopOfAnchor : PopupPosition() {

            override fun getPopupProperties(
                anchorGlobalX: Int,
                anchorGlobalY: Int,
                anchorWidth: Int,
                anchorHeight: Int,
                popupWindowWidth: Int,
                popupWindowHeight: Int
            ): PopupProperties {
                return PopupProperties(
                    offsetX = -1 * popupWindowWidth - anchorGapSpacingPx,
                    offsetY = 0,
                    gravity = Gravity.TOP or Gravity.START,
                    animationStyle = R.style.RnPopupWindowAnimation_DropDownFromEnd
                )
            }

        }

    }

    class MenuItem<T>(val item: T, val text: CharSequence)


    fun <T> createAndShow(
        anchorView: View,
        popupPosition: PopupPosition,
        menuItems: List<MenuItem<T>>,
        onMenuItemClicked: (MenuItem<T>) -> Boolean
    ): PopupWindow {
        val popupWindowWidth = dimenResToPx(R.dimen.m3_popup_window_menu_item_width).roundToInt()

        val estimatedItemHeight = with(
            LayoutInflater.from(anchorView.context).inflate(
                R.layout.rn_m3_popup_window_menu_item,
                anchorView.parent as? ViewGroup,
                false
            )
        ) {
            if (this is TextView) {
                text = menuItems.first().text
                measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                measuredHeight
            } else {
                dimenResToPx(R.dimen.m3_popup_window_menu_item_height_estimated).roundToInt()
            }
        }

        val menuItemsHeight = estimatedItemHeight * menuItems.size
        val popupWindowHeight = menuItemsHeight + 2 * popupWindowBackgroundVerticalPaddingPx

        val context = anchorView.context
        val inflater = LayoutInflater.from(anchorView.context)
        val binding = RnM3PopupWindowBinding.inflate(inflater, null, false)
        val p = Rect().apply { anchorView.getGlobalVisibleRect(this) }.run { Point(left, top) }
        val popupProp = popupPosition.getPopupProperties(
            p.x,
            p.y,
            anchorView.width,
            anchorView.height,
            popupWindowWidth,
            popupWindowHeight
        )
        p.x += popupProp.offsetX
        p.y += popupProp.offsetY

        return PopupWindow(context).apply {
            contentView = binding.root
            elevation = dimenResToPx(R.dimen.margin_4x)
            width = LinearLayout.LayoutParams.WRAP_CONTENT
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            isFocusable = true
            setBackgroundDrawable(null)
            animationStyle = popupProp.animationStyle
            binding.llMenuItems.removeAllViews()
            menuItems.forEachIndexed { i, menuItem ->
                binding.llMenuItems.addView(
                    RnM3PopupWindowMenuItemBinding.inflate(
                        inflater,
                        null,
                        false
                    ).run {
                        val textView = root
                        textView.text = menuItem.text
                        textView.setOnClickListener {
                            if (onMenuItemClicked(menuItem)) {
                                dismissSafely()
                            }
                        }
                        if (i == 0) {
                            textView.isFocusedByDefault = true
                        }
                        textView
                    })
            }
            showAtLocation(binding.root, popupProp.gravity, p.x, p.y)
        }
    }
}


fun PopupWindow?.dismissSafely() = runCatching {
    this?.dismiss()
}



