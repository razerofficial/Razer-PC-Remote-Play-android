package com.razer.neuron.web

import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import com.limelight.R
import com.limelight.databinding.RnActivityWebViewBinding
import com.razer.neuron.common.BaseActivity
import com.razer.neuron.common.toast
import com.razer.neuron.extensions.hideNavigationBars
import com.razer.neuron.model.DynamicThemeActivity
import com.razer.neuron.utils.URL_STRING
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

class RnWebViewActivity : BaseActivity(), DynamicThemeActivity {
    override fun getThemeId() = appThemeType.settingsThemeId
    companion object{
        const val OPEN_LICENSE = "open_license"
        const val PDF_SUFFIX = ".pdf"
        const val TITLE = "title"
    }
    private lateinit var binding: RnActivityWebViewBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = RnActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        // Add the callback to the OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        var url = intent?.extras?.getString(URL_STRING)
        url = if (OPEN_LICENSE == url) {
            "file:///android_asset/licenses.html"
        } else {
            URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        }

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        binding.webView.settings.setGeolocationEnabled(true)
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (error?.errorCode == -2) {
                    toast(getString(R.string.rn_no_network_connection))
                }
            }

            override fun onLoadResource(view: WebView?, url: String?) {}

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.webProgress.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                super.shouldOverrideUrlLoading(view, request)
                val urlString = request?.url.toString()
                val pdfSuffix = PDF_SUFFIX
                val withPdfSuffix =
                    urlString.substring(max(0, urlString.length - pdfSuffix.length)) == pdfSuffix
                if (withPdfSuffix) {
//                    val intent = Intent(this@WebViewActivity, PDFViewActivity::class.java).apply {
//                        putExtra(URL_STRING, urlString)
//                        putExtra(TITLE, getString(R.string.master_guide))
//                    }
//                    startActivity(intent)
                    return true
                }
                return false
            }
        }

        if (url != null) {
            binding.webView.loadUrl(url)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // Create a callback for back button press
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Handle the back button press here
            // You can perform any custom logic or navigation
            onBackAction()
        }
    }

    private fun onBackAction() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }

    override fun dispatchKeyEvent(ke: KeyEvent): Boolean {
        // settings key
        if (ke.action == KeyEvent.ACTION_UP && ke.keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            finish()
            return true
        }
        return super.dispatchKeyEvent(ke)
    }

    private fun setupToolbar() {
        val toolbar = binding.topToolBar
        val title = intent?.extras?.getString(TITLE)
        toolbar.title = title
        toolbar.navigationContentDescription = title
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener {
            onBackAction()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the callback when the activity is destroyed
        backPressedCallback.remove()
    }

}
