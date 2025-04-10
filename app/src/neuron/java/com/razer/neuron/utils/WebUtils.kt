package com.razer.neuron.utils

import android.app.Activity
import androidx.core.os.bundleOf
import com.limelight.R
import com.razer.neuron.web.RnWebViewActivity


const val URL_STRING = "url_string"
const val TITLE = "title"

fun Activity.openInAppBrowser(type: Web) {
    val bundle = bundleOf(URL_STRING to type.url)
    if (!type.title.isNullOrEmpty()) {
        bundle.putString(TITLE, type.title)
    }
    launch(RnWebViewActivity::class.java, bundle)
}

sealed class Web(val url: String, val title: String? = null) {
    data object TermsOfService : Web(
        getStringExt(R.string.cux_terms_of_service_2),
        getStringExt(R.string.tos)
    )

    data object PrivacyPolicy : Web(
        getStringExt(R.string.cux_url_privacy_policy),
        getStringExt(R.string.pp)
    )

    data object OpenSourceNotice : Web(
        getStringExt(R.string.url_open_source_notice),
        getStringExt(R.string.open_source_notice)
    )

    class Custom(url: String, title: String? = null) : Web(url = url, title = title)
}