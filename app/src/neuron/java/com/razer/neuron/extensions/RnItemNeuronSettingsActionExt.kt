package com.razer.neuron.extensions

import android.app.Activity
import android.graphics.drawable.Drawable
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.limelight.R
import com.limelight.databinding.RnItemNeuronSettingsActionBinding
import com.razer.neuron.common.debugToast
import com.razer.neuron.common.showSingleChoiceItemsDialog
import timber.log.Timber

/**
 * A simple wrapper for [AlertDialog.dismissSafely] (any maybe other UI that needs to be remove if
 * the host fragment/activity is paused)
 */
fun interface Disposable {
    fun dispose()
}

/**
 * Given a list of [options], use can click on the view/row to select 1. This implementation will
 * show a [com.razer.neuron.common.DropdownButtonView] and allow user to pick on option from the
 * drop down.
 *
 * @param viewLifecycleOwner, used by [liveData]
 * @param title
 * @param options, the list of options to choose from
 * @param selectedItem a function to get the current selected item [T]
 * @param liveData a [LiveData] if the value of the select was change by the app (instead of the user)
 * @param displayName, convert [T] to [CharSequence] (for display purpose)
 * @param onUserSelectItem, when user selected a [T] from [AlertDialog]
 */
fun <T> RnItemNeuronSettingsActionBinding.setupSingleChoiceItemsDropdown(
    viewLifecycleOwner : LifecycleOwner,
    title : CharSequence,
    subtitle : CharSequence?,
    options : () -> List<T>,
    selectedItem : () -> T?,
    liveData: LiveData<T>,
    displayName : (T) -> CharSequence,
    onUserSelectItem: (T) -> Unit
) {
    with(this) {
        tvTitle.text = title
        subtitle?.let {
            tvSubtitle.text = it
            tvSubtitle.visible()
        } ?: run {
            tvSubtitle.gone()
        }
        ivActionStartIcon.gone()
        layoutDropdownMenu.visible()
        val ddbv = ddbvDropdownMenu
        ddbv.itemObjects = options().map { it as Any to displayName(it) }
        val initialValue = selectedItem()
        fun setter(value : T) = ddbv.setSelectedItem(value as Any, displayName(value))
        initialValue?.let { setter(it) }
        ddbv.onItemClickedListener = {
                value ->
            val selectedValue = value as? T
            if(selectedValue != null) {
                onUserSelectItem(selectedValue)
            }
            true
        }
        liveData.observe(viewLifecycleOwner) {
                value ->
            setter(value)
        }
        root.setOnClickListener {
            ddbv.showMenu()
        }
    }
}


/**
 * Given a list of [options], use can click on the view/row to select 1. This implementation will
 * show a [AlertDialog] and allow user to pick on option from the popup.
 *
 * @param activity for the [AlertDialog]
 * @param viewLifecycleOwner, used by [liveData]
 * @param title
 * @param options, the list of options to choose from
 * @param selectedItem a function to get the current selected item [T]
 * @param liveData a [LiveData] if the value of the select was change by the app (instead of the user)
 * @param displayName, convert [T] to [CharSequence] (for display purpose)
 * @param onUserSelectItem, when user selected a [T] from [AlertDialog]
 *
 * @return A [Disposable] that wraps the [AlertDialog] in case it needs to be [AlertDialog.dismissSafely] need to be called
 */
fun <T> RnItemNeuronSettingsActionBinding.setupSingleChoiceItemsDialog(
    activity: Activity,
    viewLifecycleOwner : LifecycleOwner,
    title : CharSequence,
    options : () -> List<T>,
    selectedItem : () -> T?,
    liveData: LiveData<T>,
    displayName : (T) -> CharSequence,
    onUserSelectItem: (T) -> Unit
) : Disposable {
    var alertDialog : AlertDialog? = null
    val result = Disposable {
        alertDialog?.dismissSafely()
    }
    fun T.updateSubtitle() {
        tvSubtitle.text = displayName(this)
    }
    with(this) {
        tvTitle.text = title
        liveData.value?.updateSubtitle()
        switchLayout.gone()
        ivActionStartIcon.gone()
        ivActionEndIcon.visible()
        root.setOnClickListener {
            val defaultOption =
                selectedItem() ?: return@setOnClickListener
            result.dispose()
            alertDialog = activity.showSingleChoiceItemsDialog(
                title = title,
                message = null,
                onSelected = { false },
                displayName = displayName,
                options = options.invoke(),
                defaultOption = defaultOption,
                isCancelable = true,
                positiveButton = activity.getString(android.R.string.ok) to {
                    onUserSelectItem(it)
                },
                negativeButton = activity.getString(android.R.string.cancel) to { kotlin.Unit }
            )
        }
    }


    liveData.observe(viewLifecycleOwner) {
        it.updateSubtitle()
    }
    return result
}



/**
 * Change [RnItemNeuronSettingsActionBinding] to a row that has a switch
 * e.g.
 * "(icon) Xbox cloud gaming (switch)"
 *
 * @param viewLifecycleOwner, used to observe [liveData]
 * @param icon, an icon for display
 * @param title
 * @param subtitle (optional)
 * @param layoutDirection, used to set [MaterialSwitch] layout direction
 * @param isChecked is the initial value of the switch
 * @param liveData, a [LiveData] used by [viewLifecycleOwner] to detect when switch value need to change (rarely used)
 * @param onCheckedChangeListener use this to call viewmodel's function
 */
fun RnItemNeuronSettingsActionBinding.setupSwitch(
    viewLifecycleOwner : LifecycleOwner,
    icon : Drawable? = null,
    title : CharSequence,
    subtitle: CharSequence? = null,
    layoutDirection : Int? = null,
    isChecked: Boolean? = null,
    liveData: LiveData<Boolean>? = null,
    onCheckedChangeListener : OnCheckedChangeListener
) {
    root.visible()
    if(icon != null) {
        ivActionStartIcon.visible()
        ivActionStartIcon.setImageDrawable(icon)
    }
    tvTitle.text = title
    tvSubtitle.gone()
    if(subtitle != null) {
        tvSubtitle.visible()
        tvSubtitle.text = subtitle
    }
    layoutDirection?.let {
        switchAction.layoutDirection = it
    }
    liveData?.observe(viewLifecycleOwner) {
        if(it != switchAction.isChecked) {
            Timber.v("${title} changed by liveData to $it")
            switchAction.isChecked = it
        }
    }
    fun getTalkbackText(isChecked: Boolean) = getStringExt(if(isChecked) R.string.talkback_switch_on else R.string.talkback_switch_off, title.toString())
    switchLayout.visible()
    switchAction.overrideContentDescriptionForAccessibility {
            checked ->
        getTalkbackText(checked)
    }
    if(isChecked != null) {
        switchAction.isChecked = isChecked
    }
    switchAction.setOnCheckedChangeListener {
            cb, checked ->
        onCheckedChangeListener.onCheckedChanged(cb, checked)
    }
    root.setOnClickListener {
        switchAction.toggle()
    }
}
