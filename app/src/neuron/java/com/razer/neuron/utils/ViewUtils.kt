package com.razer.neuron.utils

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import com.razer.neuron.RnApp
import com.razer.neuron.common.logWarningOnError
import com.razer.neuron.extensions.dpToPx
import com.razer.neuron.extensions.windowManager
import kotlin.jvm.Throws
import kotlin.math.roundToInt

fun getStringExt(@StringRes id: Int): String {
    return RnApp.appContext.getString(id)
}


fun ViewGroup.flattenDescendants(): List<View> = mutableListOf<View>().apply {
    for (i in 0 until childCount) {
        val view = getChildAt(i)
        add(view)
        (view as? ViewGroup)?.let {
            addAll(it.flattenDescendants())
        }
    }
}

/**
 * return this [View]'s parent or grand parent, grand grant parent etc...
 *
 * First element is the view's immediate parent
 */
fun View.ancestors(): List<ViewParent> {
    val ancestors = mutableListOf<ViewParent>()
    var parent = parent
    while (parent != null) {
        ancestors.add(parent)
        parent = parent.parent
    }
    return ancestors
}

/**
 * true, if [this] ([ViewGroup]) is [v]'s parent or grand parent, grand grant parent etc...
 */
fun View.isAncestorOf(v: View?): Boolean {
    v ?: return false
    var parent = v.parent
    while (parent != null) {
        if (parent == this) {
            return true
        } else {
            parent = parent.parent
        }
    }
    return false
}


/**
 * true, if [this] ([ViewGroup]) is [v]'s parent or grand parent, grand grant parent etc...
 * that evaluates to true for [predicate]
 */
fun View.firstAncestorOrNull(predicate: (ViewParent) -> Boolean): View? {
    var parent = this.parent
    while (parent != null) {
        if (predicate(parent)) {
            return parent as? View
        } else {
            parent = parent.parent
        }
    }
    return null
}


/**
 * return [View] of ths [ViewGroup] where it has a child or grand child, grand grant child etc...
 * that evaluates to true for [predicate]
 */
fun ViewGroup.firstDescendantOrNull(predicate: (View) -> Boolean): View? {
    return this.children.toList().firstDescendantOrNull(predicate)
}


/**
 * true, if [this] list of [View] has a child or grand child, grand grant child etc...
 * that evaluates to true for [predicate]
 */
fun List<View>.firstDescendantOrNull(predicate: (View) -> Boolean): View? {
    val children = this
    children.forEach { v ->
        if (predicate(v)) {
            return v
        } else if (v is ViewGroup) {
            val matchedView = v.firstDescendantOrNull(predicate)
            if (matchedView != null) {
                return matchedView
            }
        }
    }
    return null
}


/**
 * true, if [this] ([ViewGroup]) is [v]'s parent or grand parent, grand grant parent etc...
 *
 * Will check in the following order
 * - For all [View.isDPadFocusable]
 * - if it is a [ViewGroup] AND not [ViewGroup.isBlockDescendantsFocus], then find the first child that is [View.isDPadFocusable]
 *
 */
fun View.findFirstFocusableChild(): View? {
    return when {
        this.isDPadFocusable() -> this
        this is ViewGroup && !this.isBlockDescendantsFocus() -> findFirstFocusable(this.children)
        else -> null
    }
}

/**
 * First [View] in [children] that is [isDPadFocusable] or has a descendant that is [isDPadFocusable]
 */
fun findFirstFocusable(children: Sequence<View>): View? {
    children.forEach { child ->
        if (child.isDPadFocusable()) {
            return child
        } else if (child is ViewGroup && !child.isBlockDescendantsFocus()) {
            findFirstFocusable(child.children)?.let { return it }
        }
    }
    return null
}

fun View.findLastFocusableChild(): View? {
    return when {
        this.isDPadFocusable() -> this
        this is ViewGroup && !this.isBlockDescendantsFocus() -> findLastFocusable(this.children)
        else -> null
    }
}

fun findLastFocusable(children: Sequence<View>): View? {
    children.toMutableList().reversed().forEach { child ->
        if (child.isDPadFocusable()) {
            return child
        } else if (child is ViewGroup && !child.isBlockDescendantsFocus()) {
            findLastFocusable(child.children)?.let { return it }
        }
    }
    return null
}

/**
 * -1 if it is current focus is not in a tile
 */
fun RecyclerView.getChildPosFromDescendant(descendantView: View): Int {
    return children.indexOfFirst { it.isAncestorOf(descendantView) }
}


/**
 * TODO: Design/product team might want certain View to be isClickable but not focusable
 */
fun View.isDPadFocusable(): Boolean {
    return (isClickable && isFocusable && isShown)
}


/**
 * A programmatic way to check if this [View]/[ViewGroup] children are not [isDPadFocusable]
 */
fun ViewGroup.isBlockDescendantsFocus(): Boolean {
    return this.descendantFocusability == ViewGroup.FOCUS_BLOCK_DESCENDANTS
}

/**
 * A programmatic way to specify that this [View]/[ViewGroup] children are not [isDPadFocusable]
 */
fun ViewGroup.setBlockDescendantsFocus(block: Boolean) {
    this.descendantFocusability =
        if (block) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_BEFORE_DESCENDANTS
}


fun View.notFocusable() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusable = View.FOCUSABLE
    }
}

fun View.focusable() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusable = View.NOT_FOCUSABLE
    }
}

fun View.autoFocusable() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusable = View.FOCUSABLE_AUTO
    }
}


fun Int.focusDirectionToString() = when (this) {
    View.FOCUS_UP -> "FOCUS_UP"
    View.FOCUS_DOWN -> "FOCUS_DOWN"
    View.FOCUS_LEFT -> "FOCUS_LEFT"
    View.FOCUS_RIGHT -> "FOCUS_RIGHT"
    View.FOCUS_BACKWARD -> "FOCUS_BACKWARD"
    else -> "FOCUS_$this"
}


fun KeyEvent.isKeyCodeEnter() = when (keyCode) {
    KeyEvent.KEYCODE_A,
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_DPAD_CENTER -> true
    else -> false
}


/**
 * Get actual windows display size
 */
fun Context.getWindowSize(): Size {
    val point = Point()
    with(windowManager()) {
        if (isAboveOrEqual(API_LEVEL31)) {
            val windowMetrics = currentWindowMetrics
            point.x = windowMetrics.bounds.width()
            point.y = windowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            defaultDisplay?.getRealSize(point)
        }
    }
    return Size(point.x, point.y)
}



/**
 */
fun View.doOnLayoutOnce(action: (View) -> Unit = {}) = doOnLayout(action)

/**
 * This is used to vertically center a view inside [ScrollView] or just let it scroll (l.e. top)
 */
@Throws(IllegalArgumentException::class)
fun ViewGroup.setGravityForCenterOrVerticalScroll() = runCatching {
    val parentView = parent as? View ?: error("View has no parent")
    require(height > 0) { "View has not been layout yet" }
    require(parentView.height > 0) { "Parent has not been layout yet" }
    val layoutParams = layoutParams
    check(layoutParams is FrameLayout.LayoutParams) { "View's parent must be subclass of FrameLayout (e.g. scrollview)" }
    layoutParams.gravity =
        if (height > parentView.height) (Gravity.TOP or Gravity.CENTER_HORIZONTAL) else Gravity.CENTER
}.logWarningOnError()