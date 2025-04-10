package com.razer.neuron.settings.about

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limelight.BuildConfig
import com.limelight.R
import com.limelight.databinding.RnFragmentAboutBinding
import com.razer.neuron.RnConstants
import com.razer.neuron.common.materialAlertDialogTheme
import com.razer.neuron.common.toast
import com.razer.neuron.extensions.dismissSafely
import com.razer.neuron.extensions.gone
import com.razer.neuron.extensions.toHtmlSpanned
import com.razer.neuron.extensions.visible
import com.razer.neuron.settings.RnSettingsActivity
import com.razer.neuron.utils.Web
import com.razer.neuron.utils.getStringExt
import com.razer.neuron.utils.now
import com.razer.neuron.utils.openInAppBrowser
import com.razer.neuron.utils.toDebugTimeString
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar


@AndroidEntryPoint
class RnAboutFragment : Fragment() {

    private lateinit var binding: RnFragmentAboutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RnFragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var versionText = getString(R.string.rn_version_x, BuildConfig.VERSION_NAME)
        versionText += (if (BuildConfig.DEBUG) "(${BuildConfig.BUILD_TIMESTAMP.toDebugTimeString()})" else "")
        binding.tvAppVersion.text = versionText
        binding.btnTos.setOnClickListener {
            activity?.openInAppBrowser(Web.TermsOfService)
        }
        binding.btnPp.setOnClickListener {
            activity?.openInAppBrowser(Web.PrivacyPolicy)
        }
        binding.btnOpenSourceNotice.setOnClickListener {
            activity?.openInAppBrowser(Web.OpenSourceNotice)
        }
        val year = Calendar.getInstance().get(Calendar.YEAR).toString()
        binding.tvCopyright.text = getString(R.string.rn_copyright, year)

        if(RnConstants.IS_ALLOW_DEV_MODE) {
            var count = 0
            var lastClickedAt = 0L
            val totalClickCount = 10
            binding.tvAppIcon.setOnClickListener {
                if((now() - lastClickedAt) < 1000) {
                    count++
                } else {
                    count = 0
                }
                lastClickedAt = now()
                if(count >= totalClickCount - 5) {
                    toast("${totalClickCount - count} clicks away")
                }
                if(count >= totalClickCount) {
                    (activity as? RnSettingsActivity)?.toggleDevMode()
                    count = 0
                }
            }
        }
    }

    private var alertDialog: AlertDialog? = null

    @Deprecated("Just load the URL directly with Web.OpenSourceNotice")
    private fun showOpenSourceNotice() {
        alertDialog.dismissSafely()
        val customTitleView = layoutInflater.inflate(R.layout.layout_open_source_message, null).apply {
                findViewById<TextView>(R.id.tv_opem_source_notice).apply {
                    text = buildOpenSourceMessage()
                    movementMethod = LinkMovementMethod.getInstance()
                }
                val scrollIndicatorUp = findViewById<ImageView>(R.id.scrollIndicatorUp)
                val scrollIndicatorDown = findViewById<ImageView>(R.id.scrollIndicatorDown)
                findViewById<NestedScrollView>(R.id.scrollView).setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                    if (scrollY == 0) {
                        scrollIndicatorUp.gone()
                    } else {
                        scrollIndicatorUp.visible()
                    }
                    if (scrollY == ((v as NestedScrollView).getChildAt(0).measuredHeight - v.measuredHeight)) {
                        scrollIndicatorDown.gone()
                    } else {
                        scrollIndicatorDown.visible()
                    }
                }
            }
        alertDialog = MaterialAlertDialogBuilder(binding.root.context, materialAlertDialogTheme())
            .setTitle(getStringExt(R.string.open_source_notice))
            .setView(customTitleView)
            .setCancelable(true)
            .setPositiveButton(android.R.string.ok) { _, p ->

            }
            .show()
    }

    private fun buildOpenSourceMessage(): Spanned {
        val message = getString(R.string.open_source_message_0) +
                getString(R.string.open_source_message_1) +
                getString(R.string.open_source_message_2)
        return message.toHtmlSpanned()
    }
}
