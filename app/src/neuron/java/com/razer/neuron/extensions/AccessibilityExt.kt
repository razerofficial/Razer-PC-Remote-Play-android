package com.razer.neuron.extensions

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.CaptioningManager
import android.widget.CompoundButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.razer.neuron.utils.ANDROID_10
import com.razer.neuron.utils.ANDROID_12
import com.razer.neuron.utils.API_LEVEL33
import com.razer.neuron.utils.isAboveOrEqual
import timber.log.Timber

/**
 * Get [AccessibilityManager]
 */
fun Context.accessibilityManager() = applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

/**
 * Get [CaptioningManager]
 */
fun Context.captioningManager() = applicationContext.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager

/**
 * @return true, if "Use live caption" is currently enabled
 */
fun Context.isSystemAudioCaptioningEnabled() = (captioningManager()?.run {
    if(isAboveOrEqual(API_LEVEL33)) this.isSystemAudioCaptioningEnabled else null
}) ?: false



fun Context.isTalkbackEnabled() = accessibilityManager()?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)?.isNotEmpty() == true
fun View.requestAccessibilityFocus() = sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)



/**
 * Override [View.getContentDescription] when the switch is clicked.
 *
 * Otherwise it will always say "on" or "off"
 *
 * But for cases when we have multiple "CompoundButton" on a page, we will get
 * "Multiple items have the same description" warning from Google accessibility scanner.
 *
 * So we need this function to override
 */
fun CompoundButton.overrideContentDescriptionForAccessibility(contentDescription: (Boolean) -> String) {
    val actualSwitch = this
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {

        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info) // Call super first

            // 1. Set the role of the container to be a switch
            info.className = CompoundButton::class.java.name // Announce as a Switch
            info.isCheckable = true
            info.isChecked = actualSwitch.isChecked // Reflect the actual switch state

            // 2. Set the custom TalkBack text
            info.contentDescription = contentDescription(actualSwitch.isChecked)

            // 3. Add the click action (important since the row is clickable)
            info.isClickable = true // Make sure the node is clickable for TalkBack
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK) // Explicitly add click action
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            if (super.performAccessibilityAction(host, action, args)) {
                return true // If default behavior handles it, return true
            }
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                actualSwitch.toggle() // Perform the toggle action
                // Manually send an accessibility event to update TalkBack's announcement
                host.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                return true
            }
            return false
        }
    })
}



/**
 * Override [View.getContentDescription] so that we can define it on click rather than view inflate
 */
fun View.overrideAccessibilityNodeInfo(callback: (AccessibilityNodeInfoCompat) -> Unit) {
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info) // Call super first
            callback(info)
        }
    })
}