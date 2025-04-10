package com.razer.neuron.oobe

import android.animation.Animator
import android.animation.AnimatorSet
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limelight.R
import com.limelight.databinding.RnActivityOobeBinding
import com.limelight.databinding.RnLayoutLoadingBinding
import com.razer.neuron.RnConstants
import com.razer.neuron.common.BaseControllerActivity
import com.razer.neuron.common.debugToast
import com.razer.neuron.common.materialAlertDialogTheme
import com.razer.neuron.extensions.applyTransition
import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.fadeOnStart
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.hasGenericController
import com.razer.neuron.extensions.openInBrowser
import com.razer.neuron.extensions.toUriOrNull
import com.razer.neuron.extensions.visible
import com.razer.neuron.extensions.visibleIf
import com.razer.neuron.main.RnMainActivity
import com.razer.neuron.model.ButtonHint
import com.razer.neuron.model.ControllerInput
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.model.DynamicThemeActivity
import com.razer.neuron.nexus.NexusPackageStatus
import com.razer.neuron.nexus.SHARED_CONTENT_PROVIDER_DATA_ACCESS_PERMISSION
import com.razer.neuron.utils.PermissionChecker
import com.razer.neuron.utils.Web
import com.razer.neuron.utils.openInAppBrowser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RnOobeActivity : BaseControllerActivity(), RnLayoutOobePermissionsBindingHelper, ButtonHintsBarHelper,
    RnLayoutOobeTosBindingHelper, RnLayoutOobeDisplayModesBindingHelper, DynamicThemeActivity {
    override fun getThemeId() = appThemeType.settingsThemeId

    companion object {
        fun startOobe(activity: Activity, intent: Intent? = null) {
            activity.startActivity(Intent(activity, RnOobeActivity::class.java).apply {
                putExtras(intent ?: activity.intent)
            })
        }
    }

    private var transitionAnimator: Animator? = null
    private var loadingAnimator: Animator? = null


    private var _binding: RnActivityOobeBinding? = null

    private val binding get() = _binding!!

    private val layouts by lazy {
        listOf(
            binding.layoutTos.root,
            binding.layoutPermissions.root,
            binding.layoutDownloadNexus.root
        )
    }
    override val context = this

    private val firstVisibleLayout get() = layouts.firstOrNull { it.isVisible }

    private val viewModel: RnOobeViewModel by viewModels()

    override val buttonHintsBar by lazy { binding.buttonHintsBar }

    override val layoutPermissionBinding by lazy { binding.layoutPermissions }

    override val layoutTosBinding by lazy { binding.layoutTos }

    override val layoutDisplayModesBinding by lazy { binding.layoutDisplayModes }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTransition()
        super.onCreate(savedInstanceState)
        _binding = RnActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        layouts.forEach { it.gone() }
        observe()
        viewModel.onCreate(intent)
        buttonHintsBar.visible() // always visible
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val controllerInput = ControllerInput.entries.firstOrNull { it.keyCode == event?.keyCode }
        return if (controllerInput != null) {
            viewModel.onControllerInput(controllerInput, event?.action == KeyEvent.ACTION_UP)
            true
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        val newIntent = intent
        super.onNewIntent(newIntent)
        viewModel.onNewIntent(newIntent ?: this.intent)
    }

    override fun showLayout(view: View) {
        val firstVisibleLayout = firstVisibleLayout
        val animators = mutableListOf<Animator>()
        transitionAnimator?.cancel()
        if (firstVisibleLayout != view) {
            firstVisibleLayout?.let {
                with(it) {
                    animators += fadeOnStart(0f, View.GONE)
                }
            }
        }

        with(view) {
            if (isGone) {
                alpha = 0f
                visible()
            }
            animators += fadeOnStart(1f)
        }
        transitionAnimator = AnimatorSet().apply {
            playTogether(*animators.toTypedArray())
            start()
        }
    }


    private fun showLoading() {
        loadingAnimator?.cancel()
        loadingAnimator = with(binding.layoutLoading) {
            if (isGone) {
                alpha = 0f
                visible()
            }
            fadeOnStart(1f).apply { start() }
        }
        RnLayoutLoadingBinding.bind(binding.root).tvLoadingTitle.text =
            getString(R.string.rn_please_wait)
    }

    private fun hideLoading() {
        loadingAnimator?.cancel()
        loadingAnimator = with(binding.layoutLoading) {
            fadeOnStart(0f, View.GONE)
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.navigation.collect {
                when (it) {
                    is RnOobeModel.Navigation.Finish -> finish()
                    is RnOobeModel.Navigation.Main -> {
                        RnMainActivity.startMain(this@RnOobeActivity, it.launchIntent ?: intent)
                        finish()
                    }

                    is RnOobeModel.Navigation.RequestPermission -> {
                        it.oobePermission.requestPermission()
                    }

                    is RnOobeModel.Navigation.Tos -> {
                        this@RnOobeActivity.openInAppBrowser(Web.TermsOfService)
                    }

                    is RnOobeModel.Navigation.Pp -> {
                        this@RnOobeActivity.openInAppBrowser(Web.PrivacyPolicy)
                    }

                    is RnOobeModel.Navigation.DownloadNexus -> {
                        when {
                            it.nexusPackageStatus == NexusPackageStatus.InvalidVersion -> {
                                showGetNexusDialog(
                                    getString(R.string.rn_update_razer_nexus),
                                    getString(R.string.rn_razer_nexus_needs_update_message),
                                    getString(R.string.rn_update)
                                )
                            }

                            it.nexusPackageStatus == NexusPackageStatus.NotInstalled -> {
                                showGetNexusDialog(
                                    getString(R.string.rn_download_razer_nexus),
                                    getString(R.string.rn_razer_nexus_required_message),
                                    getString(R.string.rn_download)
                                )
                            }

                            it.isUserTriggered -> {
                                redirectToRazerNexusPlayStore()
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.loading.collect {
                if (it) showLoading() else hideLoading()
            }
        }

        lifecycleScope.launch {
            viewModel.state.collect {
                val buttonHints = it.buttonHints.toMutableSet()
                when (it) {
                    is RnOobeModel.State.Tos -> it.handle()
                    is RnOobeModel.State.PermissionSummary -> it.handle()
                    is RnOobeModel.State.NexusDownload -> it.handle()
                    is RnOobeModel.State.DisplayModes -> it.handle()
                    else -> Unit
                }
                updateButtonHints(it.buttonHints)
            }
        }
    }

    override fun onButtonHintsBarButtonHintClicked(buttonHint: ButtonHint) {
        viewModel.onButtonHintClicked(buttonHint)
    }

    override fun onTosClicked() {
        viewModel.onTosClicked()
    }

    override fun onPpClicked() {
        viewModel.onPpClicked()
    }

    override fun onDisplayModeSelected(displayMode: DisplayModeOption) {
        viewModel.onDisplayModeSelected(displayMode)
    }

    private var alertDialog: AlertDialog? = null


    @Deprecated("Remove this")
    private fun showTestDialog(title: String) {
        alertDialog.dismissSafely()
        alertDialog = MaterialAlertDialogBuilder(this, materialAlertDialogTheme())
            .setCancelable(false)
            .setTitle(title)
            .setMessage(getString(R.string.placeholder_long))
            .setPositiveButton(R.string.placeholder_short) { _, p ->

            }
            .setNegativeButton(R.string.rn_cancel) { _, p ->

            }
            .show()
    }

    private fun showGetNexusDialog(title: String, message: String, buttonText : String = getString(R.string.rn_download)) {
        alertDialog.dismissSafely()
        alertDialog = MaterialAlertDialogBuilder(this, materialAlertDialogTheme())
            .setCancelable(false)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText) { _, p ->
                redirectToRazerNexusPlayStore()
            }
            .setNegativeButton(R.string.rn_cancel) { _, p ->

            }
            .show()
    }

    private fun RnOobeModel.State.NexusDownload.handle() {
        showLayout(binding.layoutDownloadNexus.root)
        binding.layoutDownloadNexus.googlePlayStore
            .setOnClickListener {
                viewModel.onDownloadNexusClicked()
            }
    }

    override fun onResume() {
        super.onResume()
        updateMockSystemPermissionDialogVisual()
        viewModel.onResume()
    }


    private suspend fun OobePermission.requestPermission() {
        when {
            this.isFakePermission() -> {
                debugToast("Asking for fake permission")
                delay(2000)
                fakePermissions[this] = true
                viewModel.onPermissionRequestResult(PermissionChecker.Result(emptyMap()))
            }

            this == OobePermission.NexusContentProvider -> {
                val result =
                    maybeRequestPermissions(SHARED_CONTENT_PROVIDER_DATA_ACCESS_PERMISSION)
                viewModel.onPermissionRequestResult(result)
            }

            else -> debugToast("not implemented")
        }
    }

    override fun onDestroy() {
        transitionAnimator?.cancel()
        loadingAnimator?.cancel()
        super.onDestroy()
    }

    private fun redirectToRazerNexusPlayStore() {
        RnConstants.NEXUS_DOWNLOAD_URL.toUriOrNull()
            ?.openInBrowser(this)
    }


}

