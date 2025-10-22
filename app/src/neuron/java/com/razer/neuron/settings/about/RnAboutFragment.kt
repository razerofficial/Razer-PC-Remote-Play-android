package com.razer.neuron.settings.about

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.limelight.BuildConfig
import com.limelight.R
import com.limelight.databinding.RnFragmentAboutBinding
import com.razer.neuron.RnConstants
import com.razer.neuron.common.toast
import com.razer.neuron.extensions.dpToPx
import com.razer.neuron.settings.RnSettingsActivity
import com.razer.neuron.settings.SettingsStackFragment
import com.razer.neuron.utils.Web
import com.razer.neuron.utils.doOnLayoutOnce
import com.razer.neuron.utils.now
import com.razer.neuron.utils.openInAppBrowser
import com.razer.neuron.utils.setGravityForCenterOrVerticalScroll
import com.razer.neuron.utils.toDebugTimeString
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import kotlin.math.roundToInt


@AndroidEntryPoint
class RnAboutFragment : Fragment(), SettingsStackFragment {
    override val fragmentTitle get() = null
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

        binding.root.let {
            it.doOnLayoutOnce {
                binding.content.setGravityForCenterOrVerticalScroll()
            }
        }
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

}
