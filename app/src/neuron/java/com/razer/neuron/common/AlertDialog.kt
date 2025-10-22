package com.razer.neuron.common

import android.app.Activity
import android.content.DialogInterface.OnKeyListener
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder


/**
 * @param onSelected return true if you want to dismiss dialog
 */
fun <T : Enum<*>> Activity.showSingleChoiceItemsDialog(
    @StringRes titleId: Int,
    @StringRes messageId: Int?,
    onSelected: (option: T) -> Boolean,
    nameResList: List<Int>,
    options: List<T>,
    defaultOption: T,
    isCancelable: Boolean,
    positiveButton: Pair<String, (T) -> Unit>? = null,
    negativeButton: Pair<String, () -> Unit>? = null,
): AlertDialog {
    return showSingleChoiceItemsDialog(
        title = getString(titleId),
        message = messageId?.let { getString(it) },
        onSelected = onSelected,
        displayName = {
            (nameResList.getOrNull(options.indexOf(it))?.let { stringRes -> getString(stringRes) }) ?: it.toString()
        },
        options = options,
        defaultOption = defaultOption,
        isCancelable = isCancelable,
        positiveButton = positiveButton,
        negativeButton = negativeButton
    )
}

/**
 * @param onSelected return true if you want to dismiss dialog
 */
fun <T> Activity.showSingleChoiceItemsDialog(
    title: CharSequence,
    message: CharSequence?,
    onSelected: (option: T) -> Boolean,
    displayName: (T) -> CharSequence,
    options: List<T>,
    defaultOption: T,
    isCancelable: Boolean,
    positiveButton: Pair<String, (T) -> Unit>? = null,
    negativeButton: Pair<String, () -> Unit>? = null,
): AlertDialog {
    val selectedIndex = options.indexOf(defaultOption)
    var selectedOption = defaultOption

    return MaterialAlertDialogBuilder(this, materialAlertDialogTheme())
        .setTitle(title)
        .setSingleChoiceItems(
            options.map { displayName(it) }.toTypedArray(),
            selectedIndex
        ) { dialog, which ->
            selectedOption = options[which]
            if (onSelected.invoke(selectedOption)) {
                dialog.dismiss()
            }
        }
        .apply {
            if (message != null) {
                setMessage(message)
            }
            if (positiveButton != null) {
                setPositiveButton(positiveButton.first) { dialog, _ ->
                    positiveButton.second.invoke(selectedOption)
                    dialog.dismiss()
                }
            }
            if (negativeButton != null) {
                setNegativeButton(negativeButton.first) { dialog, _ ->
                    negativeButton.second.invoke()
                    dialog.dismiss()
                }
            }
        }
        .setCancelableExt(isCancelable)
        .show()
}

/**
 * Calls [MaterialAlertDialogBuilder.setCancelable] mostly
 * but also allow [KeyEvent.KEYCODE_BUTTON_B] to immediately call [AlertDialog.cancel] the dialog without
 * requesting windows focus first.
 *
 * Calling [MaterialAlertDialogBuilder.setCancelable] with true still requires 2 controller
 * button presses to dismiss the dialog. First press to focus on the dialog window, and the second
 * one to press the button.
 *
 * @param isCancelable is the value for [MaterialAlertDialogBuilder.setCancelable]
 * @param onKeyListener because [setCancelableExt] uses [MaterialAlertDialogBuilder.setOnKeyListener] to listen for controller
 * key-event(and each [MaterialAlertDialogBuilder] only supports 1 key-listener), so if you need to use a
 * custom [OnKeyListener], then you can provide it here and [setCancelableExt] will wrap your [OnKeyListener] into its own.
 */
fun MaterialAlertDialogBuilder.setCancelableExt(
    isCancelable: Boolean,
    onKeyListener: OnKeyListener? = null
) = apply {
    setCancelable(isCancelable)
    if (isCancelable) {
        setOnKeyListener { dialog, keyCode, event ->
            val isButtonBClicked =
                keyCode == KeyEvent.KEYCODE_BUTTON_B && event.action == KeyEvent.ACTION_UP
            val result = onKeyListener?.onKey(dialog, keyCode, event)
            // not handled
            if (result == true) {
                true // consumed by the onKeyListener
            } else {
                if (isButtonBClicked) {
                    dialog.cancel()
                    true // consumed by the B press
                } else {
                    false // not consume by onKeyListener and not B press
                }
            }
        }
    }
}