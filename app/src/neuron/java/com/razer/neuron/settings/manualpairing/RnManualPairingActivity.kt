package com.razer.neuron.settings.manualpairing

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limelight.BuildConfig
import com.limelight.R
import com.limelight.RemotePlayConfig
import com.limelight.databinding.RnActivityManualPairingBinding
import com.limelight.databinding.RnLayoutLoadingBinding
import com.razer.neuron.common.BaseActivity
import com.razer.neuron.common.ComputerServiceHelper
import timber.log.Timber
import com.razer.neuron.common.materialAlertDialogTheme

import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.hideKeyboard
import com.razer.neuron.extensions.setHintOnlyWhenFocused
import com.razer.neuron.extensions.visible
import com.razer.neuron.model.DynamicThemeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RnManualPairingActivity : BaseActivity(), DynamicThemeActivity {
    override fun getThemeId() = appThemeType.settingsThemeId
    companion object {
        const val TAG: String = "RnManualPairingActivity"

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, RnManualPairingActivity::class.java)
        }
    }

    private val viewModel: RnManualPairingViewModel by viewModels()

    private lateinit var binding: RnActivityManualPairingBinding
    private var pinCodeDialog : AlertDialog? = null
    private var isDebugging = BuildConfig.DEBUG// || isTesterBadgeAppInstalled()

    private val computerServiceHelper by lazy {
        object : ComputerServiceHelper() {
            override val computerServiceActivity = this@RnManualPairingActivity
        }
    }
    private val managerBinder get() = computerServiceHelper.managerBinder


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RnActivityManualPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        computerServiceHelper.onCreate()
        observe()
        setupUI()
    }


    private fun observe() {
        lifecycleScope.launch {
            viewModel.viewSharedFlow.collect {
                Timber.v("viewStateFlow: $it")
                when (it) {
                    is ManualPairingState.OnPaired -> it.handle()
                    is ManualPairingState.ShowLoading -> it.handle()
                    is ManualPairingState.HideLoading -> it.handle()
                    is ManualPairingState.Error -> { it.handle() }
                    is ManualPairingState.ShowPin -> it.handle()
                    is ManualPairingState.HidePin -> pinCodeDialog.dismissSafely()
                    is ManualPairingState.DismissPinDialog -> it.handle()
                }
            }
        }
    }

    private fun hasIpAddressAndPort(ipAddress : Editable? = binding.etIpAddress.text, port : Editable? = binding.etPort.text) =
        hasIpAddress(ipAddress) && hasPort(port)

    private fun hasIpAddress(ipAddress : Editable? = binding.etIpAddress.text) = ipAddress?.toString()?.isNotEmpty() == true

    private fun hasPort(port : Editable? = binding.etPort.text) = port?.toString()?.toIntOrNull() != null

    private fun setupUI() {
        binding.etIpAddress.requestFocus()
        binding.etIpAddress.setHintOnlyWhenFocused("192.168.x.x")
        binding.etIpAddress.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                binding.tilIpAddress.error = null
                enableAddButton(hasIpAddressAndPort(ipAddress = s))
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        })
        binding.etIpAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnAdd.performClick()
                true
            } else {
                false
            }
        }
        binding.etPort.setHintOnlyWhenFocused(RemotePlayConfig.default.defaultHttpPort.toString())
        binding.etPort.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                binding.tilPort.error = null
                enableAddButton(hasIpAddressAndPort(port = s))
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        })
        binding.etPort.filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            var output = ""
            for (i in start until end) {
                if (source[i].isDigit()) {
                    output += source[i]
                }
            }
            output
        })
        binding.etPort.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnAdd.performClick()
                true
            } else {
                false
            }
        }
        enableAddButton(false)
        binding.btnAdd.isEnabled = false
        binding.btnAdd.setOnClickListener {
            if (!hasIpAddress()) {
                binding.tilIpAddress.error = getString(R.string.rn_warning_missing_address)
                return@setOnClickListener
            }
            if (!hasPort()) {
                binding.tilPort.error = getString(R.string.rn_warning_missing_port)
                return@setOnClickListener
            }
            val input = getInputIpAddressWithPortFormatted()
            if(input != null) {
                onIpAddressWithPortEntered(input)
            } else {
                com.razer.neuron.common.debugToast("No IP address")
            }
        }
        onBackPressedDispatcher.addCallback {
            finish()
        }

        val toolbar = binding.topToolBar
        toolbar.title = getString(R.string.title_add_pc)
        toolbar.navigationContentDescription = getString(R.string.title_add_pc)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener {
            finish()
        }

    }

    private fun getInputIpAddressWithPortFormatted() : String? {
        var ipAddressInput = binding.etIpAddress.text?.toString() ?: return null
        var portInput = binding.etPort.text?.toString()?.toIntOrNull()?.coerceIn(0, 65535)
        val parsedIpAddress = ipAddressInput.split(":")
        if(portInput == null) {
            // no specific port input
            if(parsedIpAddress.size == 2) {
                // has ip and port (eg. 1.1.1.1:111)
                ipAddressInput = parsedIpAddress[0] // 1.1.1.1
                portInput = parsedIpAddress[1].toIntOrNull() // 111
            }
        } else {
            // has specific port input (e.g. 222)
            if(parsedIpAddress.size == 2) {
                // has ip and port (eg. 1.1.1.1:111)
                // we use 222
                ipAddressInput = parsedIpAddress[0]
            }
        }
        var ipAddressWithPort = ipAddressInput
        if(portInput != null) {
            ipAddressWithPort = "$ipAddressInput:$portInput"
        }
        return ipAddressWithPort
    }

    private fun enableAddButton(enabled: Boolean) {
        binding.btnAdd.isEnabled = enabled
    }

    private fun onIpAddressWithPortEntered(address: String) {
        hideKeyboard(binding.root)
        binding.tilIpAddress.error = null
        viewModel.onIPAddressEntered(managerBinder?.uniqueId, address) {
            managerBinder?.addComputerBlocking(it) == true
        }
    }

    private fun ManualPairingState.OnPaired.handle() {
        finish()
    }

    private fun ManualPairingState.ShowLoading.handle() {
        binding.loadingLayoutContainer.visible()
        RnLayoutLoadingBinding.bind(binding.root).tvLoadingTitle.text = getString(R.string.rn_connecting)
        with(RnLayoutLoadingBinding.bind(binding.root).tvLoadingSubtitle) {
            text = getString(R.string.rn_manual_pairing_loading_content)
            visible()
        }
    }

    private fun ManualPairingState.HideLoading.handle() {
        hideLoading()
    }

    private fun hideLoading() {
        binding.loadingLayoutContainer.gone()
    }

    private fun ManualPairingState.Error.handle() {
        val message = if (isDebugging) {
            exception.message
        } else {
            getString(R.string.rn_warning_could_not_connect_to_host)
        }
        binding.tilIpAddress.error = message
    }

    private fun ManualPairingState.ShowPin.handle() {
        pinCodeDialog.dismissSafely()
        pinCodeDialog = MaterialAlertDialogBuilder(this@RnManualPairingActivity, materialAlertDialogTheme())
            .setCancelable(true)
            .setTitle(getString(R.string.rn_pin_code_title, computerName))
            .setView(LayoutInflater.from(this@RnManualPairingActivity).inflate(R.layout.rn_layout_pin_code, null).apply {
                findViewById<TextView>(R.id.tv_pin_code).text = pinCode
            })
            .setPositiveButton(android.R.string.ok) { _, p ->

            }
            .setOnDismissListener {
                hideLoading()
            }
            .show()
    }

    private fun ManualPairingState.DismissPinDialog.handle() {
        pinCodeDialog.dismissSafely()
    }

    override fun onStop() {
        pinCodeDialog?.dismissSafely()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        computerServiceHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        computerServiceHelper.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        computerServiceHelper.onDestroy()
    }

}
