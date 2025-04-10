package com.razer.neuron.oobe

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.children
import com.limelight.R
import com.limelight.databinding.RnLayoutOobePermissionsBinding
import com.razer.neuron.common.isSystemInDarkMode
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.invisible
import com.razer.neuron.extensions.visible
import com.razer.neuron.model.ButtonHint
import com.razer.neuron.model.toButtonText

interface RnLayoutOobePermissionsBindingHelper {

    val context: Context
    val layoutPermissionBinding: RnLayoutOobePermissionsBinding


    fun showLayout(view: View)

    /**
     * Updating the mock permission dialog with either light or dark
     * because this is the OS's dialog, it does not follow our custom theme dark/light
     * (from using [AppCompatDelegate.setDefaultNightMode])
     */
    fun updateMockSystemPermissionDialogVisual() {
        val binding = layoutPermissionBinding
        binding.mockSystemPermissionDialog.setBackgroundResource(
            if (context.isSystemInDarkMode()) R.drawable.mock_system_permission_dialog_night else R.drawable.mock_system_permission_dialog_light
        )
    }

    fun RnOobeModel.State.PermissionSummary.handle() {
        showLayout(layoutPermissionBinding.root)
        updateMockSystemPermissionDialogVisual()
        val allOobePermissionViews = layoutPermissionBinding
            .permissionViewsContainer
            .children
            .filterIsInstance(OobePermissionView::class.java)
            .toMutableList()

        allOobePermissionViews.forEach { it.gone() }
        for (oobePermission in displayPermissions) {
            val v = allOobePermissionViews.removeFirstOrNull() ?: break
            v.visible()
            val isFakeFocus = oobePermission == focusPermission
            v.setOobePermission(oobePermission)
            if (isFakeFocus) {
                v.checkSelfAndUncheckSiblings()
            } else {
                v.isChecked = false
            }
        }
    }


    private fun OobePermissionView.setOobePermission(
        oobePermission: OobePermission
    ) {
        val (name, desc) = oobePermission.toDisplayNameAndDesc()
        this.title.text = name
        this.subtitle.text = desc
        this.icon.setImageResource(oobePermission.toIconRes())
        if (oobePermission.wasGranted(context)) {
            this.checkedIcon.visible()
        } else {
            this.checkedIcon.gone()
        }
    }


    private fun OobePermission.toDisplayNameAndDesc() = when (this) {
        OobePermission.NexusContentProvider -> context.getString(R.string.rn_nexus_content_provider_permission_name) to context.getString(
            R.string.rn_nexus_content_provider_permission_description
        )

        OobePermission.PermissionA -> name to "Special ${name}. Allow special feature."
        OobePermission.PermissionB -> name to "Special ${name}. Allow special feature."
    }

    private fun OobePermission.toIconRes() = when (this) {
        OobePermission.NexusContentProvider -> R.drawable.ic_pairing_48dp
        OobePermission.PermissionA -> R.drawable.ic_pairing_48dp
        OobePermission.PermissionB -> R.drawable.ic_pairing_48dp
    }
}