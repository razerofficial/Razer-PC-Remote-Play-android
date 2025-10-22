package com.razer.neuron.common

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.limelight.R
import com.razer.neuron.extensions.setForegroundResource
import com.razer.neuron.utils.ancestors
import com.razer.neuron.utils.getStringExt


/**
 * true if a [ViewGroup] has an [R.id.pill_background]
 */
fun ViewGroup.hasDirectPillBackground() =
    children.any { it.id == R.id.pill_background }


/**
 * Find the [View] used by in [PillGroup] (if any)
 */
fun ViewGroup.pillBackground(): View? = if(this.id == R.id.pill_background) this else findViewById(R.id.pill_background)

/**
 * For cases when your [ViewBinding] have included [R.layout.m3_ex_pill_background_layout]
 */
fun ViewBinding.pillBackground(): View? = (root as? ViewGroup)?.pillBackground()


typealias PillGroup = List<View>
/**
 * For case when a [View] or its ancestors are not shown because of view position or if the visibility
 * issue state is just temporary
 *
 * This doesn't work on [androidx.fragment.app.FragmentContainerView], for some reason it reset [View.getTag]
 */
private fun View.isSkipPillVisibilityCheck() = tag == getStringExt(R.string.skip_pill_visibility_check)

private fun View.isVisibleForPillBackground() = (isVisible || isSkipPillVisibilityCheck() || this is androidx.fragment.app.FragmentContainerView)

/**
 * If this is true then this view's [pillBackground] should [updateCorners]
 */
private fun View.isPillVisible(): Boolean {
    return isShown || (isVisibleForPillBackground() && ancestors().all { (it as? View)?.isVisibleForPillBackground() == true })
}

/**
 * Calls [applyPillCorners] for all items in [PillGroup]
 */
fun PillGroup.updateCorners() {
    applyPillCorners(*toTypedArray())
}

/**
 * Calls [View.updateCorners] for all [items]
 */
private fun applyPillCorners(vararg items: View) {
    val shownItems = items.filter { it.isPillVisible() }
    shownItems.forEachIndexed { index, view ->
        (view as? ViewGroup)?.pillBackground()?.updateCorners(index, shownItems.size)
    }
}

/**
 * Update all 4 corners of [View] based on [position]
 */
fun View.updateCorners(position: Int, familySize: Int) {
    val isFirst = position == 0
    val isLast = position == (familySize - 1)
    updateCorners(isFirst = isFirst, isLast = isLast)
}

/**
 * Update all 4 corners of [View]
 */
fun View.updateCorners(isFirst: Boolean, isLast: Boolean) {
    if(isFirst) {
        if(isLast) setSinglePillCorners() else setTopPillCorners()
    } else {
        if(isLast) setBottomPillCorners() else setMidPillCorners()
    }
}

fun View.setSinglePillCorners() {
    setForegroundResource(R.drawable.m3_ex_pill_single_ripple)
    setBackgroundResource(R.drawable.m3_ex_pill_single_background)
}

fun View.setTopPillCorners() {
    setForegroundResource(R.drawable.m3_ex_pill_top_ripple)
    setBackgroundResource(R.drawable.m3_ex_pill_top_background)
}

fun View.setBottomPillCorners() {
    setForegroundResource(R.drawable.m3_ex_pill_bottom_ripple)
    setBackgroundResource(R.drawable.m3_ex_pill_bottom_background)
}

fun View.setMidPillCorners() {
    setForegroundResource(R.drawable.m3_ex_pill_mid_ripple)
    setBackgroundResource(R.drawable.m3_ex_pill_mid_background)
}
