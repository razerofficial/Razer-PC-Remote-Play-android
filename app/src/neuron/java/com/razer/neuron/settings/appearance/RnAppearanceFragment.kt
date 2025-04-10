package com.razer.neuron.settings.appearance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.limelight.R
import com.limelight.databinding.RnFragmentAppearanceBinding
import com.razer.neuron.common.RnThemeManager
import timber.log.Timber
import com.razer.neuron.common.debugToast
import com.razer.neuron.common.showSingleChoiceItemsDialog

import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.visible
import com.razer.neuron.model.AppThemeType
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.utils.getStringExt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@AndroidEntryPoint
class RnAppearanceFragment : Fragment() {

    private lateinit var binding: RnFragmentAppearanceBinding

    private val viewModel: RnAppearanceViewModel by activityViewModels()

    private val tvLoadingTitle by lazy {
        binding.loadingLayoutContainer.findViewById<TextView>(R.id.tv_loading_title)
    }

    private val tvLoadingSubtitle by lazy {
        binding.loadingLayoutContainer.findViewById<TextView>(R.id.tv_loading_subtitle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RnFragmentAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observe()
    }

    private fun setupUI() {
        binding.btnTheme.ivActionIcon.gone()
        binding.btnTheme.tvTitle.text = getStringExt(R.string.rn_theme_title)
        binding.btnTheme.tvSubtitle.text = getStringExt(RemotePlaySettingsPref.appThemeType.title)
        binding.btnTheme.root.setOnClickListener {
            viewModel.onThemeClicked()
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            viewModel.viewSharedFlow.collect {
                Timber.v("observe: viewStateFlow: ${it.javaClass.simpleName}")
                when (it) {
                    is AppearanceState.ShowLoading -> it.handle()
                    is AppearanceState.HideLoading -> it.handle()
                    is AppearanceState.ApplyTheme -> it.handle()
                    is AppearanceState.ShowSelectTheme -> it.handle()
                    else -> debugToast("${it.javaClass.simpleName} not handled")
                }
            }
        }
    }

    private fun AppearanceState.ShowLoading.handle() {
        binding.loadingLayoutContainer.visible()
        tvLoadingTitle.text = getString(R.string.rn_please_wait)
        with(tvLoadingSubtitle) {
            gone()
        }
    }

    private fun AppearanceState.HideLoading.handle() {
        binding.loadingLayoutContainer.gone()
    }

    private fun AppearanceState.ApplyTheme.handle() {
        val activity = activity ?: return
        binding.loadingLayoutContainer.visible()
        tvLoadingTitle.text = getString(R.string.rn_please_wait)
        tvLoadingSubtitle.gone()
        lifecycleScope.launch {
            delay(250)
            RnThemeManager.maybeRecreateOnThemeChange(activity)
        }
    }

    private var appThemeSelectionDialog : AlertDialog? = null

    /**
     * TODO:
     * To be removed
     */
    private fun AppearanceState.ShowSelectTheme.handle() {
        val activity = activity ?: return
        appThemeSelectionDialog.dismissSafely()
        appThemeSelectionDialog = activity.showSingleChoiceItemsDialog(
            titleId = R.string.rn_theme_dialog_title,
            messageId = null,
            onSelected = { false } ,
            nameResList = AppThemeType.entries.map { it.title },
            options = AppThemeType.entries,
            defaultOption = defaultOption,
            isCancelable = true,
            positiveButton = getString(android.R.string.ok) to {
                viewModel.onSelectTheme(it)
            },
            negativeButton = getString(android.R.string.cancel) to { Unit }
        )
    }
}
