package com.razer.neuron.settings

import SettingsItem
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.limelight.R
import com.limelight.databinding.RnActivitySettingsBinding
import com.razer.neuron.common.BaseActivity
import com.razer.neuron.common.isShowInAppReview
import com.razer.neuron.common.requestReview
import com.razer.neuron.common.toast
import com.razer.neuron.common.viewTreeObserver
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.setDescendantsUnfocusableUntilLayout
import com.razer.neuron.model.ControllerInput
import com.razer.neuron.model.DynamicThemeActivity
import com.razer.neuron.pref.RemotePlaySettingsPref
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class RnSettingsActivity : BaseActivity(), DynamicThemeActivity {
    override fun getThemeId() = appThemeType.settingsThemeId

    companion object {
        const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 12346
        const val EXTRA_FILE_DISPLAY_NAME = "EXTRA_FILE_DISPLAY_NAME"
        const val EXTRA_FILE_MIME_TYPE = "EXTRA_FILE_MIME_TYPE"
        fun createIntent(context: Context) = Intent(context, RnSettingsActivity::class.java)
    }

    private lateinit var binding: RnActivitySettingsBinding

    private val navView by lazy { binding.navView }
    private val viewModel: RnSettingsViewModel by viewModels()



    /**
     * [RnSettingsActivity] should show navigation bar to match system settings page
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RnActivitySettingsBinding.inflate(layoutInflater)
        navView.navigationBehavior = NavigationBehavior.NavigateOnSelect // BAA-2220 (as per Kevin instruction, we should use standard m3 UX behavior)
        setContentView(binding.root)
        setupNavigationView()
        setupToolbar()
        setReviewAllowed(true)
    }


    private fun setupNavigationView() {
        navView.initNavigationBehavior(window = window) { itemId -> viewModel.navigationTo(itemId) }
        viewTreeObserver()?.let { navView.adjustOverlappingTopMargin(
            viewTreeObserver = it,
            overlappingTopView = binding.topToolBar,
            topMarginOffsetPx = dimenResToPx(R.dimen.m3_navigation_view_top_margin_offset))
        }
        val menu = navView.menu
        viewModel.settingsGroupLiveData.observe(this) { settingsGroup ->
            fun Menu.addMenuItem(groupId: Int, order: Int, item: SettingsItem): MenuItem {
                return add(groupId, item.itemId, order, item.titleId).apply {
                    isCheckable = true
                    setIcon(item.iconId)
                }
            }

            menu.clear()
            settingsGroup.forEachIndexed { groupIndex, group ->
                val subMenu = menu.addSubMenu(group.groupId, Menu.NONE, groupIndex, group.titleId)
                group.items.forEachIndexed { index, item ->
                    subMenu.addMenuItem(group.groupId, index, item)
                }
            }
        }

        viewModel.settingsState.observe(this) {
            state ->
            when(state) {
                SettingsState.Finish -> finish()
                else -> Unit
            }
        }

        viewModel.currentSettingsItemLiveData.observe(this) {
            if (navView.navigationBehavior == NavigationBehavior.NavigateOnSelect) {
                // isPerformNavigation is false because we are already navigating here
                navView.selectItem(it.itemId)
            }
            val targetId = it.navigationId
            val navController = binding.flContainer.getFragment<NavHostFragment>().navController
            if (navController.currentDestination?.id == targetId) return@observe
            navController.popBackStack(navController.currentDestination?.id ?: 0, inclusive = true)
            navController.navigate(targetId)
        }
    }

    private fun setupToolbar() {
        val toolbar = binding.topToolBar
        toolbar.title = getString(R.string.rn_settings)
        toolbar.navigationContentDescription = getString(R.string.rn_back)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        toolbar.setDescendantsUnfocusableUntilLayout()
    }


    fun toggleDevMode() {
        viewModel.toggleDevMode()
        toast("Dev mode ${if (RemotePlaySettingsPref.isDevModeEnabled) "enabled" else "disabled"}")
    }


    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val controllerInput = ControllerInput.entries.firstOrNull { it.keyCode == event.keyCode }
        return if(controllerInput != null) {
            viewModel.onControllerInput(controllerInput, event.action == KeyEvent.ACTION_UP)
        } else {
            super.dispatchKeyEvent(event)
        }
    }
}



