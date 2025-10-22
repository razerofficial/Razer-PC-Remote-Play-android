package com.razer.neuron.settings

import SettingsGroup
import SettingsItem
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.limelight.R
import com.limelight.databinding.RnActivitySettingsBinding
import com.razer.neuron.common.BaseActivity
import com.razer.neuron.common.FragmentNavigator
import com.razer.neuron.common.getMidLinePercent
import com.razer.neuron.common.logWarningOnError
import com.razer.neuron.common.setNavigationBarIconColor
import com.razer.neuron.common.setStatusBarIconColor
import com.razer.neuron.common.showNavigationBarsOnly
import com.razer.neuron.common.showStatusBar
import com.razer.neuron.common.toast
import com.razer.neuron.common.viewTreeObserver
import com.razer.neuron.extensions.dimenResToPx
import com.razer.neuron.extensions.setDescendantsUnfocusableUntilLayout
import com.razer.neuron.extensions.setOnlyPadding
import com.razer.neuron.extensions.visibleIf
import com.razer.neuron.model.ControllerInput
import com.razer.neuron.model.DynamicThemeActivity
import com.razer.neuron.model.getNavigationView3BackgroundColor
import com.razer.neuron.pref.RemotePlaySettingsPref
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber


@AndroidEntryPoint
class RnSettingsActivity : BaseActivity(), DynamicThemeActivity, FragmentNavigator {
    override val isRegisterSystemBarInsetsChange = true
    override fun getThemeId() = appThemeType.settingsThemeId

    companion object {
        const val EXTRA_RN_SETTINGS_ITEM = "EXTRA_RN_SETTINGS_ITEM"
        fun createIntent(context: Context) = Intent(context, RnSettingsActivity::class.java)
    }

    private lateinit var binding: RnActivitySettingsBinding

    private val navView by lazy { binding.navView }
    private val viewModel: RnSettingsViewModel by viewModels()
    private lateinit var nvHelper: NavigationViewHelper
    private val navHostFragment get() = binding.fragmentContainer.getFragment<NavHostFragment>()

    private val childFragmentManager get() = navHostFragment.childFragmentManager

    private val currentTopNavFragment get() = childFragmentManager.primaryNavigationFragment

    private val currentTopNavSettingsItem get() = currentTopNavFragment?.arguments?.getParcelable<SettingsItem>(EXTRA_RN_SETTINGS_ITEM)
    /**
     * [RnSettingsActivity] should show navigation bar to match system settings page
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nvHelper = NavigationViewHelper(layoutInflater)
        binding = RnActivitySettingsBinding.inflate(layoutInflater)
        navView.navigationBehavior =
            NavigationBehavior.NavigateOnSelect // BAA-2220 (as per Kevin instruction, we should use standard m3 UX behavior)
        setContentView(binding.root)
        setupNavigationView()
        setupNavViewToolbar()
        setupFragmentToolbar()
        setReviewAllowed(true)
    }

    override fun onStart() {
        super.onStart()
        binding.midLine.setGuidelinePercent(getMidLinePercent())
    }

    override fun onSystemBarInsetsChanged(insets : Insets) {
        runCatching {
            Timber.v("onApplySystemBarInsets: $insets")
            with(insets) {
                binding.navViewLayout.setOnlyPadding(top = top, start = left, bottom = bottom)
                binding.fragmentLayout.setOnlyPadding(top = top, end = right, bottom = bottom)
            }
            showStatusBar(transparent = true)
            showNavigationBarsOnly()
            setNavigationBarIconColor(window, isUseWhiteSystemBarIcons)
            setStatusBarIconColor(window, isUseWhiteSystemBarIcons)
        }.logWarningOnError()
    }

    private fun setupNavigationView() {
        navView.initNavigationBehavior(window = window) { itemId ->
            viewModel.navigationTo(itemId)
        }
        viewTreeObserver()?.let {
            navView.adjustOverlappingTopMargin(
                viewTreeObserver = it,
                overlappingTopView = binding.navViewToolbar,
                topMarginOffsetPx = dimenResToPx(R.dimen.m3_navigation_view_top_margin_offset)
            )
        }

        binding.navViewLayout.setBackgroundColor(getNavigationView3BackgroundColor())
        viewModel.settingsGroupLiveData.observe(this) { settingsGroup ->
            lifecycleScope.launch {
                nvHelper.refreshMenuItems(settingsGroup)
            }
        }

        viewModel.settingsState.observe(this) { state ->
            when (state) {
                SettingsState.Finish -> finish()
                else -> Unit
            }
        }

        viewModel.currentSettingsItemLiveData.observe(this) {
            it.handle()
        }

        navHostFragment.navController.addOnDestinationChangedListener { _, _, _ ->
            updateFragmentToolbar()
        }
    }



    private fun SelectedSettingsItem.handle() {
        val settingsItem = item
        if (navView.navigationBehavior == NavigationBehavior.NavigateOnSelect) {
            Timber.v("SelectedSettingsItem.handle: selectItem = $settingsItem")
            // isPerformNavigation is false because we are already navigating here
            navView.selectItem(settingsItem.itemId)
        }
        nvHelper.updateSubMenuCheckedState(binding.navView.menu, checkedItemId = settingsItem.itemId)
        Timber.v("SelectedSettingsItem.handle: setRootFragment $settingsItem")
        nvHelper.setRootFragment(
            navHostFragment.navController,
            settingsItem.navigationId,
            settingsItem
        )

    }

    private fun updateFragmentToolbar() {
        window.decorView.post {
            val fragmentToolbar = binding.fragmentToolbar
            currentTopNavFragment?.let { fragment ->
                val title = (fragment as? SettingsStackFragment)?.fragmentTitle
                fragmentToolbar.setTitle(title ?: "")
                fragmentToolbar.visibleIf { title != null }
            }
        }
    }

    private fun setupNavViewToolbar() {
        val toolbar = binding.navViewToolbar
        toolbar.title = getString(R.string.rn_settings)
        toolbar.navigationContentDescription = getString(R.string.rn_back)
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        toolbar.setDescendantsUnfocusableUntilLayout()
    }

    /**
     *  back button action
     */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupFragmentToolbar() {
        val fragmentToolbar = binding.fragmentToolbar
        fragmentToolbar.navigationContentDescription = getString(R.string.rn_back)
        fragmentToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        fragmentToolbar.setDescendantsUnfocusableUntilLayout()
    }


    fun toggleDevMode() {
        viewModel.toggleDevMode()
        toast("Dev mode ${if (RemotePlaySettingsPref.isDevModeEnabled) "enabled" else "disabled"}")
    }


    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val controllerInput = ControllerInput.entries.firstOrNull { it.keyCode == event.keyCode }
        return if (controllerInput != null) {
            viewModel.onControllerInput(controllerInput, event.action == KeyEvent.ACTION_UP)
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        (supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment)?.let { host ->
            host.childFragmentManager.fragments.firstOrNull { it.isVisible }?.run {
                onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    override fun addFragmentToBackStack(navigationId: Int, bundle: Bundle?) {
        nvHelper.addFragmentToBackStack(
            navHostFragment.navController,
            navigationId,
            bundle
        )
    }


    private inner class NavigationViewHelper(private val layoutInflater: LayoutInflater) {

        /**
         * Calls [NavController.navigate], but skip if [targetNavigationId] is [NavController.currentDestination]
         * AND also use [currentTopNavSettingsItem] to see if is the same as [settingsItem], because
         * for cases like [M3SettingsItem.CONTROLLER_DEVICE] the [M3SettingsItem.SUBITEMS_ITEM.subItems] might
         * be different.
         */
        fun setRootFragment(
            navController: NavController,
            targetNavigationId: Int,
            settingsItem: SettingsItem
        ) {
            val currentDestinationId = navController.currentDestination?.id
            val currentTopNavSettingsItem = currentTopNavSettingsItem
            if (currentDestinationId == targetNavigationId
                && currentTopNavSettingsItem?.isSameContent(settingsItem) == true
            ) {
                return
            }

            fun backStackSize() = navController.backQueue.size
            // Pop all fragments when navigation menu item is selected.
            while (backStackSize() > 1) {
                Timber.v("setRootFragment: backStackSize()=${backStackSize()}")
                navController.popBackStack()
            }

            val bundle = bundleOf(EXTRA_RN_SETTINGS_ITEM to settingsItem)
            navController.navigate(targetNavigationId, bundle)
        }

        fun addFragmentToBackStack(
            navController: NavController,
            navigationId: Int,
            bundle: Bundle?
        ) {
            navController.navigate(navigationId, bundle)
        }

        fun updateSubMenuCheckedState(menu: Menu, checkedItemId: Int) {
            menu.forEach { menuGroup ->
                menuGroup.subMenu?.forEach { subMenu ->
                    subMenu.isChecked = subMenu.itemId == checkedItemId
                }
            }
        }

        private fun Menu.getCheckedMenuItem() = flatten().firstOrNull { it.isChecked }

        private fun List<SettingsGroup>.findSettingsItem(itemId: Int): SettingsItem? {
            return map { it.items }.flatten().firstOrNull { it.itemId == itemId }
        }

        suspend fun refreshMenuItems(settingsGroup: List<SettingsGroup>) {

            fun Menu.addMenuItem(groupId: Int, order: Int, item: SettingsItem): MenuItem {
                val titleText = item.titleText ?: getString(item.titleId)
                return add(groupId, item.itemId, order, titleText).apply {
                    isCheckable = true
                    setIcon(item.iconId)
                }
            }

            navView.updateMenu(
                transaction = { menu ->
                    menu.clear()
                    settingsGroup.forEach { group ->
                        val groupTitle = group.getTitle(resources = resources)
                        val subMenu = menu.addSubMenu(group.groupId, 0, 0, groupTitle)
                        group.items.forEachIndexed { index, item ->
                            subMenu.addMenuItem(group.groupId, index, item)
                        }
                    }
                },
                onCompleted = { menu ->
                    /**
                     * Update the right side of the the page by calling [SelectedSettingsItem.handle]
                     */
                    val selectedSettingsItem =
                        menu.getCheckedMenuItem()?.let { settingsGroup.findSettingsItem(it.itemId) }
                    Timber.v("refreshMenuItems:updateMenu:onCompleted: selectedSettingsItem=$selectedSettingsItem currentTopNavSettingsItem=$currentTopNavSettingsItem isSame=${selectedSettingsItem?.isSameContent(currentTopNavSettingsItem)}"
                   )
                    if (selectedSettingsItem != null) {
                        if (!selectedSettingsItem.isSameContent(currentTopNavSettingsItem)) {
                            SelectedSettingsItem(selectedSettingsItem, true).handle()
                        } else {
                            // same setting item selected, ignore update
                        }
                    } else {
                        SelectedSettingsItem(SettingsItem.defaultItem, true).handle()
                    }
                })
        }
    }
}


fun SettingsGroup.getTitle(resources: Resources) = resources.getString(titleId)