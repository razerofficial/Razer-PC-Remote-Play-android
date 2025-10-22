package com.razer.neuron.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.ActionProvider
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.limelight.R
import com.limelight.databinding.RnNavigationView3Binding
import com.limelight.databinding.RnNavigationView3HeaderBinding
import com.limelight.databinding.RnNavigationView3MenuItemBinding
import com.razer.neuron.common.BaseDataAdapter
import com.razer.neuron.common.logWarningOnError
import com.razer.neuron.common.updateCorners
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.gone
import com.razer.neuron.common.pillBackground
import com.razer.neuron.extensions.getScreenResolution
import com.razer.neuron.extensions.getStringExt
import com.razer.neuron.extensions.height
import com.razer.neuron.extensions.overrideAccessibilityNodeInfo
import com.razer.neuron.extensions.visible
import com.razer.neuron.extensions.width
import kotlinx.coroutines.delay
import okhttp3.internal.toImmutableList
import timber.log.Timber
import java.util.Collections
import kotlin.math.abs
import kotlin.math.roundToInt


typealias RnNv3View = RnNavigationView3

class RnNavigationView3 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseNv3View(context, attrs, defStyleAttr) {
    private val binding = RnNavigationView3Binding.inflate(LayoutInflater.from(context), this)

    private val rv by lazy {
        binding.nv3Recyclerview.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = Nv3ItemAdapter()
            addItemDecoration(ItemDecoration())
        }
    }
    private inner class ItemDecoration : RecyclerView.ItemDecoration() {
        private val marginPx by lazy {
            dimenResToPx(R.dimen.margin_4x).roundToInt()
        }
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            val position = parent.getChildAdapterPosition(view)
            if (position == -1) return
            val currentItem = this@RnNavigationView3.adapter.getItemOrNull(position)
            val previousItem = this@RnNavigationView3.adapter.getItemOrNull(position - 1)
            val nextItem = this@RnNavigationView3.adapter.getItemOrNull(position + 1)

            when(currentItem) {
                is SubMenu -> Unit
                is MenuItem -> {
                    if(nextItem !is MenuItem) {
                        outRect.bottom = marginPx
                    }
                }
            }

            if(nextItem == null) {
                outRect.bottom = marginPx
            }
        }
    }

    private val linearLayoutManager get() = rv.layoutManager as LinearLayoutManager


    private val adapter get() = rv.adapter as Nv3ItemAdapter

    private var navigationTo: (Int) -> Unit = {}


    var navigationBehavior: NavigationBehavior = NavigationBehavior.NavigateOnSelect

    init {
        setBackgroundResource(R.drawable.bg_transparent)
        rv.setBackgroundResource(R.drawable.bg_transparent)
        isFocusable = false
        isClickable = false
    }

    fun initNavigationBehavior(window: Window, navigationTo: (Int) -> Unit) {
        this.navigationTo = navigationTo
    }

    private fun MenuItem.navigateTo() = navigationTo(itemId)

    @SuppressLint("RestrictedApi")
    fun selectItem(
        itemId: Int,
        requestFocus: Boolean = true,
        isPerformNavigation: Boolean = navigationBehavior == NavigationBehavior.NavigateOnFocus
    ) = runCatching {
        val flattenItems = flatten()
        val index = flattenItems.indexOfFirst { it is Nv3MenuItem && it.itemId == itemId }.takeIf { it > -1 } ?: error("itemId $itemId not found")
        val toBeSelectedMenuItem = flattenItems.getOrNull(index) as? MenuItem ?: error("index $index is not MenuItem")
        selectItem(toBeSelectedMenuItem)
        viewHandler.post {
            rv.smoothScrollToPosition(index)
            if(requestFocus) {
                rv.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
            }
            if(isPerformNavigation) {
                toBeSelectedMenuItem.navigateTo()
            }
        }
    }.logWarningOnError()

    /**
     * First item of [NavigationView] tends to leave a large top margin
     * Call this to offset it.
     *
     *
     * @param [topMarginOffsetPx], assuming [overlappingTopView] overlaps this [NavigationView]
     * what is the amount of pixel do you want to subtract from the first item so that the gap
     * between the 2 views are not so great. Bigger this is, the smaller the gap
     */
    fun adjustOverlappingTopMargin(viewTreeObserver : ViewTreeObserver, overlappingTopView: View, topMarginOffsetPx : Float) {
    }


    override fun notifyDataSetChanged(list : List<Any>) {
        adapter.setData(list)
        adapter.notifyDataSetChanged()
    }


    /**
     * Get the current adapter position of the focused view
     */
    fun getFocusItemPosition() : Int? {
        val focusedView = rv.findFocus()
        val focusedItemView = focusedView?.let {
            rv.findContainingItemView(it)
        }
        val focusedItemPosition = focusedItemView?.let { rv.getChildAdapterPosition(it) }
        return focusedItemPosition
    }

    /**
     * Set the item focus given an position from [getFocusItemPosition]
     */
    fun setFocusItemPosition(position : Int) : Boolean {
        if(position !in 0 until adapter.itemCount) {
            Timber.w("Position $position not in range itemCount=${adapter.itemCount}")
            return false
        }
        rv.scrollToPosition(position)
        val itemView = rv.findViewHolderForAdapterPosition(position)?.itemView
        return itemView?.requestFocus() == true
    }

    /**
     * Run [transaction] function while stopping any internal [notifyMenuChanged] call from completing
     * while it is running.
     *
     * Will call [notifyMenuChanged] once [transaction] finished
     *
     * @param transaction the function to call to manipulate [Menu]
     * @param onCompleted the end function to call after [notifyMenuChanged] is called
     *
     */
    suspend fun updateMenu(refocusDelay : Long = 250, transaction : suspend (Menu) -> Unit, onCompleted : (Menu) -> Unit = {}) {
        val focusPosition = getFocusItemPosition()
        try {
            isUpdating = true
            transaction(menu)
        } finally {
            isUpdating = false
            notifyMenuChanged()
            onCompleted(menu)
            delay(refocusDelay)
            focusPosition?.let { setFocusItemPosition(it) }
        }
    }


    internal inner class Nv3ItemAdapter : BaseDataAdapter<Any>() {

        internal fun getItemOrNull(index : Int) = data.getOrNull(index)

        override fun getItemId(position: Int): Long {
            return data.getOrNull(position)?.menuItem()?.itemId?.toLong() ?: 0L
        }

        override fun getItemViewType(position: Int) = when (val item = data.getOrNull(position)) {
            is Nv3SubMenu -> MENUITEM_TYPE_HEADER
            is Nv3MenuItem -> MENUITEM_TYPE_ITEM
            else -> error("Unsupported NavigationView type ${item?.javaClass?.simpleName}")
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): BaseDataViewHolder {
          return when(viewType) {
              MENUITEM_TYPE_HEADER -> HeaderViewHolder(RnNavigationView3HeaderBinding.inflate(LayoutInflater.from(context), parent, false))
              MENUITEM_TYPE_ITEM -> MenuItemViewHolder(RnNavigationView3MenuItemBinding.inflate(LayoutInflater.from(context), parent, false))
              else -> error("Unsupported NavigationView viewType $viewType")
          }
        }

        inner class HeaderViewHolder(private val binding : RnNavigationView3HeaderBinding) : BaseDataViewHolder(binding) {
            private val tvHeader by lazy { binding.tvHeader }
            override fun bind(data: Any, position: Int) {
                val subMenu = data as? Nv3SubMenu ?: return
                subMenu.menuItem.title?.let { tvHeader.text = it }
                tvHeader.gone()
            }
        }

        inner class MenuItemViewHolder(private val binding : RnNavigationView3MenuItemBinding) : BaseDataViewHolder(binding) {
            private val tvTitle by lazy { binding.tvMenuItemTitle }
            private val ivLarge by lazy { binding.ivMenuItemIconLarge }
            private val ivSmall by lazy { binding.ivMenuItemIconSmall }
            private val checkableLayout by lazy { binding.root }
            private val pillBackground get() = binding.pillBackground()

            override fun bind(data: Any, position: Int) {
                val menuItem = data as? Nv3MenuItem ?: return
                menuItem.title?.let {
                    title ->
                    tvTitle.text = title
                    checkableLayout.overrideAccessibilityNodeInfo {
                            info ->
                        val menuItems = copyItems().filterIsInstance<MenuItem>()
                        val positionText = menuItems.indexOf(menuItem).takeIf { it > -1 }?.let {
                            val x = it + 1
                            val y = menuItems.size
                            getStringExt(R.string.x_of_y, x, y)
                        }
                        info.contentDescription = "$title ${menuItem.getSubtitle() ?: ""} ${positionText ?: ""}"
                    }
                }
                menuItem.icon?.let {
                    ivLarge.visible()
                    ivSmall.visible()
                    ivSmall.setImageDrawable(it)
                } ?: run {
                    ivLarge.gone()
                    ivSmall.gone()
                }
                checkableLayout.isChecked = menuItem.isChecked
                updateTextFontWeight()
                checkableLayout.setOnClickListener {
                    selectItem(menuItem)
                    updateTextFontWeight()
                    menuItem.menuItemClickListener?.onMenuItemClick(menuItem)
                    menuItem.navigateTo()
                }
                updateCorners(position)
            }

            private fun updateTextFontWeight() = kotlin.runCatching {
                val fontWeight = if(checkableLayout.isChecked) 600 else 450
                tvTitle.typeface = Typeface.create(tvTitle.typeface, fontWeight, false)
            }

            private fun updateCorners(position: Int) {
                val isFirstInGroup = data.getOrNull(position - 1) !is MenuItem
                val isLastInGroup = data.getOrNull(position + 1) !is MenuItem
                pillBackground?.updateCorners(isFirst = isFirstInGroup, isLast = isLastInGroup)
            }

        }
    }


    companion object {
        const val MENUITEM_TYPE_HEADER = 0
        const val MENUITEM_TYPE_ITEM = 1
    }
}


abstract class BaseNv3View @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    protected val viewHandler = Handler(Looper.getMainLooper())

    /**
     * Update the view
     */
    abstract fun notifyDataSetChanged(list : List<Any>)

    open fun notifyDataSetChanged() {
        notifyDataSetChanged(flatten())
    }

    /**
     * If [isUpdating] is true, [notifyMenuChanged] will not be called fully
     */
    protected var isUpdating = false

    val menu: Nv3Menu = Nv3Menu()

    protected var lastSelectedItem : MenuItem? = null

    private fun MenuItem.isEqualOrVariant(that : MenuItem) = itemId == that.itemId || (this is Nv3MenuItem && that is Nv3MenuItem && that.getVariantId() != null && variantId == that.getVariantId())

    protected fun flatten() = mutableListOf<Any>().apply {
        for(any in menu.immutableMenuItems()) {
            when(any) {
                is Nv3SubMenu -> {
                    add(any)
                    addAll(any.items.filterIsInstance<Nv3MenuItem>())
                }
                is Nv3MenuItem -> {
                    add(any)
                }
            }
        }
    }.toList()

    fun selectItem(menuItem: MenuItem) {
        lastSelectedItem = menuItem
        notifyDataSetChanged()
    }

    protected fun unselectItem(menuItem: MenuItem) {
        if(lastSelectedItem?.itemId == menuItem.itemId) {
            lastSelectedItem = null
            notifyDataSetChanged()
        }
    }

    protected fun notifyMenuChanged() {
        // reselect the last selected, if none is selected (checked)
        val menuItems = flatten().filterIsInstance<Nv3MenuItem>()
        val isAllUnChecked = menuItems.all { (it as? MenuItem)?.isChecked != true }
        val lastSelectedItem = lastSelectedItem as? Nv3MenuItem
        if(isAllUnChecked) {
            if(lastSelectedItem != null) {
                val resumeSelectedMenuItem = menuItems.firstOrNull { it.isEqualOrVariant(lastSelectedItem) }
                resumeSelectedMenuItem?.isChecked = true
            }
        }
        if(!isUpdating) {
            viewHandler.removeCallbacksAndMessages(null)
            viewHandler.post {
                notifyDataSetChanged()
            }
        }
    }

    inner class Nv3MenuItem : android.view.MenuItem {
        internal var itemId: Int = 0
        internal var variantId: Int? = null
        internal var groupId: Int = 0
        internal var order: Int = 0
        private var title: CharSequence? = null
        private var subtitle: CharSequence? = null
        private var titleCondensed: CharSequence? = null
        private var icon: Drawable? = null
        private var isCheckable = true
        private var isEnabled = true
        private var isVisible = true
        private var subMenu: SubMenu? = null
        internal var menuItemClickListener: android.view.MenuItem.OnMenuItemClickListener? = null

        override fun getItemId() = itemId
        override fun getGroupId() = groupId
        override fun getOrder() = order

        fun getVariantId() = variantId

        fun setVariantId(variantId : Int?) {
            this.variantId = variantId
        }

        override fun toString() = title?.toString() ?: ""

        override fun setTitle(title: CharSequence?) = this.apply {
            this.title = title
            notifyMenuChanged()
        }
        fun setSubtitle(subtitle: CharSequence?) = this.apply {
            this.subtitle = subtitle
            notifyMenuChanged()
        }

        fun getSubtitle() = subtitle

        override fun setTitle(title: Int) = this.apply {
            this.title = resources.getString(title)
            notifyMenuChanged()
        }

        override fun getTitle() = title

        override fun setTitleCondensed(title: CharSequence?) = this.apply {
            this.titleCondensed = title
            notifyMenuChanged()
        }

        override fun getTitleCondensed() = titleCondensed

        override fun setIcon(icon: Drawable?) = this.apply {
            this.icon = icon
            notifyMenuChanged()
        }

        override fun setIcon(iconRes: Int) = this.apply {
            this.icon = ResourcesCompat.getDrawable(resources, iconRes, context.theme)
            notifyMenuChanged()
        }

        override fun getIcon() = icon

        override fun setIntent(intent: Intent?) = this

        override fun getIntent() = null

        override fun setShortcut(numericChar: Char, alphaChar: Char) = this

        override fun setNumericShortcut(numericChar: Char) = this

        override fun getNumericShortcut() = '0'

        override fun setAlphabeticShortcut(alphaChar: Char) = this

        override fun getAlphabeticShortcut() = 'a'

        override fun setCheckable(checkable: Boolean) = this.apply {
            this.isCheckable = checkable
            notifyMenuChanged()
        }

        override fun isCheckable() = isCheckable

        override fun setChecked(checked: Boolean) = this.apply {
            if(checked) {
                selectItem(this)
            } else {
                unselectItem(this)
            }
        }

        override fun isChecked() = this.itemId == lastSelectedItem?.itemId

        override fun setVisible(visible: Boolean) = this.apply {
            this.isVisible = visible
            notifyMenuChanged()
        }

        override fun isVisible() = isVisible

        override fun setEnabled(enabled: Boolean) = this.apply {
            this.isEnabled = enabled
            notifyMenuChanged()
        }

        override fun isEnabled() = isEnabled

        override fun hasSubMenu() = subMenu != null

        override fun getSubMenu() = subMenu

        override fun setOnMenuItemClickListener(menuItemClickListener: android.view.MenuItem.OnMenuItemClickListener?) =
            this.apply {
                this.menuItemClickListener = menuItemClickListener
                notifyMenuChanged()
            }

        override fun getMenuInfo() = null

        override fun setShowAsAction(actionEnum: Int) = Unit
        override fun setShowAsActionFlags(actionEnum: Int) = this
        override fun setActionView(view: View?) = this
        override fun setActionView(resId: Int) = this
        override fun getActionView() = null
        override fun setActionProvider(actionProvider: ActionProvider?) = this
        override fun getActionProvider() = null
        override fun expandActionView() = false
        override fun collapseActionView() = false
        override fun isActionViewExpanded() = false
        override fun setOnActionExpandListener(listener: android.view.MenuItem.OnActionExpandListener?) =
            this
    }

    internal fun Any.menuItem() : MenuItem? = when (this) {
        is MenuItem -> this
        is Nv3SubMenu -> this.item
        else -> null
    }

    open inner class Nv3Menu : Menu {
        internal var items = Collections.synchronizedList(mutableListOf<Any>())

        private fun createMenuItem() = Nv3MenuItem()

        private fun createSubMenu(menuItem: Nv3MenuItem) = Nv3SubMenu(menuItem)

        internal fun immutableMenuItems() = items.toImmutableList()

        override fun toString() = "(Menu${hashCode()}=[${items.joinToString()}])"

        override fun add(title: CharSequence?) = createMenuItem().apply {
            setTitle(title)
            items.add(this@apply)
            Timber.v("add: ${this@Nv3Menu}")
            notifyMenuChanged()
        }

        override fun add(titleRes: Int) = add(resources.getString(titleRes))

        override fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence?) =
            createMenuItem().apply {
                this.groupId = groupId
                this.itemId = itemId
                this.order = order
                setTitle(title)
                items.add(this@apply)
                Timber.v("add: ${this@Nv3Menu}")
                notifyMenuChanged()
            }

        override fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int) =
            createMenuItem().apply {
                this.groupId = groupId
                this.itemId = itemId
                this.order = order
                setTitle(resources.getString(titleRes))
                items.add(this@apply)
                Timber.v("add: ${this@Nv3Menu}")
                notifyMenuChanged()
            }

        override fun addSubMenu(title: CharSequence?) = run {
            val internalMenuItem = createMenuItem().apply {
                setTitle(title)
            }
            val subMenu = createSubMenu(internalMenuItem)
            subMenu.setHeaderTitle(title)
            items.add(subMenu)
            Timber.v("addSubMenu: ${this@Nv3Menu}")
            notifyMenuChanged()
            subMenu
        }

        override fun addSubMenu(titleRes: Int) = run {
            val internalMenuItem = createMenuItem().apply {
                setTitle(titleRes)
            }

            val subMenu = createSubMenu(internalMenuItem)
            subMenu.setHeaderTitle(titleRes)
            items.add(subMenu)
            Timber.v("addSubMenu: ${this@Nv3Menu}")
            notifyMenuChanged()
            subMenu
        }

        override fun addSubMenu(groupId: Int, itemId: Int, order: Int, title: CharSequence?) = run {
            val internalMenuItem = createMenuItem().apply {
                this@apply.groupId = groupId
                this@apply.itemId = itemId
                this@apply.order = order
                setTitle(title)
            }
            val subMenu = createSubMenu(internalMenuItem)
            subMenu.setHeaderTitle(title)
            items.add(subMenu)
            Timber.v("addSubMenu: ${this@Nv3Menu}")
            notifyMenuChanged()
            subMenu
        }

        override fun addSubMenu(groupId: Int, itemId: Int, order: Int, titleRes: Int) = run {
            val internalMenuItem = createMenuItem().apply {
                this@apply.groupId = groupId
                this@apply.itemId = itemId
                this@apply.order = order
                setTitle(titleRes)
            }
            val subMenu = createSubMenu(internalMenuItem)
            subMenu.setHeaderTitle(titleRes)
            items.add(subMenu)
            Timber.v("addSubMenu: ${this@Nv3Menu}")
            notifyMenuChanged()
            subMenu
        }

        override fun addIntentOptions(
            groupId: Int,
            itemId: Int,
            order: Int,
            caller: ComponentName?,
            specifics: Array<out Intent>?,
            intent: Intent?,
            flags: Int,
            outSpecificItems: Array<out android.view.MenuItem>?
        ) = 0

        override fun removeItem(id: Int) {
            items.removeAll { it.menuItem()?.itemId == id }
            notifyMenuChanged()
        }

        override fun removeGroup(groupId: Int) {
            items.removeAll { it.menuItem()?.groupId == groupId }
            notifyMenuChanged()
        }

        override fun clear() {
            items.clear()
            notifyMenuChanged()
        }

        override fun setGroupCheckable(group: Int, checkable: Boolean, exclusive: Boolean) {
            items.filter { it.menuItem()?.groupId == group }.forEach {
                it.menuItem()?.setCheckable(checkable)
            }
            notifyMenuChanged()
        }

        override fun setGroupVisible(group: Int, visible: Boolean) {
            items.filter { menuItem()?.groupId == group }.forEach {
                it.menuItem()?.setVisible(visible)
            }
            notifyMenuChanged()
        }

        override fun setGroupEnabled(group: Int, enabled: Boolean) {
            items.filter { menuItem()?.groupId == group }.forEach {
                it.menuItem()?.setEnabled(enabled)
            }
            notifyMenuChanged()
        }

        override fun hasVisibleItems() = items.any { it.menuItem()?.isVisible == true}

        override fun findItem(id: Int) = items.firstOrNull { it.menuItem()?.itemId == id }?.menuItem()

        override fun size() = items.size

        override fun getItem(index: Int) = items.getOrNull(index)?.menuItem()

        override fun close() = Unit

        override fun performShortcut(keyCode: Int, event: KeyEvent?, flags: Int) = false

        override fun isShortcutKey(keyCode: Int, event: KeyEvent?) = false

        override fun performIdentifierAction(id: Int, flags: Int) = false

        override fun setQwertyMode(isQwerty: Boolean) = Unit
    }


    inner class Nv3SubMenu(val menuItem: Nv3MenuItem) : Nv3Menu(), SubMenu {
        override fun getItem() = menuItem

        override fun toString() = "(${menuItem.title}=[${items.joinToString()}])"
        override fun setHeaderTitle(titleRes: Int) = this.apply {
            menuItem.setTitle(titleRes)
            notifyMenuChanged()
        }

        override fun setHeaderTitle(title: CharSequence?) = this.apply {
            menuItem.setTitle(title)
            notifyMenuChanged()
        }

        override fun setHeaderIcon(iconRes: Int) = this.apply {
            menuItem.setIcon(iconRes)
            notifyMenuChanged()
        }

        override fun setHeaderIcon(icon: Drawable?) = this.apply {
            menuItem.setIcon(icon)
            notifyMenuChanged()
        }

        override fun setHeaderView(view: View?) = this

        override fun clearHeader() = Unit

        override fun setIcon(iconRes: Int) = this.apply {
            menuItem.setIcon(iconRes)
            notifyMenuChanged()
        }

        override fun setIcon(icon: Drawable?) = this.apply {
            menuItem.setIcon(icon)
            notifyMenuChanged()
        }

        override fun addSubMenu(title: CharSequence?) = error("not supported")
        override fun addSubMenu(
            groupId: Int,
            itemId: Int,
            order: Int,
            title: CharSequence?
        ) = error("not supported")

        override fun addSubMenu(titleRes: Int) = error("not supported")
    }

}


