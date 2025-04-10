package com.razer.neuron.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.internal.NavigationMenuItemView
import com.google.android.material.internal.NavigationMenuView
import com.google.android.material.navigation.NavigationView
import com.razer.neuron.common.debugToast
import com.razer.neuron.extensions.getViewFocusDirection
import com.razer.neuron.extensions.requestFocus
import com.razer.neuron.extensions.setOnlyMargin
import com.razer.neuron.utils.flattenDescendants
import com.razer.neuron.utils.isAncestorOf
import com.razer.neuron.utils.isKeyCodeEnter
import com.razer.neuron.utils.randomInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BAA-2220
 * This class extends [NavigationView] because by default Android will choose the next focus view
 * based on the closest focusable view on the screen at the focus direction.
 *
 * However, if [NavigationBehavior.NavigateOnFocus] is used then choosing the closest focusable
 * view can (and often) forces a change in "checked" item also (since we change "checked" on focus) which will
 * lead to accidental navigation.
 *
 * This class will attempt to override the [requestChildFocus] function and call [NavigationMenuItemView.requestFocus]
 * on the the item where [NavigationMenuItemView.isChecked] is true (only under specific conditions)
 */
open class RnNavigationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NavigationView(context, attrs, defStyleAttr) {

    /**
     * If true, then by default the currently checked item where
     * [NavigationMenuItemView.isChecked] will be focused
     */
    var isRefocusCheckedItem = false

    /**
     * Get all [NavigationMenuItemView] that matches [predicate] (look through sub-menu also)
     */
    @SuppressLint("RestrictedApi")
    fun navigationMenuItemViews(predicate: (NavigationMenuItemView) -> Boolean = { it.isFocusable }) =
        flattenDescendants()
            .filterIsInstance<NavigationMenuItemView>()
            .filter(predicate)

    private fun logDebug(tag: String, line: String) =
        Unit //Timber.v("RnNavigationView: $tag $line")

    override fun requestChildFocus(child: View?, focused: View?) {
        val checkedItem =
            navigationMenuItemViews { it.isCheckable() && it.isFocusable && it.isChecked() }.firstOrNull()
        if (isRefocusCheckedItem) requestChildFocusByRefocusCheckedItem(
            child,
            focused,
            checkedItem
        ) else super.requestChildFocus(child, focused)
    }

    /**
     * When sub-menu is used, [NavigationView] will block parent view from getting focus (UP direction only)
     */
    private var blockedFocusableForeignView: View? = null
    private var blockedFocusableForeignViewCount = 0

    /**
     *  If next focusable view is not inside this [NavigationView], then we should force [NavigationView]
     *  to give up focus by calling [View.requestFocus].
     *
     *  This is a bug with [NavigationView] when [android.view.Menu.addSubMenu] is used BAA-2400
     *
     *  So if it is happens more than 2 times, we will force that foreign view to be focused
     */
    private fun unblockFocusabilityOnForeignView(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        val focusDirection = event.getViewFocusDirection() ?: return
        if (focusDirection != View.FOCUS_UP) return
        val v = focusSearch(focusDirection)?.takeIf { !this@RnNavigationView.isAncestorOf(it) }
        if (v != null) {
            if (v == blockedFocusableForeignView) {
                blockedFocusableForeignViewCount++
            } else {
                blockedFocusableForeignView = v
                blockedFocusableForeignViewCount = 0
            }

            if (blockedFocusableForeignViewCount > 1) {
                v.requestFocus()
                blockedFocusableForeignView = null
                blockedFocusableForeignViewCount = 0
            }
        } else {
            blockedFocusableForeignView = null
            blockedFocusableForeignViewCount = 0
        }
    }


    /**
     * There is a bug where focusing a currently "checked" item will focus the first
     * [NavigationMenuItemView], so the best way to fix this without complicating the
     * change for BAA-2220 changes, is to just prevent enter key from being processed.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        event ?: return false
        unblockFocusabilityOnForeignView(event)
        val isFocusedAlreadyChecked =
            navigationMenuItemViews { it.isFocused }.firstOrNull()?.isChecked() == true
        return if (isFocusedAlreadyChecked && event?.isKeyCodeEnter() == true) true else super.dispatchKeyEvent(
            event
        )
    }

    @SuppressLint("RestrictedApi")
    private fun requestChildFocusByRefocusCheckedItem(
        child: View?,
        focused: View?,
        checkedItem: NavigationMenuItemView?
    ) {
        val tag = "requestChildFocusByRefocusCheckedItem(${randomInt(0, 100)})"
        logDebug(tag, "${"=".repeat(20)}")
        val hadFocus = hasFocus()
        logDebug(tag, "hadFocus=${hadFocus}")
        logDebug(tag, "child=${child}(${child?.hasFocus()})")
        logDebug(tag, "focused=${focused}(${focused?.hasFocus()})")

        var wasCheckItemFocused = false
        super.requestChildFocus(child, focused)
        if (!hadFocus
            && child is NavigationMenuView
            && focused is NavigationMenuItemView
            && checkedItem != null
            && checkedItem != focused
        ) {
            handler.post {
                if (checkedItem.requestFocus()) {
                    logDebug(
                        tag,
                        "currentChecked=${checkedItem}(${checkedItem.hasFocus()})"
                    )
                    wasCheckItemFocused = true
                }
            }
        }
        logDebug(tag, "wasCheckItemFocused=${wasCheckItemFocused}")
    }
}

@SuppressLint("RestrictedApi")
fun NavigationMenuItemView.isCheckable() = itemData.isCheckable

@SuppressLint("RestrictedApi")
fun NavigationMenuItemView.isChecked() = itemData.isChecked


/**
 * See BAA-2220
 */
enum class NavigationBehavior {
    /**
     * Call [RnSettingsViewModel.navigationTo] when [NavigationMenuItemView] is focused
     */
    NavigateOnFocus,

    /**
     * Call [RnSettingsViewModel.navigationTo] when
     * [NavigationView.OnNavigationItemSelectedListener.onNavigationItemSelected] is called
     */
    NavigateOnSelect;

    companion object {
        val default = NavigateOnFocus
    }
}

/**
 * Fixing issue in BAA-2220
 */
class RnNavigationView2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RnNavigationView(context, attrs, defStyleAttr) {

    private val navigationHandler = Handler(Looper.getMainLooper())

    private var globalFocusJob: Job? = null

    private val lifecycleScope get() = (context as? AppCompatActivity)?.lifecycleScope

    private var navigationTo: ((Int) -> Unit)? = null



    /**
     * BAA-2220
     */
    var navigationBehavior: NavigationBehavior =
        NavigationBehavior.default // NavigationBehavior.NavigateOnFocus
        set(value) {
            field = value
            /**
             * This is needed, as the toggle [RnNavigationView.isRefocusCheckedItem] is made for
             * the behavior [NavigationBehavior.NavigateOnFocus] to avoid navigation change
             */
            isRefocusCheckedItem = (navigationBehavior == NavigationBehavior.NavigateOnFocus)
        }

    /**
     * See BAA-2220
     */
    @SuppressLint("RestrictedApi")
    val activityFocusChangedListener =
        OnGlobalFocusChangeListener { _, newView ->
            if (newView is NavigationMenuItemView) {
                // only the second focus is correct
                if (navigationBehavior == NavigationBehavior.NavigateOnFocus) {
                    val newViewItemId = newView.itemData.itemId
                    globalFocusJob?.cancel()
                    val lifecycleScope = lifecycleScope ?: run {
                        debugToast("Please call initNavigationBehavior first")
                        return@OnGlobalFocusChangeListener
                    }
                    globalFocusJob = lifecycleScope.launch {
                        delay(50)
                        /**
                         * isPerformNavigation is true because focus happens first
                         * and we want to navigate immediately
                         */
                        selectItem(newViewItemId)
                    }
                }
            }
        }


    /**
     * Should be called before using any of these functions.
     *
     * @param window is [Activity.window]
     * @param navigationTo a function to navigate when needed by the view, where the [Int] is [MenuItem.getItemId]
     */
    fun initNavigationBehavior(window: Window, navigationTo: (Int) -> Unit) {
        this.navigationTo = navigationTo
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener(
            activityFocusChangedListener
        )
        setNavigationItemSelectedListener {
            when (navigationBehavior) {
                NavigationBehavior.NavigateOnSelect -> {
                    navigationTo?.invoke(it.itemId)
                }

                NavigationBehavior.NavigateOnFocus -> {
                    /**
                     * This is true because only touch focus/select
                     * will call here.
                     */
                    if (checkedItem?.itemId != it.itemId) {
                        selectItem(it.itemId)
                    }
                }
            }
            true
        }
    }


    /**
     * See BAA-2220
     *
     * In com.google.android.material:material library:
     * - Version 1.11.0 or later, the dynamic color will not work with Samsung
     * - Version 1.10.0 or older, it has a bug where if you check a item (call [MenuItem.setChecked])
     *   it will focus on the first item of [NavigationView]. (fixed in 1.12.0+)
     *
     * This means that we can't have a version where dynamic color is supported by samsung AND
     * have this bug fixed by google, so we have to fix it ourselves with [NavigationMenuItemView.requestFocus]
     */
    @SuppressLint("RestrictedApi")
    fun selectItem(
        itemId: Int,
        requestFocus: Boolean = true,
        isPerformNavigation: Boolean = navigationBehavior == NavigationBehavior.NavigateOnFocus
    ) {
        setCheckedItem(itemId)
        if (isPerformNavigation) {
            navigationTo?.invoke(itemId)
        }

        if (requestFocus) {
            navigationHandler.post {
                flattenDescendants()
                    .filterIsInstance<NavigationMenuItemView>()
                    .firstOrNull { it.isFocusable && it.itemData.itemId == itemId }
                    ?.requestFocus(true)
            }
        }
    }


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
        val navView = this
        viewTreeObserver.runOnceOnGlobalLayout(navView) {
            val marginTopPx = overlappingTopView.height - topMarginOffsetPx
            navView.setOnlyMargin(top = marginTopPx.toInt())
            navView.requestLayout()
        }
    }

}


/**
 * BAA-2503
 */
fun ViewTreeObserver.runOnceOnGlobalLayout(view : View, runnable: Runnable) {
    var ranOnceAlready = false
    addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if(ranOnceAlready) return
            ranOnceAlready = true
            Timber.v("runOnceOnGlobalLayout: remove $this")
            // for some reason, this always fails
            kotlin.runCatching { removeOnGlobalLayoutListener(this) }
            Timber.v("runOnceOnGlobalLayout: Running ${runnable}")
            view.post { runCatching { runnable.run() } }
        }
    })
}

